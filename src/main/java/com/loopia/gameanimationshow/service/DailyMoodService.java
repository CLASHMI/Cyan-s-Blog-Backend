package com.loopia.gameanimationshow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.model.vo.DailyMoodVO;
import com.loopia.gameanimationshow.model.vo.WeatherVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyMoodService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final WeatherService weatherService;
    private final AiService aiService;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    // 固定选项，不允许任意提示词或任意缓存键；每天最多六次生成尝试。
    public enum Mood {
        HAPPY("开心", "😊", "今天的小快乐，值得好好收藏。"),
        CALM("平静", "😌", "不急不忙，按自己的节奏来。"),
        FOCUSED("专注", "🤓", "把注意力留给眼前这一件小事。"),
        TIRED("疲惫", "😴", "今天慢一点，也没有关系。"),
        LOW("低落", "🥺", "允许自己安静一会儿。"),
        EXCITED("雀跃", "🥳", "有一点期待，日子就亮了起来。" );
        final String label;
        final String emoji;
        final String fallback;
        Mood(String label, String emoji, String fallback) {
            this.label = label; this.emoji = emoji; this.fallback = fallback;
        }
    }

    /** 访客读取只查 Redis，绝不调用天气或 AI。 */
    public DailyMoodVO today() {
        String date = ZonedDateTime.now(clock).toLocalDate().toString();
        try {
            String cached = redis.opsForValue().get("mood:today:v1:" + date);
            return cached == null ? new DailyMoodVO(date, null, null, null, "今天还没有记录心情", false, null)
                    : mapper.readValue(cached, DailyMoodVO.class);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    public DailyMoodVO select(String code) {
        UserContext.requireAdmin();
        Mood mood;
        try { mood = Mood.valueOf(code == null ? "" : code); }
        catch (IllegalArgumentException e) { throw new ServiceException(400, "请选择有效的心情"); }
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        String date = now.toLocalDate().toString();
        String key = "mood:today:v1:" + date;
        String variant = "ai:mood:v2:" + date + ":" + mood.name();
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            // 给天气两次请求与 AI 请求预留时间，不发起跨日生成。
            if (Duration.between(now, midnight).getSeconds() <= 60)
                throw new ServiceException(503, "即将换日，请稍后记录心情");
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lock", token, Duration.ofSeconds(90)));
            if (!locked) throw new ServiceException(429, "心情正在记录，请稍后查看");
            String cached = redis.opsForValue().get(variant);
            DailyMoodVO result;
            if (cached != null) {
                result = mapper.readValue(cached, DailyMoodVO.class);
            } else {
                // 失败/重启也不删除调用标记；Redis 故障时关闭生成而非绕过限额。
                if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(variant + ":attempt", "1", Duration.ofDays(2))))
                    throw new ServiceException(503, "这个心情今日已尝试生成，请稍后查看或选择其他心情");
                String weather = null;
                try {
                    WeatherVO current = weatherService.current();
                    weather = moodAtmosphere(current.icon());
                } catch (ServiceException e) {
                    log.info("今日心情使用无天气上下文：天气暂不可用");
                }
                String text = mood.fallback;
                boolean generated = false;
                try {
                    text = aiService.generateMoodText(mood.label, weather);
                    generated = true;
                } catch (ServiceException e) {
                    log.info("今日心情使用默认短句：AI 暂不可用，不重复尝试");
                }
                result = new DailyMoodVO(date, mood.name(), mood.label, mood.emoji, text, generated, weather);
                redis.opsForValue().set(variant, mapper.writeValueAsString(result), Duration.ofDays(2));
            }
            Duration ttl = Duration.between(ZonedDateTime.now(clock), midnight);
            if (ttl.isNegative() || ttl.isZero()) throw new ServiceException(503, "日期已变化，请重新选择今日心情");
            redis.opsForValue().set(key, mapper.writeValueAsString(result), ttl);
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable(e);
        } finally {
            if (locked) {
                try { redis.execute(UNLOCK, List.of(key + ":lock"), token); }
                catch (Exception e) { log.warn("今日心情锁等待租约自动释放"); }
            }
        }
    }

    /** 天气只转成写作氛围；不把地区、温度、风向或天气播报送入模型。 */
    static String moodAtmosphere(String icon) {
        int code;
        try { code = Integer.parseInt(icon); } catch (Exception e) { return "平常日子里的细微感受"; }
        if (code >= 500 && code < 600) return "朦胧、放慢脚步、给自己留白";
        if (code >= 400 && code < 500) return "安静、柔软、珍惜身边的暖意";
        if (code >= 300 && code < 400) return "向内倾听、温柔安放思绪";
        if (code == 150) return "沉静、收拢思绪、与自己相处";
        if (code == 100) return "明亮、舒展、对小事抱有期待";
        return "从容、松弛、接纳自己的节奏";
    }

    private ServiceException unavailable(Exception e) {
        log.warn("今日心情暂不可用，原因类型：{}", e.getClass().getSimpleName());
        return new ServiceException(503, "今日心情暂不可用");
    }
}
