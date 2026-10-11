package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.entity.SysUser;
import com.loopia.gameanimationshow.model.vo.LoginVO;
import com.loopia.gameanimationshow.model.vo.RecentUserVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 11:59 
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 初始化游客设备并颁发 Token
     *
     * @param uuid 设备唯一标识
     * @return 登录视图对象 (包含Token和用户信息)
     */
    LoginVO initVisitor(String uuid);
    /**
     * 修改用户昵称
     *
     * @param userId   用户ID
     * @param nickname 新昵称
     */
    void updateNickname(Long userId, String nickname);
    /**
     * 管理员暗号登录/提权
     *
     * @param password 提权暗号
     * @return 登录视图对象 (包含拥有管理员权限的全新Token)
     */
    LoginVO adminLogin(String password);

    /**
     * 站长上传并更新自己的头像
     *
     * @param file 头像文件 (不超过 1MB 的图片)
     * @return 新头像的可访问 URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 获取站长当前头像 (公开接口，供首页展示)
     *
     * @return 站长头像 URL，站长未设置时返回 null
     */
    String getOwnerAvatar();

    List<RecentUserVO> recent();
}
