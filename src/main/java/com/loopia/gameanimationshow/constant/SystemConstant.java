package com.loopia.gameanimationshow.constant;

/**
 * 全局业务字典常量
 */
public class SystemConstant {

    /**
     * 权限角色 (sys_user.role)
     */
    public static class Role {
        public static final Integer VISITOR = 0; // 游客
        public static final Integer ADMIN = 1;   // 站长/管理员
    }

    /**
     * 评论约定值
     */
    public static class Comment {
        public static final Long GLOBAL_BOARD_POST_ID = 0L; // 留言板关联的虚拟博客ID
    }
}
