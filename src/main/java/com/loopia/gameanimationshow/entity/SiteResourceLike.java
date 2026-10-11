package com.loopia.gameanimationshow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 杂物柜资源点赞记录（user_id + resource_id 联合唯一，索引 uk_user_resource）
 */
@Data
@TableName("site_resource_like")
public class SiteResourceLike implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resourceId;

    private LocalDateTime createTime;
}
