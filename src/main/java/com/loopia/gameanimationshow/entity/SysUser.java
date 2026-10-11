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
 * SysUser 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 11:25 
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_user")
public class SysUser implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备唯一标识
     */
    private String uuid;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 角色: 0-游客, 1-管理员
     */
    private Integer role;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
