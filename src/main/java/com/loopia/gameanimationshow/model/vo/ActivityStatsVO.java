package com.loopia.gameanimationshow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 首页活跃度看板：按天的随笔数量 + 每个标签被引用次数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatsVO {

    /* key: yyyy-MM-dd，value: 当天发布的随笔数 */
    private Map<String, Integer> dayCounts;

    private List<TagUsageVO> tagCounts;

    private Map<String, Integer> categoryCounts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagUsageVO {

        private Integer id;

        private String name;

        private Integer count;
    }
}
