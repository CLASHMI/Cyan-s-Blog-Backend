package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 随笔点击量：详情请求只把增量累加到 Redis，定时任务再批量刷回 MySQL。
 * Redis 里存的是「尚未落库的增量」，展示时按 DB 已落库值 + 增量取实时值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostViewCountService {

    /* Hash：field = postId，value = 待刷入数据库的点击量增量 */
    private static final String VIEW_DELTA_KEY = "blog:post:view:delta";

    private final StringRedisTemplate stringRedisTemplate;
    private final BlogPostMapper blogPostMapper;

    /** 记录一次点击：只写 Redis，不打数据库 */
    public void recordView(Long postId) {
        if (postId == null) {
            return;
        }
        stringRedisTemplate.opsForHash().increment(VIEW_DELTA_KEY, String.valueOf(postId), 1L);
    }

    /** 全站尚未落库的点击量增量之和 */
    public long pendingViews() {
        return stringRedisTemplate.<String, String>opsForHash().values(VIEW_DELTA_KEY).stream()
                .mapToLong(PostViewCountService::parseLong)
                .filter(value -> value > 0)
                .sum();
    }

    /**
     * 给列表 / 详情补齐实时点击量：数据库已落库值 + Redis 里还没刷入的增量。
     * 落库后增量会同步扣减，两者相加始终连续，刷新不会出现数字回退。
     */
    public void decorate(Collection<? extends BlogPost> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }
        List<Long> ids = posts.stream()
                .map(BlogPost::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, Long> totals = new HashMap<>();
        blogPostMapper.selectList(new LambdaQueryWrapper<BlogPost>()
                        .select(BlogPost::getId, BlogPost::getViewCount)
                        .in(BlogPost::getId, ids))
                .forEach(post -> totals.put(post.getId(), toLong(post.getViewCount())));

        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        List<String> fields = ids.stream().map(String::valueOf).collect(Collectors.toList());
        List<String> pending = hashOps.multiGet(VIEW_DELTA_KEY, fields);
        for (int i = 0; i < ids.size(); i++) {
            long delta = i < pending.size() ? parseLong(pending.get(i)) : 0L;
            if (delta > 0) {
                totals.merge(ids.get(i), delta, Long::sum);
            }
        }

        for (BlogPost post : posts) {
            if (post.getId() == null) {
                continue;
            }
            Long total = totals.get(post.getId());
            if (total != null) {
                post.setViewCount((int) Math.min(total, Integer.MAX_VALUE));
            }
        }
    }

    /**
     * 把 Redis 增量批量刷入 MySQL。先加库、再按「读到的增量」扣减 Redis：
     * 刷库期间新到的点击只会让剩余值变大，不会被这次刷库带走。
     */
    @Scheduled(fixedDelayString = "${loopia.blog.view-flush-ms:60000}")
    public void flushToDatabase() {
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        Map<String, String> entries = hashOps.entries(VIEW_DELTA_KEY);
        entries.forEach((field, value) -> {
            long delta = parseLong(value);
            if (delta <= 0) {
                return;
            }
            Long postId;
            try {
                postId = Long.valueOf(field);
            } catch (NumberFormatException e) {
                /* 非法 field：清掉，避免每次刷库都重复处理 */
                hashOps.delete(VIEW_DELTA_KEY, field);
                return;
            }
            int updated = blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                    .setSql("view_count = view_count + {0}", delta)
                    .setSql("update_time = update_time")
                    .eq(BlogPost::getId, postId));
            if (updated > 0) {
                hashOps.increment(VIEW_DELTA_KEY, field, -delta);
            } else {
                /* 文章已被删除：这份增量没有归属，直接清掉 */
                hashOps.delete(VIEW_DELTA_KEY, field);
            }
        });
    }

    private static long toLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
