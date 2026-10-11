package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.SiteResource;
import com.loopia.gameanimationshow.entity.SiteResourceLike;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.SiteResourceLikeMapper;
import com.loopia.gameanimationshow.mapper.SiteResourceMapper;
import com.loopia.gameanimationshow.model.dto.SiteResourceDTO;
import com.loopia.gameanimationshow.model.dto.SiteResourceQueryDTO;
import com.loopia.gameanimationshow.service.SiteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteResourceServiceImpl extends ServiceImpl<SiteResourceMapper, SiteResource>
        implements SiteResourceService {

    private final SiteResourceLikeMapper siteResourceLikeMapper;

    /**
     * 列表：无条件分页；keyword 同时模糊匹配 title 与 description；
     * sortStrategy=1 按热度(like_count) 倒序，其余按最新(create_time) 倒序。
     */
    @Override
    @Cacheable(value = "resourceList")
    public PageResult<SiteResource> listResources(SiteResourceQueryDTO queryDTO) {
        LambdaQueryWrapper<SiteResource> wrapper = new LambdaQueryWrapper<>();

        String keyword = queryDTO.getKeyword();
        if (StringUtils.hasText(keyword)) {
            /* 标题或简介任一处命中即可，括号包住防止 or 破坏其它条件 */
            wrapper.and(w -> w.like(SiteResource::getTitle, keyword)
                    .or()
                    .like(SiteResource::getDescription, keyword));
        }

        boolean byHeat = Integer.valueOf(1).equals(queryDTO.getSortStrategy());
        wrapper.orderByDesc(byHeat, SiteResource::getLikeCount)
                .orderByDesc(SiteResource::getCreateTime);

        Page<SiteResource> page = this.page(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        List<SiteResource> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }
        return new PageResult<>(page.getTotal(), records);
    }

    /**
     * 点赞：写点赞记录 + like_count + 1。
     * 重复点赞交给 uk_user_resource 唯一索引兜底，命中即提示已点过赞。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "resourceList", allEntries = true)
    public void likeResource(Long id) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED);
        }
        if (this.getById(id) == null) {
            throw new ServiceException(MessageConstant.Resource.RESOURCE_NOT_FOUND);
        }

        SiteResourceLike like = new SiteResourceLike();
        like.setUserId(userId);
        like.setResourceId(id);
        like.setCreateTime(LocalDateTime.now());
        try {
            siteResourceLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            /* 命中 uk_user_resource：已经点过赞，直接结束并回滚 */
            throw new ServiceException(MessageConstant.Comment.ALREADY_LIKED);
        }

        this.baseMapper.update(null, new LambdaUpdateWrapper<SiteResource>()
                .setSql("like_count = like_count + 1")
                .eq(SiteResource::getId, id));
    }

    /**
     * 取消点赞：删掉自己的点赞记录，并把 like_count 减 1（不会减到负数）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "resourceList", allEntries = true)
    public void unlikeResource(Long id) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED);
        }
        int deleted = siteResourceLikeMapper.delete(new LambdaQueryWrapper<SiteResourceLike>()
                .eq(SiteResourceLike::getUserId, userId)
                .eq(SiteResourceLike::getResourceId, id));
        if (deleted > 0) {
            this.baseMapper.update(null, new LambdaUpdateWrapper<SiteResource>()
                    .setSql("like_count = like_count - 1")
                    .eq(SiteResource::getId, id)
                    .gt(SiteResource::getLikeCount, 0));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "resourceList", allEntries = true)
    public void addResource(SiteResourceDTO dto) {
        UserContext.requireAdmin();
        SiteResource resource = new SiteResource();
        BeanUtils.copyProperties(dto, resource);
        /* 主键、点赞数、创建时间由服务端决定，不信任前端传入 */
        resource.setId(null);
        resource.setLikeCount(0);
        resource.setCreateTime(LocalDateTime.now());
        this.save(resource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "resourceList", allEntries = true)
    public void updateResource(SiteResourceDTO dto) {
        UserContext.requireAdmin();
        if (dto.getId() == null) {
            throw new ServiceException(MessageConstant.Resource.ID_CANNOT_BE_EMPTY);
        }
        if (this.getById(dto.getId()) == null) {
            throw new ServiceException(MessageConstant.Resource.RESOURCE_NOT_FOUND);
        }
        SiteResource resource = new SiteResource();
        /* 只拷贝可编辑字段；like_count / create_time 不在 DTO 里，天然不会被覆盖 */
        BeanUtils.copyProperties(dto, resource);
        this.updateById(resource);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "resourceList", allEntries = true)
    public void deleteResource(Long id) {
        UserContext.requireAdmin();
        this.removeById(id);
    }
}
