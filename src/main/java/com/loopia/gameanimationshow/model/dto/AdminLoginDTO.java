package com.loopia.gameanimationshow.model.dto;

import com.loopia.gameanimationshow.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AdminLoginDTO 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/9/19 
 * @created 22:56 
 */
@Data
public class AdminLoginDTO {

    @NotBlank(message = MessageConstant.User.SECRET_CANNOT_BE_EMPTY)
    private String password;

}
