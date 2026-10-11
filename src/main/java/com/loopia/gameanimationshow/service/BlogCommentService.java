package com.loopia.gameanimationshow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.entity.BlogComment;
import com.loopia.gameanimationshow.model.dto.BlogCommentDTO;
import com.loopia.gameanimationshow.model.dto.BlogCommentQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogCommentVO;

public interface BlogCommentService extends IService<BlogComment> {

    PageResult<BlogCommentVO> listComments(BlogCommentQueryDTO queryDTO);

    void addComment(BlogCommentDTO commentDTO);

    void deleteComment(Long id);
}
