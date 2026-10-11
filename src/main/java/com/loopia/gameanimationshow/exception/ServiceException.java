package com.loopia.gameanimationshow.exception;

import com.loopia.gameanimationshow.common.ResultCode;
import lombok.Getter;

/**
 * ServiceException 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 22:25 
 */
@Getter
public class ServiceException extends RuntimeException {

    private final Integer code;

    public ServiceException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(ResultCode resultCode) {
        super(resultCode.getDefaultMessage());
        this.code = resultCode.getCode();
    }

    public ServiceException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
