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

    private Integer sortStrategy; // 1-按热度(点赞/评分)
}
