package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.model.vo.DailyQuoteVO;
import com.loopia.gameanimationshow.entity.BlogComment;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.BlogCommentMapper;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import com.loopia.gameanimationshow.service.AiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    /* ---------------- 看板助手说辞：缓存与并发锁都放 Redis，多实例 / 多线程共用同一份真相 ---------------- */
    private static final String SUMMARY_CACHE_ADMIN = "ai:muse:summary:admin";
    private static final String SUMMARY_CACHE_GUEST = "ai:muse:summary:guest";
    /* 说辞缓存 30 分钟，与旧的本地缓存时长保持一致 */
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofMinutes(30);
    /* 生成期间的锁租约：比 AI 请求超时略长，进程崩了也会自动释放 */
    private static final Duration SUMMARY_LOCK_TTL = Duration.ofSeconds(60);

    @Value("${loopia.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${loopia.ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("classpath:prompts/muse-persona.md")
    private Resource personaResource;

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock quoteClock = Clock.system(ZoneId.of("Asia/Shanghai"));

    /* 全局唯一的 DeepSeek 连接池：本类所有外部请求都走它，禁止再出现 HttpClient.newHttpClient()。
       HttpClient.Builder 只能设连接超时，读超时由每个 HttpRequest 自己声明（见下方各处 timeout）。 */
    private static final HttpClient deepseekHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final BlogPostMapper blogPostMapper;
    private final BlogCommentMapper blogCommentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* 通用解锁脚本：只有锁的持有者能删掉自己的锁，租约过期换了主人也不会误删 */
    private static final DefaultRedisScript<Long> LOCK_RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    // 只有当前锁持有者可以发布；锁过期的慢请求不能覆盖后来保存的手写内容。
    private static final DefaultRedisScript<Long> QUOTE_PUBLISH = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end "
                    + "redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3]); return 1", Long.class);

    @Override
    public DailyQuoteVO dailyQuote() {
        String date = ZonedDateTime.now(quoteClock).toLocalDate().toString();
        String key = "ai:dailyQuote:" + date;
        try {
            String text = stringRedisTemplate.opsForValue().get(key);
            boolean attempted = stringRedisTemplate.opsForValue().get(key + ":attempt") != null;
            return new DailyQuoteVO(date, text == null ? "" : text, attempted);
        } catch (Exception e) {
            throw quoteUnavailable(e);
        }
    }

    @Override
    public DailyQuoteVO saveDailyQuote(String text) {
        UserContext.requireAdmin();
        String quote = text == null ? "" : text.strip();
        if (!validQuote(quote)) throw new ServiceException(400, "请填写1至20字的单行短句（含标点）");
        return publishDailyQuote(quote, false);
    }

    @Override
    public DailyQuoteVO generateDailyQuote() {
        UserContext.requireAdmin();
        return publishDailyQuote(null, true);
    }

    private DailyQuoteVO publishDailyQuote(String text, boolean generate) {
        ZonedDateTime now = ZonedDateTime.now(quoteClock);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        String date = now.toLocalDate().toString();
        String key = "ai:dailyQuote:" + date;
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            if (generate && !StringUtils.hasText(apiKey)) throw new ServiceException(503, "今日一言尚未配置");
            if (generate && Duration.between(now, midnight).getSeconds() <= 35)
                throw new ServiceException(503, "即将换日，请稍后生成今日一言");
            locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key + ":lock", token, Duration.ofSeconds(90)));
            if (!locked) throw new ServiceException(429, "今日一言正在保存或生成，请稍后查看");
            if (generate) {
                // 日期键 + 持久调用标记：并发、失败、刷新和进程重启都不重复消费当天额度。
                if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                        .setIfAbsent(key + ":attempt", "1", Duration.ofDays(2))))
                    throw new ServiceException(429, "今日AI生成机会已使用，仍可手动填写");
                text = requestDailyQuote();
            }
            boolean attempted = stringRedisTemplate.opsForValue().get(key + ":attempt") != null;
            Duration ttl = Duration.between(ZonedDateTime.now(quoteClock), midnight);
            if (ttl.isNegative() || ttl.isZero()) throw new ServiceException(503, "日期已变化，请重新编辑今日一言");
            Long published = stringRedisTemplate.execute(QUOTE_PUBLISH, List.of(key + ":lock", key),
                    token, text, String.valueOf(ttl.toMillis()));
            if (!Long.valueOf(1).equals(published)) throw new ServiceException(409, "操作已超时，请刷新后重新编辑");
            return new DailyQuoteVO(date, text, attempted);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw quoteUnavailable(e);
        } finally {
            if (locked) releaseLock(key + ":lock", token);
        }
    }

    private String requestDailyQuote() throws Exception {
        Map<String, Object> body = Map.of(
                "model", SUMMARY_MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", "只输出一句有哲理的中文短句，含标点不超过20个字。克制、清晰。不署名、不加引号、不解释。"),
                        Map.of("role", "user", "content", "生成今天的一句话。")),
                "max_tokens", SUMMARY_MAX_TOKENS, "temperature", 0.8, "stream", false);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        HttpResponse<String> response = deepseekHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new ServiceException(503, "AI生成失败，原内容已保留，请手动填写");
        String quote = objectMapper.readTree(response.body()).path("choices").path(0)
                .path("message").path("content").asText("").strip()
                .replaceAll("^[\"“‘「『]+|[\"”’」』]+$", "").strip();
        // 不截断半句话，也不重试消耗额度。
        if (!validQuote(quote)) throw new ServiceException(503, "AI未返回有效短句，原内容已保留，请手动填写");
        return quote;
    }

    private boolean validQuote(String quote) {
        return !quote.isBlank() && quote.codePointCount(0, quote.length()) <= 20
                && quote.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029);
    }

    private ServiceException quoteUnavailable(Exception e) {
        log.warn("今日一言不可用，原因类型：{}", e.getClass().getSimpleName());
        // Redis 故障时绝不绕过当日调用标记。
        return new ServiceException(503, "今日一言暂不可用，请稍后读取确认，原内容不会被清空");
    }

    @Override
    public String generateMoodText(String mood, String weather) {
        if (!StringUtils.hasText(apiKey)) throw new ServiceException(503, "心情文案尚未配置");
        try {
            Map<String, Object> body = Map.of(
                    "model", SUMMARY_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", "为个人博客写一句有文笔的第一人称心情短句。以给定心情为主，氛围只作为隐性的创作灵感，用具体而细微的内心感受表达，温柔、克制、不说教、不堆砌辞藻。禁止提及天气、气象现象、地名、温度、风向或天气数据；不要解释心情与天气的关系。含标点不超过36字，只输出一句正文，不加引号、表情、署名或说明。氛围缺失时只写心情。"),
                            Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                                    "心情", mood, "创作氛围", weather == null ? "无" : weather)))),
                    "max_tokens", SUMMARY_MAX_TOKENS, "temperature", 0.8, "stream", false);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = deepseekHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new ServiceException(503, "心情文案暂不可用");
            String text = objectMapper.readTree(response.body()).path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            if (text.isBlank() || text.codePointCount(0, text.length()) > 36 || text.contains("\n") || text.contains("\r"))
                throw new ServiceException(503, "心情文案格式不符");
            return text;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("心情文案不可用，原因类型：{}", e.getClass().getSimpleName());
            throw new ServiceException(503, "心情文案暂不可用");
        }
    }

    private String baseSystemPrompt = "";

    // ---------------- AI 文章摘要 ----------------
    @Value("classpath:prompts/post-summary.md")
    private Resource summaryPromptResource;

    private String summarySystemPrompt = "";

    /* 摘要按「文章 ID + 内容指纹」缓存，正文改动后自动失效 */
    private static final String SUMMARY_MODEL = "deepseek-flash";
    private static final long SUMMARY_CACHE_DURATION = 6 * 60 * 60 * 1000L;
    private static final int SUMMARY_MAX_INPUT_CHARS = 6000;
    private static final int SUMMARY_MAX_TOKENS = 2048;
    /* 摘要硬上限：与前端预留方框的字数一致，超出就截到最近一句句末 */
    private static final int SUMMARY_MAX_CHARS = 150;
    /* 正文低于这个字数就不值得动用大模型，直接给一句兜底 */
    private static final int SUMMARY_MIN_CONTENT_CHARS = 20;
    private static final String SUMMARY_SHORT_POST_FALLBACK = "这篇随笔很短，短到还没展开就已经说完了。AI 就不多做提炼，直接看原文更有味道。";

    private final Map<Long, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initPrompt() {
        try {
            baseSystemPrompt = StreamUtils.copyToString(personaResource.getInputStream(), StandardCharsets.UTF_8);
            log.info("看板助手设定集加载成功！");
        } catch (Exception e) {
            log.error("加载看板助手设定集失败", e);
        }
    }

    /**
     * 为指定随笔生成 AI 摘要：复用现有 DeepSeek 通道，按文章维度缓存。
     * 文章不存在、没有正文或调用失败时返回空串，交给前端隐藏摘要卡片。
     */
    @Override
    public String summarizePost(Long postId) {
        if (postId == null) {
            return "";
        }
        BlogPost post = blogPostMapper.selectById(postId);
        if (post == null || !StringUtils.hasText(post.getContent())) {
            return "";
        }

        String content = post.getContent().trim();
        if (content.length() < SUMMARY_MIN_CONTENT_CHARS) {
            return SUMMARY_SHORT_POST_FALLBACK;
        }
        int fingerprint = content.hashCode();
        CachedSummary cached = summaryCache.get(postId);
        if (cached != null && cached.contentHash() == fingerprint
                && System.currentTimeMillis() < cached.expireTime()) {
            return cached.text();
        }

        if (!StringUtils.hasText(summarySystemPrompt)) {
            log.warn("AI 摘要提示词尚未加载，跳过摘要生成，postId={}", postId);
            return "";
        }

        String title = StringUtils.hasText(post.getTitle()) ? post.getTitle().trim() : "无标题随笔";
        String summary = requestPostSummary(postId, title, content);
        if (StringUtils.hasText(summary)) {
            summaryCache.put(postId,
                    new CachedSummary(fingerprint, summary, System.currentTimeMillis() + SUMMARY_CACHE_DURATION));
        }
        return summary;
    }

    /* 摘要请求超时：不能让读者对着“正在阅读”干等一分钟，20 秒拿不到就先放弃 */
    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(20);
    /* 上游偶尔会快速返回空正文（reasoning 吃满预算），这种重试一次很划算；
       超时或网络异常不重试——那只会让读者白等两倍时间 */
    private static final int SUMMARY_ATTEMPTS = 2;

    private String requestPostSummary(Long postId, String title, String content) {
        String clipped = content.length() > SUMMARY_MAX_INPUT_CHARS
                ? content.substring(0, SUMMARY_MAX_INPUT_CHARS)
                : content;
        String userPrompt = "文章标题：" + title + "\n\n文章正文：\n" + clipped;
        for (int attempt = 1; attempt <= SUMMARY_ATTEMPTS; attempt++) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", SUMMARY_MODEL);
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", summarySystemPrompt));
                messages.add(Map.of("role", "user", "content", userPrompt));
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.4);
                /* deepseek-flash 会先产出 reasoning，预算要同时容下推理与正文 */
                requestBody.put("max_tokens", SUMMARY_MAX_TOKENS);
                requestBody.put("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(SUMMARY_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<String> response = deepseekHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choice = root.path("choices").path(0);
                String text = choice.path("message").path("content").asText("").trim();
                if (StringUtils.hasText(text)) {
                    return clampSummary(text);
                }
                log.warn("生成随笔摘要返回空正文，postId={}，attempt={}，finishReason={}，body={}",
                        postId, attempt, choice.path("finish_reason").asText("-"), response.body());
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.error("生成随笔摘要异常，postId={}，attempt={}", postId, attempt, e);
                /* 超时或网络异常：再试一次只会让读者多等一份时间，直接放弃 */
                return "";
            }
        }
        return "";
    }

    /**
     * 摘要硬上限截断：优先截到最近一句的句末，避免半句话；找不到句末才硬截并补省略号。
     */
    private String clampSummary(String text) {
        if (text.length() <= SUMMARY_MAX_CHARS) {
            return text;
        }
        String head = text.substring(0, SUMMARY_MAX_CHARS);
        int cut = Math.max(Math.max(head.lastIndexOf('。'), head.lastIndexOf('！')),
                Math.max(head.lastIndexOf('？'), head.lastIndexOf('…')));
        if (cut >= SUMMARY_MAX_CHARS / 2) {
            return head.substring(0, cut + 1);
        }
        return head.substring(0, SUMMARY_MAX_CHARS - 1) + "…";
    }

    @PostConstruct
    public void initSummaryPrompt() {
        try {
            summarySystemPrompt = StreamUtils.copyToString(summaryPromptResource.getInputStream(),
                    StandardCharsets.UTF_8);
            log.info("AI 摘要提示词加载成功！");
        } catch (Exception e) {
            log.error("加载 AI 摘要提示词失败", e);
        }
    }

    private record CachedSummary(int contentHash, String text, long expireTime) {
    }

    /**
     * 看板助手说辞：无锁阻塞、无 synchronized。
     * 命中 Redis 缓存直接返回；未命中时用 setIfAbsent 抢一把带租约的锁，
     * 抢不到说明已有线程在生成，立刻返回兜底文案，绝不排队等待。
     */
    @Override
    public String generateSummary(boolean isAdmin, boolean refresh) {
        String cacheKey = isAdmin ? SUMMARY_CACHE_ADMIN : SUMMARY_CACHE_GUEST;
        String lockKey = cacheKey + ":lock";

        /* refresh 表示身份刚切换，必须跳过上一份身份的说辞 */
        if (!refresh) {
            String cached = readCachedSummary(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        }

        String token = UUID.randomUUID().toString();
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, SUMMARY_LOCK_TTL))) {
                /* 已经有人在生成：不排队、不等待，直接给占位文案 */
                return getFallbackMessage(isAdmin);
            }
        } catch (Exception e) {
            log.warn("看板助手摘要锁不可用，原因类型：{}", e.getClass().getSimpleName());
            return getFallbackMessage(isAdmin);
        }

        try {
            /* 双检：抢锁的间隙里，别的线程 / 实例可能已经把说辞写进缓存了 */
            if (!refresh) {
                String cached = readCachedSummary(cacheKey);
                if (StringUtils.hasText(cached)) {
                    return cached;
                }
            }
            String summary = composeSummary(requestMuseSummary(isAdmin));
            if (!StringUtils.hasText(summary)) {
                return getFallbackMessage(isAdmin);
            }
            writeCachedSummary(cacheKey, summary);
            return summary;
        } finally {
            releaseLock(lockKey, token);
        }
    }

    /**
     * 调 DeepSeek 生成一句看板助手说辞。
     * 任何异常（含超时）都收敛成空串，由调用方决定兜底，绝不把异常抛给看板。
     */
    private String requestMuseSummary(boolean isAdmin) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("DeepSeek API Key 尚未配置，看板助手改用兜底文案");
            return "";
        }
        try {
            Long blogCount = blogPostMapper.selectCount(null);
            Long commentCount = blogCommentMapper.selectCount(null);

            String userPrompt;
            if (isAdmin) {
                QueryWrapper<BlogPost> blogWrapper = new QueryWrapper<>();
                blogWrapper.orderByDesc("create_time").last("LIMIT 2");
                List<BlogPost> recentBlogs = blogPostMapper.selectList(blogWrapper);
                String recentBlogsText = recentBlogs.isEmpty() ? "暂无随笔" :
                        recentBlogs.stream().map(BlogPost::getContent).collect(Collectors.joining(" | "));

                QueryWrapper<BlogComment> commentWrapper = new QueryWrapper<>();
                commentWrapper.orderByDesc("create_time").last("LIMIT 2");
                List<BlogComment> recentComments = blogCommentMapper.selectList(commentWrapper);
                String recentCommentsText = recentComments.isEmpty() ? "暂无留言" :
                        recentComments.stream().map(BlogComment::getContent).collect(Collectors.joining(" | "));

                userPrompt = String.format("""
                    对话对象：站长本人。
                    网站当前数据（请严格区分概念）：
                    - 博客：%d 篇（记录技术与生活的图文心得）
                    - 评论：%d 条（来自访客的留言与打分）
                    站长近期随笔：【%s】
                    近期访客留言：【%s】

                    请以贴身女仆般的口吻，递上一句温柔而坚定的鼓励，称呼他为“站长”，50 字以内；可以自然地带一句你的自我认知。
                    """, blogCount, commentCount, recentBlogsText, recentCommentsText);
            } else {
                int randomTopic = new Random().nextInt(4);
                String topicInstruction = "";
                if (randomTopic == 0) {
                    topicInstruction = "重点向访客推荐博客列表，可以顺便提一句：站长写下的随笔记录着他的心路历程。";
                } else if (randomTopic == 1) {
                    topicInstruction = "重点向访客推荐留言板，温柔地邀请大家去那里给站长留言、打分和提建议。";
                } else if (randomTopic == 2) {
                    topicInstruction = "重点是对屏幕前的访客表达温柔和关心：问候他最近睡得好不好、提醒他注意身体，给足情绪价值。";
                } else {
                    topicInstruction = "直接从你的【自我认知】或【站长的点滴】中取一条，像朋友一样单向分享给访客。";
                }

                userPrompt = String.format("""
                    对话对象：一位刚打开网站的普通访客。
                    网站当前数据（请严格区分概念）：
                    - 博客：%d 篇
                    - 评论：%d 条

                    请以看板助手的身份，用一句贴合女仆口吻的话迎接他，50 字以内：
                    1. 以“欢迎来到小站”的语气，迎接初次到访的客人。
                    2. 本次这句话的任务：%s
                    """, blogCount, commentCount, topicInstruction);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", SUMMARY_MODEL);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", baseSystemPrompt),
                    Map.of("role", "user", "content", userPrompt)));
            requestBody.put("temperature", 0.85);
            /* deepseek-flash 会先输出 reasoning，预算给少了正文会被截空 */
            requestBody.put("max_tokens", 1200);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = deepseekHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("choices").isArray() && !root.path("choices").isEmpty()) {
                return root.path("choices").path(0).path("message").path("content").asText("");
            }
            log.warn("看板助手返回异常，body={}", response.body());
            return "";
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("看板助手 AI 分析异常", e);
            return "";
        }
    }

    /* 清洗模型输出：去掉括号里的旁白，太短视为异常，交给兜底文案 */
    private String composeSummary(String aiText) {
        if (!StringUtils.hasText(aiText)) {
            return "";
        }
        String cleaned = aiText.replaceAll("(?i)[\\(（【\\[\\*].*?[\\)）】\\]\\*]", "").trim();
        return cleaned.length() < 5 ? "" : cleaned;
    }

    private String readCachedSummary(String cacheKey) {
        try {
            return stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("读取看板助手缓存失败，原因类型：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void writeCachedSummary(String cacheKey, String summary) {
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, summary, SUMMARY_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入看板助手缓存失败，原因类型：{}", e.getClass().getSimpleName());
        }
    }

    /* 通用解锁：只删自己那把锁，租约过期换了主人也不会误删 */
    private void releaseLock(String lockKey, String token) {
        try {
            stringRedisTemplate.execute(LOCK_RELEASE, List.of(lockKey), token);
        } catch (Exception e) {
            log.warn("锁释放失败，等待租约自动过期，key={}", lockKey);
        }
    }

    private String getFallbackMessage(boolean isAdmin) {
        if (isAdmin) {
            return "站长，数据接口好像睡着了一会儿呢。不过没关系，您写下的每一篇我都记着。请继续加油，我会一直在这里的。";
        } else {
            return "欢迎来到小站，我是这里的看板助手。站长还在慢慢写作，请不要笑话他。博客已经摆好了，请慢慢看哦。";
        }
    }
}
