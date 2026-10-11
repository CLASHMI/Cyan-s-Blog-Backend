package com.loopia.gameanimationshow.model.vo;

import com.loopia.gameanimationshow.entity.BlogComment;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论视图 (附带评论者信息)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BlogCommentVO extends BlogComment {

    private String nickname;

    private String avatar;

    private Integer role;
}
