package com.loopia.gameanimationshow.model.dto;

import lombok.Data;

/**
 * 分类列表查询入参
 */
@Data
public class SysCategoryQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String name;
}
