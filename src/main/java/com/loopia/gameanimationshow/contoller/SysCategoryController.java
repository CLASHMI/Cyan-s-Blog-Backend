package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.SysCategory;
import com.loopia.gameanimationshow.model.dto.SysCategoryDTO;
import com.loopia.gameanimationshow.model.dto.SysCategoryQueryDTO;
import com.loopia.gameanimationshow.service.SysCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * SysCategoryController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/4 
 * @created 15:33 
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class SysCategoryController {

    private final SysCategoryService sysCategoryService;

    /**
     * 查询分类列表 (所有人可见)
     * @param queryDTO
     */
    @GetMapping("/list")
    public Result<PageResult<SysCategory>> list(SysCategoryQueryDTO queryDTO) {
        PageResult<SysCategory> pageResult = sysCategoryService.listCategories(queryDTO);
        return Result.success(pageResult, MessageConstant.Common.QUERY_SUCCESS);
    }


    /**
     * 新增分类 (仅限站长)
     */
    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody SysCategoryDTO categoryDTO) {
        sysCategoryService.addCategory(categoryDTO);
        return Result.success(MessageConstant.Common.ADD_SUCCESS);
    }

    /**
     * 修改分类 (仅限站长)
     */
    @PutMapping("/update")
    public Result<Void> update(@Validated @RequestBody SysCategoryDTO categoryDTO) {
        sysCategoryService.updateCategory(categoryDTO);
        return Result.success(MessageConstant.Common.UPDATE_SUCCESS);
    }

    /**
     * 删除分类 (仅限站长)
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        sysCategoryService.deleteCategory(id);
        return Result.success(MessageConstant.Common.DELETE_SUCCESS);
    }
}