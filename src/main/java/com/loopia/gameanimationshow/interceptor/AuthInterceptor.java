package com.loopia.gameanimationshow.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.loopia.gameanimationshow.common.JwtUtil;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 身份认证拦截器：校验 Token，并把 userId / role 放进 UserContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /* 预检请求不带 Token，放行，否则跨域请求全被拦住 */
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        /* 兼容 Authorization: Bearer xxx 与裸 token 两种写法 */
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("token");
        } else if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!StringUtils.hasText(token)) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, MessageConstant.Auth.NOT_LOGIN);
        }

        /* 过期或篡改会抛 JWTVerificationException，交给全局异常处理器转成 401 */
        DecodedJWT jwt = jwtUtil.verifyToken(token);
        UserContext.setCurrentId(jwt.getClaim("userId").asLong());
        UserContext.setRole(jwt.getClaim("role").asInt());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        /* 容器线程会复用，请求结束必须清理 */
        UserContext.remove();
    }
}