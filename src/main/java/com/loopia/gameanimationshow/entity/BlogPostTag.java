package com.loopia.gameanimationshow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 博客与标签的关联 (post_id + tag_id 联合主键)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("blog_post_tag")
public class BlogPostTag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long postId;

    private Integer tagId;
}
