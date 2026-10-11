package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 杂物柜添加 / 修改入参
 */
@Data
public class SiteResourceDTO {

    private Long id; // 修改时必填

    @NotBlank(message = MessageConstant.Resource.TITLE_CANNOT_BE_EMPTY)
    @Size(max = 100, message = MessageConstant.Resource.TITLE_TOO_LONG)
    private String title;

    @Size(max = 50, message = MessageConstant.Resource.DESC_TOO_LONG)
    private String description;

    @NotBlank(message = MessageConstant.Resource.URL_CANNOT_BE_EMPTY)
    @Size(max = 500, message = MessageConstant.Resource.URL_TOO_LONG)
    private String url;

    @Size(max = 255, message = MessageConstant.Resource.COVER_TOO_LONG)
    private String coverImage;
}
