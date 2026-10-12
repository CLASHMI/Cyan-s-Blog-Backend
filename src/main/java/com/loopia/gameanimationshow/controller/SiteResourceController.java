package com.loopia.gameanimationshow.controller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.SiteResource;
import com.loopia.gameanimationshow.model.dto.SiteResourceDTO;
import com.loopia.gameanimationshow.model.dto.SiteResourceQueryDTO;
import com.loopia.gameanimationshow.service.SiteResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/resource")
@RequiredArgsConstructor
public class SiteResourceController {

    private final SiteResourceService siteResourceService;

    @GetMapping("/list")
    public Result<PageResult<SiteResource>> list(SiteResourceQueryDTO queryDTO) {
        return Result.success(siteResourceService.listResources(queryDTO), MessageConstant.Common.QUERY_SUCCESS);
    }

    @PostMapping("/like/{id}")
    public Result<Void> like(@PathVariable Long id) {
        siteResourceService.likeResource(id);
        return Result.success(MessageConstant.Resource.LIKE_SUCCESS);
    }

    @PostMapping("/unlike/{id}")
    public Result<Void> unlike(@PathVariable Long id) {
        siteResourceService.unlikeResource(id);
        return Result.success(MessageConstant.Resource.UNLIKE_SUCCESS);
    }

    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody SiteResourceDTO dto) {
        siteResourceService.addResource(dto);
        return Result.success(MessageConstant.Common.ADD_SUCCESS);
    }

    @PutMapping("/update")
    public Result<Void> update(@Validated @RequestBody SiteResourceDTO dto) {
        siteResourceService.updateResource(dto);
        return Result.success(MessageConstant.Common.UPDATE_SUCCESS);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        siteResourceService.deleteResource(id);
        return Result.success(MessageConstant.Common.DELETE_SUCCESS);
    }
}
