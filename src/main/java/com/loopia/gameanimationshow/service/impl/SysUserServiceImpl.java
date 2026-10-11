package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.ClientIpUtil;
import com.loopia.gameanimationshow.common.JwtUtil;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.constant.SystemConstant;
import com.loopia.gameanimationshow.entity.SysUser;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.SysUserMapper;
import com.loopia.gameanimationshow.model.vo.LoginVO;
import com.loopia.gameanimationshow.model.vo.RecentUserVO;
import java.util.List;
import com.loopia.gameanimationshow.service.AiService;
import com.loopia.gameanimationshow.service.SysUserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * SysUserServiceImpl 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 12:00 
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    @Override
    public List<RecentUserVO> recent() {
        return baseMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getNickname, SysUser::getAvatar, SysUser::getRole, SysUser::getCreateTime)
                .orderByDesc(SysUser::getCreateTime).orderByDesc(SysUser::getId)
                .last("LIMIT 25"))
                .stream().map(user -> new RecentUserVO(
                        user.getId(), user.getNickname(), user.getAvatar(), user.getRole(), user.getCreateTime())).toList();
    }

    /* 站长头像大小上限：1MB */
    private static final long AVATAR_MAX_SIZE = 1024 * 1024L;

    @Value("${loopia.admin.secret}")
    private String adminSecret;
    private final JwtUtil jwtUtil;
    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiService aiService;

    /* 昵称改名每日上限 */
    private static final int NICKNAME_DAILY_LIMIT = 2;
    /* 注册接口兜底：同一 IP 每日最多创建的新游客账号数 */
    private static final int REGISTER_IP_DAILY_LIMIT = 15;

    /* 限流“每日”口径统一使用上海时区 */
    private final Clock dailyClock = Clock.system(ZoneId.of("Asia/Shanghai"));
    @Value("${loopia.tencent.cos.domain-url}")
    private String domainUrl;
    @Value("${loopia.tencent.cos.secret-id}")
    private String cosSecretId;
    @Value("${loopia.tencent.cos.secret-key}")
    private String cosSecretKey;
    @Value("${loopia.tencent.cos.region}")
    private String cosRegion;
    @Value("${loopia.tencent.cos.bucket-name}")
    private String cosBucket;

    /* 读取当天的计数：key 不存在或值异常时按 0 处理，避免脏值把限流判成 500 */
    private long readDailyCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 初始化用户
     * @param uuid 设备唯一标识
     * @return
     */
    @Override
    public LoginVO initVisitor(String uuid) {
        SysUser user = null;
        if (StringUtils.hasText(uuid)) {
            user = this.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUuid, uuid)
                    .eq(SysUser::getRole, SystemConstant.Role.VISITOR));
        }
        if (user == null) {
            // 注册接口兜底：同一 IP 每天最多创建 15 个新游客账号，防止自动发号被恶意刷取
            String clientIp = ClientIpUtil.currentIp();
            if (clientIp != null) {
                String ipKey = "register:ip:" + clientIp + ":" + LocalDate.now(dailyClock);
                /* 兜底修复：键若因人工 SET 或异常中断丢了 TTL，会变成永久键，
                   该 IP 跨天也不会恢复，这里先补上过期时间。 */
                Long ipTtl = stringRedisTemplate.getExpire(ipKey);
                if (ipTtl != null && ipTtl == -1L) {
                    stringRedisTemplate.expire(ipKey, Duration.ofHours(24));
                }
                /* 先判阈值再自增：额度已满时直接拦截，不再对计数器做自增，
                   否则被拒请求会持续把计数推高（曾出现上限 15 却涨到 17 的脏写）。 */
                if (readDailyCounter(ipKey) >= REGISTER_IP_DAILY_LIMIT) {
                    throw new ServiceException(429, "当前网络今日创建的游客账号数已达上限");
                }
                Long ipCount = stringRedisTemplate.opsForValue().increment(ipKey);
                if (ipCount != null && ipCount == 1L) {
                    stringRedisTemplate.expire(ipKey, Duration.ofHours(24));
                }
            }
            user = new SysUser();
            String freshUuid = UUID.randomUUID().toString();
            user.setUuid(freshUuid);
            user.setNickname("访客_" + freshUuid.substring(0, 4));
            int randomAvatarId = new java.util.Random().nextInt(10) + 1;
            user.setAvatar(domainUrl + "/avatar/" + randomAvatarId + ".png");
            user.setRole(SystemConstant.Role.VISITOR);
            user.setCreateTime(LocalDateTime.now());
            this.save(user);
            /* 新增游客会改“累计游客”，只清统计缓存，不动随笔列表 */
            org.springframework.cache.Cache cache = cacheManager.getCache("statsOverview");
            if (cache != null) {
                cache.clear();
            }
        }
        String token = jwtUtil.createToken(user.getId(), user.getRole());
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserInfo(user);
        return loginVO;
    }
    /**
     * 修改用户昵称
     * @param userId   用户ID
     * @param nickname 新昵称
     */
    @Override
    public void updateNickname(Long userId, String nickname) {
        String finalNickname = nickname != null ? nickname.trim() : "";

        if (!StringUtils.hasText(finalNickname)) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.User.NICKNAME_CANNOT_BE_EMPTY);
        }
        if (finalNickname.length() > 10) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.User.NICKNAME_TOO_LONG);
        }

        // 先限流：每天最多允许改名 2 次
        String dailyKey = "nickname:daily:" + userId + ":" + LocalDate.now(dailyClock);
        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(dailyKey, Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > NICKNAME_DAILY_LIMIT) {
            throw new ServiceException(429, "今日修改昵称次数已达上限");
        }

        // 后审核：AI 文本审核
        if (!aiService.moderateText(finalNickname)) {
            throw new ServiceException("昵称包含违规词汇，请更换");
        }

        SysUser sysUser = new SysUser();
        sysUser.setId(userId);
        sysUser.setNickname(finalNickname);
        this.updateById(sysUser);
    }

    /**
     * 管理员登录
     * @param password 提权暗号
     * @return
     */
    @Override
    public LoginVO adminLogin(String password) {
        if (!adminSecret.equals(password)) {
            throw new ServiceException(MessageConstant.User.SECRET_ERROR);
        }
        SysUser adminUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, SystemConstant.Role.ADMIN)
                .last("LIMIT 1"));
        if (adminUser == null) {
            /* 首次提权：库中还没有站长记录时直接建一个站长账号。
               这里刻意不再依赖“当前访客会话”——/admin/login 已在鉴权白名单中，
               即使该 IP 当日发号额度已满、前端拿不到访客 token，站长也必须能登录。 */
            adminUser = new SysUser();
            adminUser.setUuid("ADMIN_GOD_MODE_" + UUID.randomUUID().toString().substring(0, 8));
            adminUser.setNickname("站长");
            adminUser.setAvatar(domainUrl + "/avatar/" + (new java.util.Random().nextInt(10) + 1) + ".png");
            adminUser.setRole(SystemConstant.Role.ADMIN);
            adminUser.setCreateTime(LocalDateTime.now());
            this.save(adminUser);
        }
        String token = jwtUtil.createToken(adminUser.getId(), adminUser.getRole());
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUserInfo(adminUser);

        return loginVO;
    }

    /**
     * 站长上传头像：仅站长可调用，图片不超过 1MB，成功后写入自己的头像字段
     *
     * @param file 头像文件
     * @return 新头像 URL
     */
    @Override
    @CacheEvict(value = "commentList", allEntries = true)
    public String uploadAvatar(MultipartFile file) {
        UserContext.requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.User.AVATAR_CANNOT_BE_EMPTY);
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.User.AVATAR_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.User.AVATAR_TYPE_INVALID);
        }
        Long userId = UserContext.getCurrentId();
        String avatarUrl = uploadToCos(file);
        SysUser sysUser = new SysUser();
        sysUser.setId(userId);
        sysUser.setAvatar(avatarUrl);
        this.updateById(sysUser);
        return avatarUrl;
    }

    /**
     * 获取站长当前头像：每次读取数据库，保证首页展示的是"此刻"的头像
     *
     * @return 站长头像 URL，站长不存在或未设置时返回 null
     */
    @Override
    public String getOwnerAvatar() {
        SysUser adminUser = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, SystemConstant.Role.ADMIN)
                .select(SysUser::getAvatar)
                .last("LIMIT 1"));
        return adminUser == null ? null : adminUser.getAvatar();
    }

    /**
     * 上传文件到腾讯云 COS，文件名走 UUID 保证每次更换头像都是新地址，不被浏览器缓存
     */
    private String uploadToCos(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }
            String newFileName = "avatar/" + UUID.randomUUID().toString().replace("-", "") + extension;
            COSCredentials cred = new BasicCOSCredentials(cosSecretId, cosSecretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(cosRegion));
            clientConfig.setHttpProtocol(HttpProtocol.https);
            COSClient cosClient = new COSClient(cred, clientConfig);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(file.getSize());
            objectMetadata.setContentType(file.getContentType());
            objectMetadata.setHeader("Cache-Control", "max-age=2592000");
            objectMetadata.setHeader("Content-Disposition", "inline");
            PutObjectRequest putObjectRequest = new PutObjectRequest(cosBucket, newFileName, file.getInputStream(), objectMetadata);
            cosClient.putObject(putObjectRequest);
            cosClient.shutdown();
            return domainUrl + "/" + newFileName;
        } catch (Exception e) {
            log.error("头像上传失败", e);
            throw new ServiceException(MessageConstant.Blog.UPLOAD_FAILED);
        }
    }
}

