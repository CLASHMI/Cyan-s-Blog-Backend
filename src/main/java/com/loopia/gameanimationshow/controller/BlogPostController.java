package com.loopia.gameanimationshow.controller;

import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.model.dto.BlogPostDTO;
import com.loopia.gameanimationshow.model.dto.BlogPostQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogPostVO;
import com.loopia.gameanimationshow.service.BlogPostService;
import com.loopia.gameanimationshow.service.PostViewCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogPostController {

    private final BlogPostService blogPostService;
    private final PostViewCountService postViewCountService;

    @GetMapping("/recent")
    public Result<List<BlogPost>> recent() {
        return Result.success(blogPostService.recentPosts());
    }

    @GetMapping("/list")
    public Result<PageResult<BlogPostVO>> list(BlogPostQueryDTO queryDTO) {
        PageResult<BlogPostVO> page = blogPostService.listPosts(queryDTO);
        /* 列表走缓存，view_count 可能是缓存时刻的旧值：这里按 DB 现值 + Redis 增量补成实时值 */
        postViewCountService.decorate(page.getRecords());
        return Result.success(page, MessageConstant.Common.QUERY_SUCCESS);
    }

    @GetMapping("/detail/{id}")
    public Result<BlogPostVO> detail(@PathVariable Long id) {
        /* 详情方法被 @Cacheable 包住，重复请求不会进方法体，阅读量增量必须放在这里 */
        postViewCountService.recordView(id);
        BlogPostVO vo = blogPostService.getPostDetail(id);
        postViewCountService.decorate(Collections.singletonList(vo));
        return Result.success(vo);
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

    @PostMapping("/recommend/{id}")
    public Result<Map<String, Object>> recommend(@PathVariable Long id) {
        Integer isRecommend = blogPostService.toggleRecommend(id);
        return Result.success(Map.of("id", id, "isRecommend", isRecommend),
                Integer.valueOf(1).equals(isRecommend) ? "已设为精选" : "已取消精选");
    }
}
