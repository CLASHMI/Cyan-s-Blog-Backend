package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.constant.MessageConstant;
import com.loopia.gameanimationshow.model.vo.ActivityStatsVO;
import com.loopia.gameanimationshow.model.vo.OverviewStatsVO;
import com.loopia.gameanimationshow.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * StatsController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/9/18 
 * @created 1:52 
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取首页极简数据看板概览
     * @return
     */
    @GetMapping("/activity")
    public Result<ActivityStatsVO> getActivityStats() {
        return Result.success(statsService.getActivityStats(), MessageConstant.Common.QUERY_SUCCESS);
    }

    @GetMapping("/overview")
    public Result<OverviewStatsVO> getOverviewStats() {
        OverviewStatsVO overviewStats = statsService.getOverviewStats();
        return Result.success(overviewStats, MessageConstant.Common.QUERY_SUCCESS);
    }
}
