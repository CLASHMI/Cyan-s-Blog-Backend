package com.loopia.gameanimationshow.model.vo;

import com.loopia.gameanimationshow.entity.BlogPost;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 博客列表 / 详情视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BlogPostVO extends BlogPost {

    private String categoryName;

    private List<String> tagNames;
}
