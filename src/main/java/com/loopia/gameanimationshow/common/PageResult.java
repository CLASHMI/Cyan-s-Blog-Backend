package com.loopia.gameanimationshow.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * PageResult 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/5 
 * @created 21:00 
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页数据列表
     */
    private List<T> records;
}
