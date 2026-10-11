package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogTag;
import com.loopia.gameanimationshow.model.dto.BlogTagDTO;
import com.loopia.gameanimationshow.model.dto.BlogTagQueryDTO;
import com.loopia.gameanimationshow.service.BlogTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class BlogTagController {

    private final BlogTagService blogTagService;

    @GetMapping("/list")
    public Result<PageResult<BlogTag>> list(BlogTagQueryDTO queryDTO) {
        return Result.success(blogTagService.listTags(queryDTO), MessageConstant.Common.QUERY_SUCCESS);
    }

    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody BlogTagDTO tagDTO) {
        blogTagService.addTag(tagDTO);
        return Result.success(MessageConstant.Common.ADD_SUCCESS);
    }

    @PutMapping("/update")
    public Result<Void> update(@Validated @RequestBody BlogTagDTO tagDTO) {
        blogTagService.updateTag(tagDTO);
        return Result.success(MessageConstant.Common.UPDATE_SUCCESS);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        blogTagService.deleteTag(id);
        return Result.success(MessageConstant.Common.DELETE_SUCCESS);
    }
}
