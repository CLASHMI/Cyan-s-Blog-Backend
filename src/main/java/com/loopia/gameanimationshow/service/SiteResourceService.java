package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.entity.SiteResource;
import com.loopia.gameanimationshow.model.dto.SiteResourceDTO;
import com.loopia.gameanimationshow.model.dto.SiteResourceQueryDTO;

public interface SiteResourceService extends IService<SiteResource> {

    PageResult<SiteResource> listResources(SiteResourceQueryDTO queryDTO);

    void likeResource(Long id);

    void unlikeResource(Long id);

    void addResource(SiteResourceDTO dto);

    void updateResource(SiteResourceDTO dto);

    void deleteResource(Long id);
}
