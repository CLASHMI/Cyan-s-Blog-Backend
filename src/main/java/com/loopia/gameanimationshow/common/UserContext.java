package com.loopia.gameanimationshow.common;

import com.loopia.gameanimationshow.constant.SystemConstant;
import com.loopia.gameanimationshow.exception.ServiceException;

/**
 * UserContext 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 16:23 
 */
public class UserContext {
    private static final ThreadLocal<Integer> ROLE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setCurrentId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getCurrentId() {
        return USER_ID_HOLDER.get();
    }

    public static void setRole(Integer role) {
        ROLE_HOLDER.set(role);
    }

    public static Integer getRole() {
        return ROLE_HOLDER.get();
    }

    /* 非站长直接 403，所有写操作统一走这里 */
    public static void requireAdmin() {
        if (!SystemConstant.Role.ADMIN.equals(getRole())) {
            throw new ServiceException(ResultCode.FORBIDDEN);
        }
    }

    public static void remove() {
        USER_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
    }
}

