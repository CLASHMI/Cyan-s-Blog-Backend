package com.loopia.gameanimationshow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 博客评论 / 留言
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("blog_comment")
public class BlogComment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long postId; // 留言板为 0

    private Integer rating; // 1~5 星，留言板为 0

    private String content;

    private LocalDateTime createTime;
}
