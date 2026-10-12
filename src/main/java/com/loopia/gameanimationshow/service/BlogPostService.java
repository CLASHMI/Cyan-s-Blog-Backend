package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.model.dto.BlogPostDTO;
import com.loopia.gameanimationshow.model.dto.BlogPostQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogPostVO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface BlogPostService extends IService<BlogPost> {

    PageResult<BlogPostVO> listPosts(BlogPostQueryDTO queryDTO);

    List<BlogPost> recentPosts();

    BlogPostVO getPostDetail(Long id);

    void addPost(BlogPostDTO postDTO);

    void updatePost(BlogPostDTO postDTO);

    void deletePost(Long id);

    String uploadCover(MultipartFile file);

    void likePost(Long id);

    void unlikePost(Long id);

    Integer toggleRecommend(Long id);
}
