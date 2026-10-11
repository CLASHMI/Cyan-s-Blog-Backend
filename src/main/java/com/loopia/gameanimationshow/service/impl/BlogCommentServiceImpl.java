package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.ClientIpUtil;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.SystemConstant;
import com.loopia.gameanimationshow.entity.BlogComment;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.entity.SysUser;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.BlogCommentMapper;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import com.loopia.gameanimationshow.model.dto.BlogCommentDTO;
import com.loopia.gameanimationshow.model.dto.BlogCommentQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogCommentVO;
import com.loopia.gameanimationshow.service.AiService;
import com.loopia.gameanimationshow.service.BlogCommentService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.loopia.gameanimationshow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogCommentServiceImpl extends ServiceImpl<BlogCommentMapper, BlogComment> implements BlogCommentService {

    /* 评论限流：单条 15 秒冷却 + 每人每日 30 条上限 */
    private static final Duration COMMENT_LOCK_TTL = Duration.ofSeconds(15);
    private static final int COMMENT_DAILY_LIMIT = 30;
    /* 同一 IP 每日评论硬上限，防止刷小号绕过 userId 限流 */
    private static final int COMMENT_IP_DAILY_LIMIT = 100;

    private final SysUserService sysUserService;
    private final BlogPostMapper blogPostMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiService aiService;

    /* 限流“每日”口径统一使用上海时区，与其它模块保持一致 */
    private final Clock commentClock = Clock.system(ZoneId.of("Asia/Shanghai"));

    @Override
    @Cacheable(value = "commentList")
    public PageResult<BlogCommentVO> listComments(BlogCommentQueryDTO queryDTO) {
        Page<BlogComment> page = this.lambdaQuery()
                .eq(BlogComment::getPostId, queryDTO.getPostId())
                .like(StringUtils.hasText(queryDTO.getContent()), BlogComment::getContent, queryDTO.getContent())
                .orderByDesc(BlogComment::getCreateTime)
                .page(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()));

        List<BlogComment> comments = page.getRecords();
        if (comments.isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of());
        }
        Set<Long> userIds = comments.stream().map(BlogComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user, (v1, v2) -> v1));

        List<BlogCommentVO> voList = comments.stream().map(comment -> {
            BlogCommentVO vo = new BlogCommentVO();
            BeanUtils.copyProperties(comment, vo);
            SysUser user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setRole(user.getRole());
            } else {
                vo.setNickname("已注销访客");
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    @CacheEvict(value = {"commentList", "statsOverview", "postList", "postDetail"}, allEntries = true)
    public void addComment(BlogCommentDTO commentDTO) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED);
        }

        // 第一道防线：限流（15 秒防刷 + 每日 30 条上限），先于内容审核
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent("comment:lock:" + userId, "1", COMMENT_LOCK_TTL))) {
            throw new ServiceException(429, "发言太频繁，请15秒后再试");
        }
        String dailyKey = "comment:daily:" + userId + ":" + LocalDate.now(commentClock);
        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(dailyKey, Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > COMMENT_DAILY_LIMIT) {
            throw new ServiceException(429, "今日评论次数已达上限");
        }
        // 第二层：同一 IP 每日上限，防止用多个游客账号绕过 userId 限流
        String clientIp = ClientIpUtil.currentIp();
        if (clientIp != null) {
            String ipDailyKey = "comment:ip:" + clientIp + ":" + LocalDate.now(commentClock);
            Long ipDailyCount = stringRedisTemplate.opsForValue().increment(ipDailyKey);
            if (ipDailyCount != null && ipDailyCount == 1L) {
                stringRedisTemplate.expire(ipDailyKey, Duration.ofHours(24));
            }
            if (ipDailyCount != null && ipDailyCount > COMMENT_IP_DAILY_LIMIT) {
                throw new ServiceException(429, "当前网络今日评论次数已达上限");
            }
        }
        BlogComment comment = new BlogComment();
        BeanUtils.copyProperties(commentDTO, comment);
        comment.setUserId(userId);
        if (comment.getRating() == null) {
            comment.setRating(0);
        }
        // 留言板不参与打分
        if (SystemConstant.Comment.GLOBAL_BOARD_POST_ID.equals(comment.getPostId())) {
            comment.setRating(0);
        }
        comment.setCreateTime(LocalDateTime.now());
        // 第二道防线：AI 内容审核，放行后才落库
        if (!aiService.moderateText(commentDTO.getContent())) {
            /* 违规被拒时主动释放 15 秒冷却锁：用户改完内容即可立刻重试，不被自己的试错卡住。
             * 每日额度不退还，违规仍然计入当日次数。 */
            stringRedisTemplate.delete("comment:lock:" + userId);
            throw new ServiceException("评论包含违规或敏感内容，请修改后重试");
        }

        this.save(comment);

        if (!SystemConstant.Comment.GLOBAL_BOARD_POST_ID.equals(comment.getPostId()) && comment.getRating() > 0) {
            recalculateAverageRating(comment.getPostId());
        }
    }

    // 重新计算该博客的平均分并回写
    private void recalculateAverageRating(Long postId) {
        QueryWrapper<BlogComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("AVG(rating) as avg_rating")
                .eq("post_id", postId)
                .gt("rating", 0);
        Map<String, Object> map = this.getMap(queryWrapper);
        BigDecimal avgRating = BigDecimal.ZERO;
        if (map != null && map.get("avg_rating") != null) {
            avgRating = new BigDecimal(map.get("avg_rating").toString()).setScale(1, RoundingMode.HALF_UP);
        }
        blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                .set(BlogPost::getAverageRating, avgRating)
                .setSql("update_time = update_time")
                .eq(BlogPost::getId, postId));
    }

    @Override
    @CacheEvict(value = {"commentList", "statsOverview", "postList", "postDetail"}, allEntries = true)
    public void deleteComment(Long id) {
        UserContext.requireAdmin();
        BlogComment comment = this.getById(id);
        if (comment != null) {
            Long postId = comment.getPostId();
            this.removeById(id);
            if (!SystemConstant.Comment.GLOBAL_BOARD_POST_ID.equals(postId)) {
                recalculateAverageRating(postId);
            }
        }
    }
}
