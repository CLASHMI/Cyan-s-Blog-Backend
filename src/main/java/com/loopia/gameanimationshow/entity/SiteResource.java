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
 * 杂物柜资源
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("site_resource")
public class SiteResource implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String url;

    private String coverImage;

    private Integer likeCount;

    private LocalDateTime createTime;
}
