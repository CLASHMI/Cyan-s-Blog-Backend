package com.loopia.gameanimationshow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.loopia.gameanimationshow.common.PageResult;
import com.loopia.gameanimationshow.common.ResultCode;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.entity.BlogComment;
import com.loopia.gameanimationshow.entity.BlogLike;
import com.loopia.gameanimationshow.entity.BlogPost;
import com.loopia.gameanimationshow.entity.BlogPostTag;
import com.loopia.gameanimationshow.entity.BlogTag;
import com.loopia.gameanimationshow.entity.SysCategory;
import com.loopia.gameanimationshow.exception.ServiceException;
import com.loopia.gameanimationshow.mapper.BlogCommentMapper;
import com.loopia.gameanimationshow.mapper.BlogLikeMapper;
import com.loopia.gameanimationshow.mapper.BlogPostMapper;
import com.loopia.gameanimationshow.mapper.BlogPostTagMapper;
import com.loopia.gameanimationshow.mapper.BlogTagMapper;
import com.loopia.gameanimationshow.model.dto.BlogPostDTO;
import com.loopia.gameanimationshow.model.dto.BlogPostQueryDTO;
import com.loopia.gameanimationshow.model.vo.BlogPostVO;
import com.loopia.gameanimationshow.service.BlogPostService;
import com.loopia.gameanimationshow.service.SysCategoryService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl extends ServiceImpl<BlogPostMapper, BlogPost> implements BlogPostService {

    private final SysCategoryService sysCategoryService;
    private final BlogPostTagMapper blogPostTagMapper;
    private final BlogTagMapper blogTagMapper;
    private final BlogLikeMapper blogLikeMapper;
    private final BlogCommentMapper blogCommentMapper;

    @Value("${loopia.tencent.cos.secret-id}")
    private String secretId;
    @Value("${loopia.tencent.cos.secret-key}")
    private String secretKey;
    @Value("${loopia.tencent.cos.region}")
    private String regionName;
    @Value("${loopia.tencent.cos.bucket-name}")
    private String bucketName;
    @Value("${loopia.tencent.cos.domain-url}")
    private String domainUrl;

    @Override
    public List<BlogPost> recentPosts() {
        return list(new LambdaQueryWrapper<BlogPost>()
                .select(BlogPost::getId, BlogPost::getTitle, BlogPost::getCoverImage, BlogPost::getUpdateTime)
                .orderByDesc(BlogPost::getUpdateTime, BlogPost::getId)
                .last("LIMIT 5"));
    }

    @Override
    @Cacheable(value = "postList")
    public PageResult<BlogPostVO> listPosts(BlogPostQueryDTO queryDTO) {
        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<BlogPost>()
                .eq(queryDTO.getCategoryId() != null, BlogPost::getCategoryId, queryDTO.getCategoryId());

        String keyword = queryDTO.getKeyword();
        if (StringUtils.hasText(keyword)) {
            List<Integer> tagIds = matchTagIds(keyword);
            wrapper.and(inner -> {
                inner.like(BlogPost::getTitle, keyword);
                if (!tagIds.isEmpty()) {
                    inner.or().inSql(BlogPost::getId, tagPostIdSubSql(tagIds));
                }
            });
        }

        if (queryDTO.getTagId() != null) {
            wrapper.inSql(BlogPost::getId, tagPostIdSubSql(List.of(queryDTO.getTagId())));
        }

        Integer sortStrategy = queryDTO.getSortStrategy();
        boolean byViews = Integer.valueOf(1).equals(sortStrategy);
        boolean byRating = Integer.valueOf(2).equals(sortStrategy);
        boolean byLikes = Integer.valueOf(3).equals(sortStrategy);
        wrapper.orderByDesc(byViews, BlogPost::getViewCount)
                .orderByDesc(byLikes, BlogPost::getLikeCount)
                .orderByDesc(byRating, BlogPost::getAverageRating)
                .orderByDesc(BlogPost::getCreateTime);

        Page<BlogPost> page = this.page(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        List<BlogPost> posts = page.getRecords();
        if (posts.isEmpty()) {
            return new PageResult<>(page.getTotal(), Collections.emptyList());
        }
        return new PageResult<>(page.getTotal(), buildVoList(posts));
    }

    @Override
    @Cacheable(value = "postDetail", key = "#id")
    public BlogPostVO getPostDetail(Long id) {
        BlogPost post = this.getById(id);
        if (post == null) {
            throw new ServiceException(MessageConstant.Blog.POST_NOT_FOUND);
        }
        BlogPostVO vo = new BlogPostVO();
        BeanUtils.copyProperties(post, vo);
        if (post.getCategoryId() != null) {
            SysCategory category = sysCategoryService.getById(post.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        vo.setTagNames(listTagNames(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"postList", "statsOverview", "postStats"}, allEntries = true)
    public void addPost(BlogPostDTO postDTO) {
        UserContext.requireAdmin();
        BlogPost post = new BlogPost();
        BeanUtils.copyProperties(postDTO, post);
        post.setLikeCount(0);
        post.setViewCount(0);
        post.setAverageRating(BigDecimal.ZERO);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(post.getCreateTime());
        this.save(post);
        savePostTags(post.getId(), postDTO.getTagIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"postList", "postDetail", "statsOverview", "postStats"}, allEntries = true)
    public void updatePost(BlogPostDTO postDTO) {
        UserContext.requireAdmin();
        if (postDTO.getId() == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR.getCode(), MessageConstant.Blog.ID_CANNOT_BE_EMPTY);
        }
        BlogPost post = new BlogPost();
        BeanUtils.copyProperties(postDTO, post);
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);

        // 更新标签时先清空旧关联，再写入新关联
        if (postDTO.getTagIds() != null) {
            blogPostTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postDTO.getId()));
            savePostTags(postDTO.getId(), postDTO.getTagIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"postList", "postDetail", "statsOverview", "postStats"}, allEntries = true)
    public void deletePost(Long id) {
        UserContext.requireAdmin();
        blogCommentMapper.delete(new LambdaQueryWrapper<BlogComment>().eq(BlogComment::getPostId, id));
        blogLikeMapper.delete(new LambdaQueryWrapper<BlogLike>().eq(BlogLike::getPostId, id));
        blogPostTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, id));
        this.removeById(id);
    }

    @Override
    @CacheEvict(value = {"postList", "postDetail"}, allEntries = true)
    public String uploadCover(MultipartFile file) {
        UserContext.requireAdmin();
        if (file.isEmpty()) {
            throw new ServiceException(MessageConstant.Blog.COVER_CANNOT_BE_EMPTY);
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString().replace("-", "") + extension;
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(regionName));
            clientConfig.setHttpProtocol(HttpProtocol.https);
            COSClient cosClient = new COSClient(cred, clientConfig);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(file.getSize());
            objectMetadata.setContentType(file.getContentType());
            objectMetadata.setHeader("Cache-Control", "max-age=2592000");
            objectMetadata.setHeader("Content-Disposition", "inline");
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, newFileName, file.getInputStream(), objectMetadata);
            cosClient.putObject(putObjectRequest);
            cosClient.shutdown();
            return domainUrl + "/" + newFileName;
        } catch (Exception e) {
            log.error("封面图上传失败", e);
            throw new ServiceException(MessageConstant.Blog.UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"postList", "postDetail", "statsOverview"}, allEntries = true)
    public void likePost(Long id) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED);
        }
        Long count = blogLikeMapper.selectCount(new LambdaQueryWrapper<BlogLike>()
                .eq(BlogLike::getUserId, userId)
                .eq(BlogLike::getPostId, id));
        if (count == 0) {
            BlogLike like = new BlogLike();
            like.setUserId(userId);
            like.setPostId(id);
            like.setCreateTime(LocalDateTime.now());
            blogLikeMapper.insert(like);
            this.baseMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                    .setSql("like_count = like_count + 1")
                    .setSql("update_time = update_time")
                    .eq(BlogPost::getId, id));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"postList", "postDetail", "statsOverview"}, allEntries = true)
    public void unlikePost(Long id) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED);
        }
        int deleted = blogLikeMapper.delete(new LambdaQueryWrapper<BlogLike>()
                .eq(BlogLike::getUserId, userId)
                .eq(BlogLike::getPostId, id));
        if (deleted > 0) {
            this.baseMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                    .setSql("like_count = like_count - 1")
                    .setSql("update_time = update_time")
                    .eq(BlogPost::getId, id)
                    .gt(BlogPost::getLikeCount, 0));
        }
    }

    private List<BlogPostVO> buildVoList(List<BlogPost> posts) {
        Set<Integer> categoryIds = posts.stream()
                .map(BlogPost::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : sysCategoryService.listByIds(categoryIds).stream()
                .collect(Collectors.toMap(SysCategory::getId, SysCategory::getName, (v1, v2) -> v1));

        List<Long> postIds = posts.stream().map(BlogPost::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagNameMap = buildTagNameMap(postIds);

        return posts.stream().map(post -> {
            BlogPostVO vo = new BlogPostVO();
            BeanUtils.copyProperties(post, vo);
            vo.setCategoryName(categoryMap.get(post.getCategoryId()));
            vo.setTagNames(tagNameMap.getOrDefault(post.getId(), Collections.emptyList()));
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, List<String>> buildTagNameMap(List<Long> postIds) {
        List<BlogPostTag> relations = blogPostTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().in(BlogPostTag::getPostId, postIds));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Integer> tagIds = relations.stream().map(BlogPostTag::getTagId).collect(Collectors.toSet());
        Map<Integer, String> tagNameMap = blogTagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(BlogTag::getId, BlogTag::getName, (v1, v2) -> v1));
        return relations.stream()
                .filter(relation -> tagNameMap.containsKey(relation.getTagId()))
                .collect(Collectors.groupingBy(BlogPostTag::getPostId,
                        Collectors.mapping(relation -> tagNameMap.get(relation.getTagId()), Collectors.toList())));
    }

    private List<Integer> matchTagIds(String keyword) {
        return blogTagMapper.selectList(new LambdaQueryWrapper<BlogTag>()
                        .like(BlogTag::getName, keyword))
                .stream().map(BlogTag::getId).collect(Collectors.toList());
    }

    /* 子查询代替“先查 postId 再拼一长串 IN”，少一次往返，也不会顶到 IN 长度上限 */
    private String tagPostIdSubSql(List<Integer> tagIds) {
        String ids = tagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "SELECT post_id FROM blog_post_tag WHERE tag_id IN (" + ids + ")";
    }

    private List<String> listTagNames(Long postId) {
        List<BlogPostTag> relations = blogPostTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Integer> tagIds = relations.stream().map(BlogPostTag::getTagId).collect(Collectors.toSet());
        return blogTagMapper.selectBatchIds(tagIds).stream().map(BlogTag::getName).collect(Collectors.toList());
    }

    private void savePostTags(Long postId, List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        tagIds.stream().distinct().forEach(tagId -> blogPostTagMapper.insert(new BlogPostTag(postId, tagId)));
    }
}
