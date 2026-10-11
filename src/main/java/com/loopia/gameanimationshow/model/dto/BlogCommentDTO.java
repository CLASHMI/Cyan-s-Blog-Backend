package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论 / 打分入参
 */
@Data
public class BlogCommentDTO {

    @NotNull(message = MessageConstant.Comment.POST_ID_CANNOT_BE_EMPTY)
    private Long postId;

    @Min(value = 0, message = MessageConstant.Comment.RATING_OUT_OF_RANGE)
    @Max(value = 5, message = MessageConstant.Comment.RATING_OUT_OF_RANGE)
    private Integer rating;

    @NotBlank(message = MessageConstant.Comment.CONTENT_CANNOT_BE_EMPTY)
    @Size(max = 2000, message = MessageConstant.Comment.CONTENT_TOO_LONG)
    private String content;
}
