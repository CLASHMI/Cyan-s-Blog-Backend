package com.loopia.gameanimationshow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管理员登录防爆破：失败计数与封禁标记都放 Redis，
 * 多实例共享同一份计数，进程重启也不会丢。
 */
@Service
@RequiredArgsConstructor
public class LoginThrottle {

    /** 达到该失败次数即封禁 */
    private static final int MAX_ATTEMPTS = 5;
    /** 计数窗口与封禁时长一致：封禁到期后计数也随之失效 */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    public static final String LOCKED_MESSAGE = "密码错误次数过多，您的 IP 已被锁定 15 分钟";

    private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 该 IP 当前是否处于封禁期
     */
    public boolean isLocked(String ip) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(ip)));
    }

    /**
     * 登录成功后清空该 IP 的失败计数
     */
    public void clearFailures(String ip) {
        stringRedisTemplate.delete(attemptKey(ip));
    }

    /**
     * 记录一次登录失败
     *
     * @return 本次失败是否触发了封禁
     */
    public boolean recordFailure(String ip) {
        String attemptKey = attemptKey(ip);
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
        if (attempts == null) {
            return false;
        }
        if (attempts == 1L) {
            stringRedisTemplate.expire(attemptKey, WINDOW);
        }
        if (attempts >= MAX_ATTEMPTS) {
            stringRedisTemplate.opsForValue().set(lockKey(ip), "1", WINDOW);
            return true;
        }
        return false;
    }

    private String attemptKey(String ip) {
        return ATTEMPT_KEY_PREFIX + ip;
    }

    private String lockKey(String ip) {
        return LOCK_KEY_PREFIX + ip;
    }
}
