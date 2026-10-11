package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.model.dto.BlogPostDTO;
import com.loopia.gameanimationshow.model.dto.BlogPostQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogPostVO;
import com.loopia.gameanimationshow.service.BlogPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogPostController {

    private final BlogPostService blogPostService;

    @GetMapping("/recent")
    public Result<List<BlogPost>> recent() {
        return Result.success(blogPostService.recentPosts());
    }

    @GetMapping("/list")
    public Result<PageResult<BlogPostVO>> list(BlogPostQueryDTO queryDTO) {
        return Result.success(blogPostService.listPosts(queryDTO), MessageConstant.Common.QUERY_SUCCESS);
    }

    @GetMapping("/detail/{id}")
    public Result<BlogPostVO> detail(@PathVariable Long id) {
        return Result.success(blogPostService.getPostDetail(id));
    }

    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody BlogPostDTO postDTO) {
        blogPostService.addPost(postDTO);
        return Result.success(MessageConstant.Common.ADD_SUCCESS);
    }

    @PutMapping("/update")
    public Result<Void> update(@Validated @RequestBody BlogPostDTO postDTO) {
        blogPostService.updatePost(postDTO);
        return Result.success(MessageConstant.Common.UPDATE_SUCCESS);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        blogPostService.deletePost(id);
        return Result.success(MessageConstant.Common.DELETE_SUCCESS);
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(blogPostService.uploadCover(file), "上传成功");
    }

    @PostMapping("/like/{id}")
    public Result<Void> like(@PathVariable Long id) {
        blogPostService.likePost(id);
        return Result.success("点赞成功！");
    }

    @PostMapping("/unlike/{id}")
    public Result<Void> unlike(@PathVariable Long id) {
        blogPostService.unlikePost(id);
        return Result.success("已取消点赞！");
    }
}
