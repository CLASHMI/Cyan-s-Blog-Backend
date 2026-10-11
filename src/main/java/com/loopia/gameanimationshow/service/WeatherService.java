package com.loopia.gameanimationshow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.model.vo.WeatherVO;
import com.loopia.gameanimationshow.model.vo.WeatherCityVO;
import com.loopia.gameanimationshow.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${loopia.weather.api-key:}")
    private String apiKey;
    @Value("${loopia.weather.api-host:}")
    private String apiHost;
    @Value("${loopia.weather.location:}")
    private String location;
    @Value("${loopia.weather.city:}")
    private String city;

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private static final String SELECTED_CITY = "weather:city:v1";

    public WeatherVO current() {
        try {
            String selected = redis.opsForValue().get(SELECTED_CITY);
            WeatherCityVO target = selected == null ? null : mapper.readValue(selected, WeatherCityVO.class);
            return currentAt(target == null ? location : target.id(), target == null ? city : target.name());
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException(503, "天气暂不可用"); }
    }

    /** 仅管理员可搜索；缓存命中不消耗上游额度，未命中全站三秒最多一次。 */
    public List<WeatherCityVO> cities(String keyword) {
        UserContext.requireAdmin();
        String query = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        if (!query.matches("[\\p{L}\\p{N} ·.-]{2,40}"))
            throw new ServiceException(400, "请输入 2–40 字的城市名称或 Location ID");
        String key = "weather:search:v1:" + apiHost + ":" + query;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, mapper.getTypeFactory().constructCollectionType(List.class, WeatherCityVO.class));
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("weather:search:rate", "1", Duration.ofSeconds(3))))
                throw new ServiceException(429, "搜索太快了，请稍等三秒");
            String token = UUID.randomUUID().toString();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lock", token, Duration.ofSeconds(60))))
                throw new ServiceException(429, "城市搜索正在更新，请稍后再试");
            validateHost();
            JsonNode result = fetchPath("/geo/v2/city/lookup?location=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&lang=zh&number=10", true);
            List<WeatherCityVO> cities = new ArrayList<>();
            for (JsonNode item : result.path("location")) {
                if (cities.size() >= 10) break;
                WeatherCityVO candidate = new WeatherCityVO(required(item, "id"), required(item, "name"),
                        item.path("adm1").asText(""), item.path("adm2").asText(""), item.path("country").asText(""));
                cities.add(candidate);
                // 选城只能引用已验证过的候选项。比搜索缓存多保留一天，避免边界过期。
                redis.opsForValue().set("weather:candidate:v1:" + apiHost + ":" + candidate.id(), mapper.writeValueAsString(candidate), Duration.ofDays(2));
            }
            redis.opsForValue().set(key, mapper.writeValueAsString(cities), Duration.ofDays(1));
            unlock(key, token);
            return cities;
        } catch (ServiceException e) { throw e; }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ServiceException(503, "城市搜索暂不可用");
        }
    }

    public WeatherVO selectCity(String id) {
        UserContext.requireAdmin();
        if (id == null || !id.matches("[a-zA-Z0-9]{1,32}")) throw new ServiceException(400, "请选择搜索结果中的城市");
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            String candidate = redis.opsForValue().get("weather:candidate:v1:" + apiHost + ":" + id);
            if (candidate == null) throw new ServiceException(400, "城市候选已过期，请重新搜索");
            WeatherCityVO target = mapper.readValue(candidate, WeatherCityVO.class);
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(SELECTED_CITY + ":lock", token, Duration.ofSeconds(60)));
            if (!locked) throw new ServiceException(429, "正在切换城市，请稍后再试");
            WeatherVO result = currentAt(target.id(), target.name());
            // 先确保新城市天气可用；失败时保留原来的站点城市。
            redis.opsForValue().set(SELECTED_CITY, candidate);
            return result;
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException(503, "切换城市失败，请稍后再试"); }
        finally {
            if (locked) {
                try { unlock(SELECTED_CITY, token); }
                catch (Exception e) { log.warn("城市选择锁等待租约自动释放"); }
            }
        }
    }

    private void validateHost() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiHost)
                || !("devapi.qweather.com".equalsIgnoreCase(apiHost)
                || apiHost.matches("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+qweatherapi\\.com")))
            throw new ServiceException(503, "天气尚未配置");
    }

    private WeatherVO currentAt(String location, String city) {
        validateHost();
        if (!StringUtils.hasText(location) || !StringUtils.hasText(city))
            throw new ServiceException(503, "天气尚未配置");
        String key = "weather:current:v1:" + apiHost + ":" + location + ":" + city;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, WeatherVO.class);
            String token = UUID.randomUUID().toString();
            // 60 秒租约覆盖两次各 10 秒的上游请求；失败保留租约，避免失败风暴。
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lock", token, Duration.ofSeconds(60)))) {
                cached = redis.opsForValue().get(key);
                if (cached != null) return mapper.readValue(cached, WeatherVO.class);
                throw new ServiceException(503, "天气正在更新，请稍后再试");
            }
            // 拿锁后再查一次，避免前一个实例刚刚填好缓存。
            cached = redis.opsForValue().get(key);
            if (cached != null) {
                unlock(key, token);
                return mapper.readValue(cached, WeatherVO.class);
            }
            JsonNode current = fetch("now", location);
            JsonNode forecast = fetch("3d", location);
            JsonNode now = current.path("now");
            List<WeatherVO.Forecast> days = new ArrayList<>();
            for (JsonNode day : forecast.path("daily")) {
                if (days.size() == 3) break;
                days.add(new WeatherVO.Forecast(required(day, "fxDate"), required(day, "tempMin"),
                        required(day, "tempMax"), required(day, "iconDay"), required(day, "textDay")));
            }
            if (days.size() != 3) throw new ServiceException(503, "天气预报暂不可用");
            WeatherVO result = new WeatherVO(city, required(now, "temp"), required(now, "windDir"),
                    required(now, "icon"), required(now, "text"), required(now, "obsTime"),
                    current.path("fxLink").asText(""), days);
            redis.opsForValue().set(key, mapper.writeValueAsString(result), Duration.ofHours(1));
            unlock(key, token);
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("天气代理不可用，原因类型：{}", e.getClass().getSimpleName());
            throw new ServiceException(503, "天气暂不可用");
        }
    }

    private void unlock(String key, String token) {
        redis.execute(UNLOCK, List.of(key + ":lock"), token);
    }

    private JsonNode fetch(String endpoint, String location) throws Exception {
        return fetchPath("/v7/weather/" + endpoint + "?location="
                + URLEncoder.encode(location, StandardCharsets.UTF_8) + "&lang=zh&unit=m", false);
    }

    private JsonNode fetchPath(String path, boolean lookup) throws Exception {
        URI uri = URI.create("https://" + apiHost + path);
        HttpRequest request = HttpRequest.newBuilder(uri).header("X-QW-Api-Key", apiKey)
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        try (var body = new java.io.ByteArrayInputStream(response.body())) {
            if (response.statusCode() != 200) {
                // 只记录状态与接口类型，不记录请求头、Key 或完整供应商响应。
                log.warn("天气上游请求失败：endpoint={}, HTTP={}", lookup ? "city/lookup" : "weather", response.statusCode());
                throw new ServiceException(503, "天气上游返回 HTTP " + response.statusCode());
            }
            // 和风响应可能使用 gzip 压缩。
            var decoded = "gzip".equalsIgnoreCase(response.headers().firstValue("Content-Encoding").orElse(""))
                    ? new java.util.zip.GZIPInputStream(body) : body;
            JsonNode root = mapper.readTree(decoded);
            if (!("200".equals(root.path("code").asText()) || (lookup && "404".equals(root.path("code").asText())))) throw new ServiceException(503, "天气服务暂不可用");
            return root;
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (!StringUtils.hasText(value)) throw new ServiceException(503, "天气数据暂不完整");
        return value;
    }
}
