package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.loopia.gameanimationshow.constant.SystemConstant;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.entity.BlogPostTag;
import com.loopia.gameanimationshow.entity.SysUser;
import com.loopia.gameanimationshow.mapper.BlogCommentMapper;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import com.loopia.gameanimationshow.mapper.BlogPostTagMapper;
import com.loopia.gameanimationshow.mapper.BlogTagMapper;
import com.loopia.gameanimationshow.mapper.SysCategoryMapper;
import com.loopia.gameanimationshow.mapper.SysUserMapper;
import com.loopia.gameanimationshow.model.vo.ActivityStatsVO;
import com.loopia.gameanimationshow.model.vo.OverviewStatsVO;
import com.loopia.gameanimationshow.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final BlogPostMapper blogPostMapper;
    private final BlogCommentMapper blogCommentMapper;
    private final BlogPostTagMapper blogPostTagMapper;
    private final BlogTagMapper blogTagMapper;
    private final SysCategoryMapper sysCategoryMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Cacheable(value = "statsOverview", key = "'overview'")
    public OverviewStatsVO getOverviewStats() {
        Long postsCount = blogPostMapper.selectCount(null);
        Long interactionsCount = blogCommentMapper.selectCount(null);
        Long tagsCount = blogTagMapper.selectCount(null);
        Long categoriesCount = sysCategoryMapper.selectCount(null);
        Long visitorsCount = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("role", SystemConstant.Role.VISITOR));

        List<Object> likeSums = blogPostMapper.selectObjs(
                new QueryWrapper<BlogPost>().select("IFNULL(SUM(like_count), 0)"));

        return OverviewStatsVO.builder()
                .totalPosts(postsCount)
                .totalComments(interactionsCount)
                .totalLikes(likeSums.isEmpty() ? 0L : toLong(likeSums.get(0)))
                .totalVisitors(visitorsCount)
                .totalTags(tagsCount)
                .totalCategories(categoriesCount)
                .build();
    }

    /* 热力图与标签云共用：交给 MySQL 分组统计，前端不必再拉全量文章 */
    @Override
    @Cacheable(value = "statsOverview", key = "'activity:v2'")
    public ActivityStatsVO getActivityStats() {
        Map<String, Integer> dayCounts = new LinkedHashMap<>();
        blogPostMapper.selectMaps(new QueryWrapper<BlogPost>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS day_key", "COUNT(*) AS total")
                        .groupBy("day_key"))
                .forEach(row -> dayCounts.put(String.valueOf(row.get("day_key")), toInt(row.get("total"))));

        List<ActivityStatsVO.TagUsageVO> tagCounts = new ArrayList<>();
        List<Map<String, Object>> relations = blogPostTagMapper.selectMaps(new QueryWrapper<BlogPostTag>()
                .select("tag_id", "COUNT(*) AS total")
                .groupBy("tag_id"));
        if (!relations.isEmpty()) {
            Map<Integer, Integer> countByTag = new HashMap<>();
            relations.forEach(row -> countByTag.put(toInt(row.get("tag_id")), toInt(row.get("total"))));
            blogTagMapper.selectBatchIds(countByTag.keySet()).forEach(tag ->
                    tagCounts.add(new ActivityStatsVO.TagUsageVO(tag.getId(), tag.getName(), countByTag.get(tag.getId()))));
        }
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        blogPostMapper.selectMaps(new QueryWrapper<BlogPost>()
                        .select("category_id", "COUNT(*) AS total").isNotNull("category_id").groupBy("category_id"))
                .forEach(row -> categoryCounts.put(String.valueOf(row.get("category_id")), toInt(row.get("total"))));
        return ActivityStatsVO.builder().dayCounts(dayCounts).tagCounts(tagCounts).categoryCounts(categoryCounts).build();
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}