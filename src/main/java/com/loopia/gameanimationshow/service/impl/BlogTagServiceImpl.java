package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogPostTag;
import com.loopia.gameanimationshow.entity.BlogTag;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.BlogPostTagMapper;
import com.loopia.gameanimationshow.mapper.BlogTagMapper;
import com.loopia.gameanimationshow.model.dto.BlogTagDTO;
import com.loopia.gameanimationshow.model.dto.BlogTagQueryDTO;
import com.loopia.gameanimationshow.service.BlogTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BlogTagServiceImpl extends ServiceImpl<BlogTagMapper, BlogTag> implements BlogTagService {

    private final BlogPostTagMapper blogPostTagMapper;

    private void checkNameDuplicate(String name, Integer excludeId) {
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getName, name)
                .ne(excludeId != null, BlogTag::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Tag.NAME_DUPLICATE);
        }
    }

    @Override
    @Cacheable(value = "tagList")
    public PageResult<BlogTag> listTags(BlogTagQueryDTO queryDTO) {
        Page<BlogTag> page = this.lambdaQuery()
                .like(StringUtils.hasText(queryDTO.getName()), BlogTag::getName, queryDTO.getName())
                .orderByDesc(BlogTag::getCreateTime)
                .page(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()));
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    @CacheEvict(value = {"tagList", "statsOverview"}, allEntries = true)
    public void addTag(BlogTagDTO tagDTO) {
        UserContext.requireAdmin();
        BlogTag tag = new BlogTag();
        BeanUtils.copyProperties(tagDTO, tag);
        tag.setName(tag.getName().trim());
        checkNameDuplicate(tag.getName(), null);
        tag.setCreateTime(LocalDateTime.now());
        this.save(tag);
    }

    @Override
    /* 改名会让随笔列表/详情里缓存的 tagNames 过期，必须一并清掉 */
    @CacheEvict(value = {"tagList", "statsOverview", "postList", "postDetail"}, allEntries = true)
    public void updateTag(BlogTagDTO tagDTO) {
        UserContext.requireAdmin();
        if (tagDTO.getId() == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.Tag.ID_CANNOT_BE_EMPTY);
        }
        BlogTag tag = new BlogTag();
        BeanUtils.copyProperties(tagDTO, tag);
        tag.setName(tag.getName().trim());
        checkNameDuplicate(tag.getName(), tagDTO.getId());
        this.updateById(tag);
    }

    @Override
    @CacheEvict(value = {"tagList", "statsOverview"}, allEntries = true)
    public void deleteTag(Integer id) {
        UserContext.requireAdmin();
        Long count = blogPostTagMapper.selectCount(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getTagId, id));
        if (count != null && count > 0) {
            throw new ServiceException(ResultCode.BUSINESS_ERROR.getCode(), MessageConstant.Tag.TAG_IN_USE);
        }
        this.removeById(id);
    }
}
