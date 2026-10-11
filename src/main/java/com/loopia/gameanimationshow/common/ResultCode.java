package com.loopia.gameanimationshow.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "身份未认证或已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求资源不存在"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    BUSINESS_ERROR(601, "业务处理失败");

    private final Integer code;
    private final String defaultMessage;
}
