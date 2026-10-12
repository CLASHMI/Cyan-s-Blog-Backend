package com.loopia.gameanimationshow.service;

import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 腾讯云 COS 上传：客户端构造与桶/域名配置只在这里出现一次，
 * 业务侧只负责决定对象键（key），不再各自 new COSClient、各自读一遍配置。
 */
@Slf4j
@Service
public class CosStorageService {

    /* 对象缓存 30 天：换图一律走新的 UUID 键，旧地址自然失效，不需要依赖 CDN 刷新 */
    private static final String CACHE_CONTROL = "max-age=2592000";

    @Value("${loopia.tencent.cos.secret-id}")
    private String secretId;

    @Value("${loopia.tencent.cos.secret-key}")
    private String secretKey;

    @Value("${loopia.tencent.cos.region}")
    private String region;

    @Value("${loopia.tencent.cos.bucket-name}")
    private String bucketName;

    @Value("${loopia.tencent.cos.domain-url}")
    private String domainUrl;

    /**
     * 上传一个对象并返回可访问地址。
     *
     * @param key         对象键，含目录前缀，例如 "avatar/xxx.png"
     * @param content     数据流
     * @param size        字节数
     * @param contentType MIME 类型
     * @return 完整访问地址（domain-url + "/" + key）
     */
    public String urlOf(String key) {
        return domainUrl + "/" + key;
    }

    public String upload(String key, InputStream content, long size, String contentType) {
        COSClient cosClient = null;
        try {
            COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            clientConfig.setHttpProtocol(HttpProtocol.https);
            cosClient = new COSClient(credentials, clientConfig);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);
            metadata.setHeader("Cache-Control", CACHE_CONTROL);
            metadata.setHeader("Content-Disposition", "inline");

            cosClient.putObject(new PutObjectRequest(bucketName, key, content, metadata));
            return urlOf(key);
        } catch (Exception e) {
            log.error("COS 上传失败，key={}", key, e);
            throw new ServiceException(MessageConstant.Blog.UPLOAD_FAILED);
        } finally {
            /* 每次上传用一个短连接客户端，成功失败都立刻释放 */
            if (cosClient != null) {
                cosClient.shutdown();
            }
        }
    }
}