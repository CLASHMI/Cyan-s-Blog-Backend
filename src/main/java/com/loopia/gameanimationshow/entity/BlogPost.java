package com.loopia.gameanimationshow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 博客文章
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("blog_post")
public class BlogPost implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String coverImage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer likeCount;

    private BigDecimal averageRating;

    private Integer categoryId;
}
