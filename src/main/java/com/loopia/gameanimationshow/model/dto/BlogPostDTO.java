package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 博客发布 / 修改入参
 */
@Data
public class BlogPostDTO {

    private Long id; // 修改时必填

    @NotBlank(message = MessageConstant.Blog.TITLE_CANNOT_BE_EMPTY)
    @Size(max = 100, message = MessageConstant.Blog.TITLE_TOO_LONG)
    private String title;

    private String content;

    private String coverImage;

    @NotNull(message = MessageConstant.Blog.CATEGORY_CANNOT_BE_EMPTY)
    private Integer categoryId;

    @Size(max = 5, message = MessageConstant.Blog.TAG_TOO_MANY)
    private List<Integer> tagIds;
}
