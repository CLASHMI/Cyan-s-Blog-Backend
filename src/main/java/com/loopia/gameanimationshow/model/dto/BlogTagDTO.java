package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签新增 / 修改入参
 */
@Data
public class BlogTagDTO {

    private Integer id;

    @NotBlank(message = MessageConstant.Tag.NAME_CANNOT_BE_EMPTY)
    @Size(max = 50, message = MessageConstant.Tag.NAME_TOO_LONG)
    private String name;
}
