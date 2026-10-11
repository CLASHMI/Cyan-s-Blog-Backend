package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.entity.SysCategory;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import com.loopia.gameanimationshow.mapper.SysCategoryMapper;
import com.loopia.gameanimationshow.model.dto.SysCategoryDTO;
import com.loopia.gameanimationshow.model.dto.SysCategoryQueryDTO;
import com.loopia.gameanimationshow.service.SysCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SysCategoryServiceImpl extends ServiceImpl<SysCategoryMapper, SysCategory> implements SysCategoryService {

    private final BlogPostMapper blogPostMapper;

    private void checkNameDuplicate(String name, Integer excludeId) {
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<SysCategory>()
                .eq(SysCategory::getName, name)
                .ne(excludeId != null, SysCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Category.NAME_DUPLICATE);
        }
    }

    @Override
    @Cacheable(value = "categoryList")
    public PageResult<SysCategory> listCategories(SysCategoryQueryDTO queryDTO) {
        Page<SysCategory> page = this.lambdaQuery()
                .like(StringUtils.hasText(queryDTO.getName()), SysCategory::getName, queryDTO.getName())
                .orderByAsc(SysCategory::getSort)
                .orderByDesc(SysCategory::getCreateTime)
                .page(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()));
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    @CacheEvict(value = {"categoryList", "statsOverview"}, allEntries = true)
    public void addCategory(SysCategoryDTO categoryDTO) {
        UserContext.requireAdmin();
        SysCategory category = new SysCategory();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setName(category.getName().trim());
        checkNameDuplicate(category.getName(), null);
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setCreateTime(LocalDateTime.now());
        this.save(category);
    }

    @Override
    @CacheEvict(value = {"categoryList", "statsOverview"}, allEntries = true)
    public void updateCategory(SysCategoryDTO categoryDTO) {
        UserContext.requireAdmin();
        if (categoryDTO.getId() == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.Category.ID_CANNOT_BE_EMPTY);
        }
        SysCategory category = new SysCategory();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setName(category.getName().trim());
        checkNameDuplicate(category.getName(), categoryDTO.getId());
        this.updateById(category);
    }

    @Override
    @CacheEvict(value = {"categoryList", "statsOverview"}, allEntries = true)
    public void deleteCategory(Integer id) {
        UserContext.requireAdmin();
        Long count = blogPostMapper.selectCount(new LambdaQueryWrapper<BlogPost>().eq(BlogPost::getCategoryId, id));
        if (count != null && count > 0) {
            throw new ServiceException(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Category.CATEGORY_IN_USE);
        }
        this.removeById(id);
    }
}
