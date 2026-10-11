package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.loopia.gameanimationshow.service.BlogCommentService;
import com.loopia.gameanimationshow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogCommentServiceImpl extends ServiceImpl<BlogCommentMapper, BlogComment> implements BlogCommentService {

    private final SysUserService sysUserService;
    private final BlogPostMapper blogPostMapper;

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
        BlogPost postUpdate = new BlogPost();
        postUpdate.setId(postId);
        postUpdate.setAverageRating(avgRating);
        blogPostMapper.updateById(postUpdate);
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
