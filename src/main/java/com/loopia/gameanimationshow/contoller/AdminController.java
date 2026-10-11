package com.loopia.gameanimationshow.contoller;

/**
 * AdminController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4
 * @created 16:34
 */

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.model.dto.AdminLoginDTO;
import com.loopia.gameanimationshow.model.vo.LoginVO;
import com.loopia.gameanimationshow.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysUserService sysUserService;

    /* 简易防爆破：按 IP 记录失败次数与封禁开始时间 */
    private static final ConcurrentHashMap<String, Integer> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LOCK_TIME = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 900_000L;
    private static final String LOCKED_MESSAGE = "密码错误次数过多，您的 IP 已被锁定 15 分钟";

    /**
     * 管理员登录验证接口
     */
    @PostMapping("/login")
    public Result<?> adminLogin(
            @Validated @RequestBody AdminLoginDTO loginDTO,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        Long lockedAt = LOCK_TIME.get(ip);
        if (lockedAt != null) {
            if (now - lockedAt < LOCK_DURATION_MS) {
                return Result.error(LOCKED_MESSAGE);
            }
            LOCK_TIME.remove(ip);
            LOGIN_ATTEMPTS.remove(ip);
        }

        try {
            LoginVO loginVO = sysUserService.adminLogin(loginDTO.getPassword());
            /* 登录成功即清空该 IP 的失败记录 */
            LOGIN_ATTEMPTS.remove(ip);
            return Result.success(loginVO, MessageConstant.Common.LOGIN_SUCCESS);
        } catch (Exception e) {
            int attempts = LOGIN_ATTEMPTS.getOrDefault(ip, 0) + 1;
            LOGIN_ATTEMPTS.put(ip, attempts);
            if (attempts >= MAX_ATTEMPTS) {
                LOCK_TIME.put(ip, now);
                return Result.error(LOCKED_MESSAGE);
            }
            throw e;
        }
    }
}
