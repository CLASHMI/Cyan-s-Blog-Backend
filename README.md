# Cyan's Blog Backend

个人随笔博客的后端 API。Spring Boot 3 + MyBatis-Plus + MySQL + Redis，与前端仓库
[Cyan-s-Blog-Frontend](https://github.com/CLASHMI/Cyan-s-Blog-Frontend) 配套使用。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.5（Web / Validation / Cache / Data Redis） |
| MyBatis-Plus | 3.5.5（分页插件、Lambda 条件构造器） |
| 数据库 | MySQL 8（Druid 连接池） |
| 缓存 / 计数 | Redis（Spring Cache + 手写计数与分布式锁） |
| 鉴权 | JWT（`Authorization: Bearer <token>`） |
| 接口文档 | Knife4j + springdoc-openapi |
| 其他 | Hutool、腾讯云 COS（封面/媒体上传）、DeepSeek（AI 摘要 / 每日一言 / 心情文案 / 内容审核） |

## 主要能力

- **身份与鉴权**：站长登录（`admin.secret`）签发管理端 token；游客 `POST /user/init` 自动签发访客身份；拦截器统一校验，白名单接口免登录（见 `loopia.auth.white-list`）。
- **随笔内容**：文章增删改查、分类与标签、封面/视频上传（腾讯云 COS）。
- **互动**：点赞、取消点赞、评论与评分（写入前经 AI 内容审核，异常时 fail-open 放行）。
- **阅读量**：详情请求在 Redis 累加增量，`@Scheduled` 定时批量刷入 MySQL（默认 60 秒，`loopia.blog.view-flush-ms` 可调）；列表/详情返回「已落库值 + 未落库增量」。
- **数据看板**：全站聚合统计（总随笔/互动/点赞/访客/标签/分类、总字数、总阅读量含未落库增量）、活跃度热力图与标签云。
- **AI 能力**：看板助手说辞（Redis 缓存 30 分钟 + 分布式锁）、随笔摘要（`ai:postSummary:{postId}:{contentHash}` 缓存 6 小时 + 生成锁 + 5 分钟失败负缓存）、每日一言、今日心情文案。
- **其他**：天气代理（和风天气）、今日心情、杂物柜资源收藏与点赞。

## 运行要求

- JDK 17、Maven 3.8+
- MySQL 8（默认库名 `loopia_anim_hub`）
- Redis 6+

## 快速开始

```bash
# 1) 建库：导入仓库根目录的 script.sql（完整建表脚本，含全部表结构）
#    mysql -u <db-user> -p <db-name> < script.sql

# 2) 本地配置：新建 src/main/resources/application-dev.yml（已被 .gitignore 忽略，不会进仓库）
#    需要填写的键见下一节

# 3) 启动
mvn spring-boot:run
```

启动后服务地址为 `http://localhost:8080/api`，接口文档：

- Knife4j：`http://localhost:8080/api/doc.html`
- Swagger UI：`http://localhost:8080/api/swagger-ui/index.html`

## 配置说明

`application.yml` 只保留结构，涉及环境与密钥的值都用占位符引用 `loopia.*`；真实值放在
`application-dev.yml`（**含密钥，已在 `.gitignore` 中，请勿提交**）：

```yaml
loopia:
  datasource:
    host: 127.0.0.1
    port: 3306
    database: loopia_anim_hub
    username: <db-user>
    password: <db-password>
  redis:
    host: 127.0.0.1
    port: 6379
    database: 0
  jwt:
    secret: <jwt-secret>
    expiration: 604800000        # 毫秒
  ai:
    deepseek:
      api-key: <deepseek-api-key>
  weather:
    api-key: <qweather-key>
    api-host: <qweather-api-host>
    location: "101040100"        # 和风天气 Location ID
    city: 重庆
  tencent:
    cos:
      secret-id: <cos-secret-id>
      secret-key: <cos-secret-key>
  admin:
    secret: <admin-login-secret>
```

> 未配置 `loopia.ai.deepseek.api-key` 时，AI 相关接口会自动降级：内容审核直接放行，摘要返回空串由前端静默隐藏卡片。

## 接口总览

分组与完整参数见仓库内 [`接口文档.md`](./接口文档.md)。

| 分组 | 主要路径 |
| --- | --- |
| 用户与认证 | `POST /admin/login`、`POST /user/init`、`GET /user/recent`、`GET /user/ownerAvatar` |
| 分类 | `GET /category/list`、`POST /category/add`、`PUT /category/update`、`DELETE /category/delete/{id}` |
| 标签 | `GET /tag/list`、`POST /tag/add`、`PUT /tag/update`、`DELETE /tag/delete/{id}` |
| 随笔 | `GET /blog/list`、`GET /blog/recent`、`GET /blog/detail/{id}`、`POST /blog/add`、`PUT /blog/update`、`DELETE /blog/delete/{id}`、`POST /blog/upload`、`POST /blog/like/{id}`、`POST /blog/unlike/{id}` |
| 互动 | `GET /comment/list`、`POST /comment/add`、`DELETE /comment/delete/{id}` |
| 数据看板 | `GET /stats/overview`、`GET /stats/activity`、`GET /stats/post` |
| AI | `GET /ai/summary`、`GET /ai/postSummary`、`GET /ai/dailyQuote`、`PUT /ai/dailyQuote/manual`、`POST /ai/dailyQuote/generate` |
| 心情 | `GET /mood/today`、`POST /mood/select` |
| 天气 | `GET /weather/current`、`GET /weather/cities`、`PUT /weather/city` |
| 杂物柜 | `GET /resource/list`、`POST /resource/add`、`POST /resource/like/{id}` 等 |

统一响应结构为 `{ code, message, data }`：`200` 成功、`400` 参数错误、`401` 未登录 / 身份失效、
`429` 操作过于频繁、`503` 依赖服务（Redis / 上游 AI / 天气）暂不可用。

## 目录结构

```
src/main/java/com/loopia/gameanimationshow/
├── common/        # 统一响应、分页结果、JWT、用户上下文、工具
├── config/        # 跨域、MyBatis-Plus、Redis 缓存、WebMvc（拦截器 / 静态资源）
├── constant/      # 系统常量与提示文案
├── controller/    # 各业务 Controller
├── entity/        # 数据库实体
├── exception/     # 业务异常与全局异常处理
├── interceptor/   # 鉴权拦截器
├── mapper/        # MyBatis-Plus Mapper
├── model/         # DTO / VO
└── service/       # 业务接口与实现
src/main/resources/
├── application.yml
└── prompts/       # AI 提示词（看板人设 / 摘要 / 内容审核）
```

建库脚本 `script.sql` 位于**仓库根目录**，包含全部表结构。

## 部署要点

- 打包：`mvn clean package`，产物为 `target/cyan-blog-backend-0.0.1-SNAPSHOT.jar`。
- 生产环境：`java -jar cyan-blog-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`，通过环境变量或配置中心注入 `loopia.*`。
- 服务自带 context-path `/api`；前端（非 localhost）请求 `/api`，反向代理时注意不要重复拼接。
- 阅读量计数与刷库、AI 缓存与分布式锁都依赖 Redis；Redis 不可用时相关接口按设计降级，而不是直接报错。
- `uploads/` 用于本地上传资源，已被 `.gitignore` 忽略，部署时请单独挂载 / 备份。

## 相关仓库

- 前端：[CLASHMI/Cyan-s-Blog-Frontend](https://github.com/CLASHMI/Cyan-s-Blog-Frontend)
