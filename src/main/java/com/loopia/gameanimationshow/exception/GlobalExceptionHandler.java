package com.loopia.gameanimationshow.exception;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleServiceException(ServiceException e) {
        log.info("业务逻辑拦截: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handle400(MethodArgumentNotValidException e) {
        String msg = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        log.info("参数校验失败: {}", msg);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }


    @ExceptionHandler(JWTVerificationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handle401(JWTVerificationException e) {
        log.info("JWT 认证失败原生信息: {}", e.getMessage());
        String msg = MessageConstant.Auth.TOKEN_INVALID;
        if (e instanceof TokenExpiredException) {
            msg = MessageConstant.Auth.TOKEN_EXPIRED;
        }
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handle404(Exception e) {
        log.warn("请求的接口或资源不存在: {}", e.getMessage());
        return Result.error(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getDefaultMessage());
    }


    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据库唯一约束冲突: {}", e.getMessage());
        String msg = e.getMessage();
        String returnMsg = MessageConstant.Common.DATA_EXIST;

        if (msg != null) {
            if (msg.contains("uk_user_post")) {
                returnMsg = MessageConstant.Comment.ALREADY_LIKED;
            } else if (msg.contains("uk_uuid")) {
                returnMsg = MessageConstant.Common.DATA_EXIST;
            }
        }
        return Result.error(ResultCode.BUSINESS_ERROR.getCode(), returnMsg);
    }


    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result handleMultipartException(Exception e) {
        log.warn("上传异常: {}", e.getMessage());
        if (e instanceof MaxUploadSizeExceededException) {
            return Result.error(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Common.FILE_TOO_LARGE);
        }
        return Result.error(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Common.NETWORK_ABORT);
    }

    /* 客户端提前断开（切页、取消加载）不是错误，静默丢弃 */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        log.debug("客户端已断开连接");
    }

    /* 连接关闭后再写响应会抛这个，忽略即可 */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleHttpMessageNotWritableException(HttpMessageNotWritableException e) {
        log.debug("响应流已关闭，跳过写入");
    }


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handle500(Exception e) {
        log.error("系统未知错误: ", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
