package com.loopia.gameanimationshow.controller;

/**
 * AdminController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4
 * @created 16:34
 */

import com.loopia.gameanimationshow.common.ClientIpUtil;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.model.dto.AdminLoginDTO;
import com.loopia.gameanimationshow.model.vo.LoginVO;
import com.loopia.gameanimationshow.service.LoginThrottle;
import com.loopia.gameanimationshow.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysUserService sysUserService;
    private final LoginThrottle loginThrottle;

    /**
     * 管理员登录验证接口
     */
    @PostMapping("/login")
    public Result<?> adminLogin(@Validated @RequestBody AdminLoginDTO loginDTO) {
        String ip = ClientIpUtil.currentIp();
        if (loginThrottle.isLocked(ip)) {
            return Result.error(LoginThrottle.LOCKED_MESSAGE);
        }

        try {
            LoginVO loginVO = sysUserService.adminLogin(loginDTO.getPassword());
            /* 登录成功即清空该 IP 的失败记录 */
            loginThrottle.clearFailures(ip);
            return Result.success(loginVO, MessageConstant.Common.LOGIN_SUCCESS);
        } catch (Exception e) {
            if (loginThrottle.recordFailure(ip)) {
                return Result.error(LoginThrottle.LOCKED_MESSAGE);
            }
            throw e;
        }
    }
}
