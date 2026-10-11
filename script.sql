CREATE TABLE blog_comment
(
    id          bigint AUTO_INCREMENT
        PRIMARY KEY,
    user_id     bigint                                   NOT NULL COMMENT '评论者ID (关联 sys_user.id)',
    post_id     bigint                                   NOT NULL COMMENT '关联的内容ID。如果是【全局留言板】，此值为 0',
    rating      tinyint  DEFAULT 0                       NULL COMMENT '打分 (1~5星)。留言板默认为 0',
    content     varchar(2000) COLLATE utf8mb4_general_ci NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP       NULL
)
    COMMENT '单层评论与留言板表' CHARSET = utf8mb4;

CREATE INDEX idx_post_id
    ON blog_comment (post_id);

CREATE INDEX idx_user_id
    ON blog_comment (user_id);

CREATE TABLE blog_like
(
    id          bigint AUTO_INCREMENT
        PRIMARY KEY,
    user_id     bigint                             NOT NULL COMMENT '用户/游客ID (关联 sys_user.id)',
    post_id     bigint                             NOT NULL COMMENT '作品ID (关联 content_post.id)',
    create_time datetime DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT uk_user_post
        UNIQUE (user_id, post_id) COMMENT '核心防御：单用户单作品只能有一条记录'
)
    COMMENT '点赞关联记录表' CHARSET = utf8mb4;

CREATE TABLE blog_post
(
    id             bigint AUTO_INCREMENT
        PRIMARY KEY,
    title          varchar(100)                            NOT NULL COMMENT '作品标题',
    content        text                                    NULL COMMENT '作品描述/创作心得',
    cover_image    varchar(255)                            NULL COMMENT '博客封面图(原media_url)',
    create_time    datetime      DEFAULT CURRENT_TIMESTAMP NULL,
    update_time    datetime                                NULL COMMENT '文章最后编辑时间（只在编辑时由应用写入，点赞/评分不改动）',
    like_count     int           DEFAULT 0                 NULL COMMENT '点赞数',
    view_count     int           DEFAULT 0                 NULL COMMENT '阅读量/点击量',
    average_rating decimal(2, 1) DEFAULT 0.0               NULL COMMENT '平均评分 (如 4.5)',
    category_id    int                                     NULL COMMENT '博客分类ID (关联 sys_category.id)'
)
    COMMENT '作品与POSE发布表' CHARSET = utf8mb4;

-- 最近更新列表（/blog/recent）按 update_time + id 倒序取前几条，走这个联合索引
CREATE INDEX idx_blog_post_recent
    ON blog_post (update_time DESC, id DESC);

CREATE TABLE blog_post_tag
(
    post_id bigint NOT NULL COMMENT '博客ID',
    tag_id  int    NOT NULL COMMENT '标签ID',
    PRIMARY KEY (post_id, tag_id)
)
    COMMENT '博客与标签关联表' CHARSET = utf8mb4;

CREATE TABLE blog_tag
(
    id          int AUTO_INCREMENT
        PRIMARY KEY,
    name        varchar(50)                        NOT NULL COMMENT '标签名称 (如: Java, 并发, 踩坑)',
    create_time datetime DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT uk_tag_name
        UNIQUE (name) COMMENT '标签名称唯一约束'
)
    COMMENT '博客标签表' CHARSET = utf8mb4;

CREATE TABLE site_resource
(
    id          bigint AUTO_INCREMENT
        PRIMARY KEY,
    title       varchar(100)                       NOT NULL COMMENT '资源名称',
    description varchar(500)                       NULL COMMENT '简短描述',
    url         varchar(500)                       NOT NULL COMMENT '资源链接',
    cover_image varchar(255)                       NULL COMMENT '小图标或首字母Logo链接',
    like_count  int      DEFAULT 0                 NULL COMMENT '点赞数',
    create_time datetime DEFAULT CURRENT_TIMESTAMP NULL COMMENT '收录时间'
)
    COMMENT '杂物柜资源表' CHARSET = utf8mb4;

CREATE TABLE site_resource_like
(
    id          bigint AUTO_INCREMENT
        PRIMARY KEY,
    user_id     bigint                             NOT NULL COMMENT '用户/游客ID (关联 sys_user.id)',
    resource_id bigint                             NOT NULL COMMENT '资源ID (关联 site_resource.id)',
    create_time datetime DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT uk_user_resource
        UNIQUE (user_id, resource_id) COMMENT '单用户单资源只能点赞一次'
)
    COMMENT '资源点赞记录表' CHARSET = utf8mb4;

CREATE TABLE sys_category
(
    id          int AUTO_INCREMENT
        PRIMARY KEY,
    name        varchar(50)                        NOT NULL COMMENT '分类名称 (如: 女性, 怪物, 跑步, 连招)',
    sort        int      DEFAULT 0                 NULL COMMENT '排序权重',
    create_time datetime DEFAULT CURRENT_TIMESTAMP NULL
)
    COMMENT '系统分类与标签表' CHARSET = utf8mb4;

CREATE TABLE sys_user
(
    id          bigint AUTO_INCREMENT
        PRIMARY KEY,
    uuid        varchar(64)                            NOT NULL COMMENT '设备唯一标识',
    nickname    varchar(50)                            NOT NULL COMMENT '昵称',
    avatar      varchar(255) DEFAULT ''                NULL COMMENT '头像',
    role        tinyint      DEFAULT 0                 NULL COMMENT '0-游客, 1-管理员',
    create_time datetime     DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT uuid
        UNIQUE (uuid)
)
    COMMENT '用户与游客表' CHARSET = utf8mb4;


