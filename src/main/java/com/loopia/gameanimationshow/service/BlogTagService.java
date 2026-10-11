package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.entity.BlogTag;
import com.loopia.gameanimationshow.model.dto.BlogTagDTO;
import com.loopia.gameanimationshow.model.dto.BlogTagQueryDTO;

public interface BlogTagService extends IService<BlogTag> {

    PageResult<BlogTag> listTags(BlogTagQueryDTO queryDTO);

    void addTag(BlogTagDTO tagDTO);

    void updateTag(BlogTagDTO tagDTO);

    void deleteTag(Integer id);
}
