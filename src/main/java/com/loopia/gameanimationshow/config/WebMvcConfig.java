package com.loopia.gameanimationshow.config;

import com.loopia.gameanimationshow.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * WebMvcConfig 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 21:43 
 */
@Configuration
@ConfigurationProperties(prefix = "loopia.auth")
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    private List<String> whiteList = new ArrayList<>();

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 拦截所有进站请求
                .addPathPatterns("/**")
                // 排除白名单请求
                .excludePathPatterns(whiteList);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到本地项目根目录下的 uploads 文件夹，使上传的图片/视频能够被浏览器访问
        String path = "file:" + System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(path);
    }
}
