package com.loopia.gameanimationshow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页数据看板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewStatsVO {

    private Long totalPosts;

    /* blog_comment 同时存放博客评论与留言板留言，所以它就是“累计互动” */
    private Long totalComments;

    private Long totalLikes;

    private Long totalVisitors;

    private Long totalTags;

    private Long totalCategories;
}