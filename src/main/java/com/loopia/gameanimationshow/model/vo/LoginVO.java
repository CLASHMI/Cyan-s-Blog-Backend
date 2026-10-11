package com.loopia.gameanimationshow.model.vo;

import com.loopia.gameanimationshow.entity.SysUser;
import lombok.Data;

/**
 * LoginVO 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 16:02 
 */
@Data
public class LoginVO {
    private String token;
    private SysUser userInfo;
}
