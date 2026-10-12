package com.loopia.gameanimationshow.model.dto;

import lombok.Data;

/**
 * 博客列表查询入参
 */
@Data
public class BlogPostQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private Integer categoryId;

    private Integer tagId;

    private String keyword; // 标题或标签关键字

    private Integer sortStrategy; // 0-最新 1-按浏览量(view_count) 2-按评分 3-按点赞(like_count)

    private Integer isRecommend; // 1-只看站长精选（随机 5 篇）
}
