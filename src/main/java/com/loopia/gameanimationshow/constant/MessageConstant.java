package com.loopia.gameanimationshow.constant;

/**
 * 全局业务提示语
 */
public class MessageConstant {

    /**
     * 通用模块
     */
    public static class Common {
        public static final String OPERATION_SUCCESS = "操作成功";
        public static final String DATA_EXIST = "数据已存在，请勿重复操作";
        public static final String PARAM_ERROR = "参数错误";
        public static final String FILE_TOO_LARGE = "上传的图片过大，请压缩后再试";
        public static final String NETWORK_ABORT = "网络波动导致上传失败，请重试";
        public static final String ADD_SUCCESS = "添加成功";
        public static final String LOGIN_SUCCESS = "登陆成功";
        public static final String UPDATE_SUCCESS = "修改成功";
        public static final String DELETE_SUCCESS = "删除成功";
        public static final String QUERY_SUCCESS = "查询成功";
    }

    /**
     * 认证/拦截模块
     */
    public static class Auth {
        public static final String NOT_LOGIN = "未识别到您的身份信息";
        public static final String TOKEN_EXPIRED = "登录状态已过期，页面将自动刷新";
        public static final String TOKEN_INVALID = "身份凭证无效，页面将自动刷新";
    }

    /**
     * 用户与权限业务模块
     */
    public static class User {
        public static final String SESSION_INVALID = "尚未初始化用户会话，请先刷新页面";
        public static final String SECRET_ERROR = "暗号错误，非站长请绕道！";
        public static final String NICKNAME_CANNOT_BE_EMPTY = "昵称不能为空";
        public static final String SECRET_CANNOT_BE_EMPTY = "管理员暗号不能为空";
        public static final String NICKNAME_TOO_LONG = "昵称长度不能超过10个字符";
        public static final String AVATAR_CANNOT_BE_EMPTY = "请先选择要上传的头像文件";
        public static final String AVATAR_TOO_LARGE = "头像大小不能超过 1MB";
        public static final String AVATAR_TYPE_INVALID = "头像仅支持 JPG / PNG / GIF / WEBP 图片格式";
        public static final String AVATAR_UPDATE_SUCCESS = "头像已更新";
    }

    /**
     * 分类业务模块
     */
    public static class Category {
        public static final String NAME_CANNOT_BE_EMPTY = "分类名称不能为空";
        public static final String ID_CANNOT_BE_EMPTY = "要修改的分类ID不能为空";
        public static final String CATEGORY_IN_USE = "该分类下还有关联的博客，请先解除关联";
        public static final String NAME_TOO_LONG = "分类名称不能超过50个字";
        public static final String NAME_DUPLICATE = "分类名称已存在";
    }

    /**
     * 标签业务模块
     */
    public static class Tag {
        public static final String NAME_CANNOT_BE_EMPTY = "标签名称不能为空";
        public static final String ID_CANNOT_BE_EMPTY = "要修改的标签ID不能为空";
        public static final String TAG_IN_USE = "该标签下还有关联的博客，请先解除关联";
        public static final String NAME_TOO_LONG = "标签名称不能超过50个字";
        public static final String NAME_DUPLICATE = "标签名称已存在";
    }

    /**
     * 博客业务模块
     */
    public static class Blog {
        public static final String TITLE_CANNOT_BE_EMPTY = "博客标题不能为空";
        public static final String TITLE_TOO_LONG = "博客标题不能超过100个字";
        public static final String ID_CANNOT_BE_EMPTY = "博客ID不能为空";
        public static final String CATEGORY_CANNOT_BE_EMPTY = "请选择一个博客分类";
        public static final String TAG_TOO_MANY = "每篇随笔最多选择 5 个标签";
        public static final String POST_NOT_FOUND = "这篇博客不存在或已被删除";
        public static final String COVER_CANNOT_BE_EMPTY = "请先选择要上传的文件";
        public static final String UPLOAD_FAILED = "文件上传失败，请检查服务器磁盘空间或权限";
    }

    /**
     * 评论与打分业务模块
     */
    /**
     * 杂物柜业务模块
     */
    public static class Resource {
        public static final String TITLE_CANNOT_BE_EMPTY = "资源标题不能为空";
        public static final String TITLE_TOO_LONG = "资源标题不能超过100个字";
        public static final String URL_CANNOT_BE_EMPTY = "资源链接不能为空";
        public static final String URL_TOO_LONG = "资源链接不能超过500个字";
        public static final String DESC_TOO_LONG = "资源简介不能超过50个字";
        public static final String COVER_TOO_LONG = "封面图地址不能超过255个字";
        public static final String ID_CANNOT_BE_EMPTY = "要修改的资源ID不能为空";
        public static final String RESOURCE_NOT_FOUND = "这个资源不存在或已被删除";
        public static final String LIKE_SUCCESS = "点赞成功！";
        public static final String UNLIKE_SUCCESS = "已取消点赞";
    }

    public static class Comment {
        public static final String POST_ID_CANNOT_BE_EMPTY = "关联博客ID不能为空（留言板请传0）";
        public static final String RATING_OUT_OF_RANGE = "评分必须在0-5星之间";
        public static final String CONTENT_CANNOT_BE_EMPTY = "评论内容不能为空";
        public static final String CONTENT_TOO_LONG = "评论内容不能超过2000字";
        public static final String ALREADY_LIKED = "您已经点过赞啦";
    }
}
