package com.loopia.gameanimationshow.model.dto;

import lombok.Data;

/**
 * 标签列表查询入参
 */
@Data
public class BlogTagQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private String name;
}
