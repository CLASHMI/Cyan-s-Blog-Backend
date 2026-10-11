package com.loopia.gameanimationshow.model.dto;

import lombok.Data;

/**
 * 杂物柜列表查询入参
 */
@Data
public class SiteResourceQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /* 同时模糊匹配 title 与 description */
    private String keyword;

    /* 1-按热度(like_count)；其余按最新(create_time) */
    private Integer sortStrategy;
}
