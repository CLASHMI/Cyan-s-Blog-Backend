package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论列表查询入参
 */
@Data
public class BlogCommentQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    @NotNull(message = MessageConstant.Comment.POST_ID_CANNOT_BE_EMPTY)
    private Long postId;

    private String content;
}
