package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.entity.SysCategory;
import com.loopia.gameanimationshow.model.dto.SysCategoryDTO;
import com.loopia.gameanimationshow.model.dto.SysCategoryQueryDTO;

/**
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 11:58 
 */
public interface SysCategoryService extends IService<SysCategory> {

    /**
     * 查询分类
     * @param queryDTO
     * @return
     */
    PageResult<SysCategory> listCategories(SysCategoryQueryDTO queryDTO);

    /**
     * 增加分类
     * @param categoryDTO
     */
    void addCategory(SysCategoryDTO categoryDTO);

    /**
     * 更新分类
     * @param categoryDTO
     */
    void updateCategory(SysCategoryDTO categoryDTO);

    /**
     * 删除分类
     * @param id
     */
    void deleteCategory(Integer id);
}
