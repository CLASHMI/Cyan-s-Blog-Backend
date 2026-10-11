package com.loopia.gameanimationshow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全站随笔聚合统计：总点击量（含 Redis 未落库增量）与总字数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostStatsVO {

    /* 全站所有随笔 view_count 之和 + Redis 待刷增量 */
    private Long totalViews;

    /* 全站所有随笔正文字数之和 */
    private Long totalWords;
}
