package com.loopia.gameanimationshow.service;

import com.loopia.gameanimationshow.model.vo.ActivityStatsVO;
import com.loopia.gameanimationshow.model.vo.OverviewStatsVO;
import com.loopia.gameanimationshow.model.vo.PostStatsVO;

/**
 * @author NaNloopia
 * @version 1.0
 * @since 2026/9/18 
 * @created 1:56 
 */
public interface StatsService {
    /**
     * 获取首页极简数据看板概览
     * @return OverviewStatsVO
     */
    OverviewStatsVO getOverviewStats();

    /**
     * 活跃度热力图与标签云的数据源，全部在 SQL 里聚合
     */
    ActivityStatsVO getActivityStats();

    /**
     * 随笔聚合统计：总浏览量（含 Redis 未落库增量）与总字数
     */
    PostStatsVO getPostStats();
}
