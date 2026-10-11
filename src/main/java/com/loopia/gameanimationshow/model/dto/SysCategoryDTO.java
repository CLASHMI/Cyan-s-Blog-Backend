package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增 / 修改入参
 */
@Data
public class SysCategoryDTO {

    private Integer id;

    @NotBlank(message = MessageConstant.Category.NAME_CANNOT_BE_EMPTY)
    @Size(max = 50, message = MessageConstant.Category.NAME_TOO_LONG)
    private String name;

    private Integer sort;
}
