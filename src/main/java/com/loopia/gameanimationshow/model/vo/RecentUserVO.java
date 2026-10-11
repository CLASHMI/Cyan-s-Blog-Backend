package com.loopia.gameanimationshow.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/** 公开访客列表：仅包含展示字段；createTime 是记录创建时间，不是最后访问时间。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RecentUserVO(Long id, String nickname, String avatar, Integer role,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTime) {}
