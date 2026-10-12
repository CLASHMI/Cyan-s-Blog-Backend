package com.loopia.gameanimationshow.controller;

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.model.vo.LoginVO;
import com.loopia.gameanimationshow.model.vo.RecentUserVO;
import java.util.List;
import com.loopia.gameanimationshow.service.SysUserService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * SysUserController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 15:32 
 */
@RequestMapping("/user")
@RestController
@RequiredArgsConstructor
public class SysUserController {
    private final SysUserService sysUserService;

    @GetMapping("/recent")
    public Result<List<RecentUserVO>> recent() {
        return Result.success(sysUserService.recent());
    }

    /**
     * 自动初始化/登录接口
     * @param uuid
     * @return
     */
    @PostMapping("/init")
    public Result<LoginVO> initVisitor(@RequestParam(required = false) String uuid) {
        LoginVO loginVO = sysUserService.initVisitor(uuid);
        return Result.success(loginVO, MessageConstant.Common.OPERATION_SUCCESS);
    }


    /**
     * 修改昵称接口
     * @param nickname
     * @return
     */
    @PutMapping("/updateNickname")
    public Result<?> updateNickname(
            @NotBlank(message = MessageConstant.User.NICKNAME_CANNOT_BE_EMPTY)
            @RequestParam String nickname) {
        Long userId = UserContext.getCurrentId();
        sysUserService.updateNickname(userId, nickname);
        return Result.success(MessageConstant.Common.UPDATE_SUCCESS);
    }

    /**
     * 站长上传头像（仅站长，图片不超过 1MB）
     *
     * @param file 头像文件
     * @return 新头像 URL
     */
    @PostMapping("/uploadAvatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(sysUserService.uploadAvatar(file), MessageConstant.User.AVATAR_UPDATE_SUCCESS);
    }

    /**
     * 获取站长当前头像（公开接口，供首页展示）
     *
     * @return 站长头像 URL
     */
    @GetMapping("/ownerAvatar")
    public Result<String> ownerAvatar() {
        return Result.success(sysUserService.getOwnerAvatar(), MessageConstant.Common.QUERY_SUCCESS);
    }

}
