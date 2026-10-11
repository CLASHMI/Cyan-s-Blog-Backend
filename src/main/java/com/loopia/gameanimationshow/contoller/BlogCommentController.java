package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.model.dto.BlogCommentDTO;
import com.loopia.gameanimationshow.model.dto.BlogCommentQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogCommentVO;
import com.loopia.gameanimationshow.service.BlogCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class BlogCommentController {

    private final BlogCommentService blogCommentService;

    @GetMapping("/list")
    public Result<PageResult<BlogCommentVO>> listComments(@Validated BlogCommentQueryDTO queryDTO) {
        return Result.success(blogCommentService.listComments(queryDTO), MessageConstant.Common.QUERY_SUCCESS);
    }

    @PostMapping("/add")
    public Result<Void> addComment(@Validated @RequestBody BlogCommentDTO commentDTO) {
        blogCommentService.addComment(commentDTO);
        return Result.success("发布成功！");
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        blogCommentService.deleteComment(id);
        return Result.success(MessageConstant.Common.DELETE_SUCCESS);
    }
}
