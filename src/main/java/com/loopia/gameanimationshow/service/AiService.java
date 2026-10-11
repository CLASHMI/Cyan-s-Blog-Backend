package com.loopia.gameanimationshow.service;

import com.loopia.gameanimationshow.model.vo.DailyQuoteVO;

/**
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/7 
 * @created 13:57 
 */
public interface AiService {
    /**
     * 呼叫 DeepSeek 生成看板助手的一句话播报
     *
     * @param isAdmin 是否以站长视角发言
     * @param refresh true 时跳过 30 分钟缓存重新生成
     */
    String generateSummary(boolean isAdmin, boolean refresh);

    /**
     * 为指定随笔生成 AI 摘要
     *
     * @param postId 随笔 ID
     * @return 纯文本摘要；文章不存在、没有正文或调用失败时返回空串
     */
    String summarizePost(Long postId);

    /** 公开读取：仅查询当日缓存，不触发生成。 */
    DailyQuoteVO dailyQuote();

    DailyQuoteVO saveDailyQuote(String text);

    DailyQuoteVO generateDailyQuote();

    /** 仅供心情服务在取得当日调用标记后使用。 */
    String generateMoodText(String mood, String weather);

    /**
     * 内容安全审核：判断文本是否包含辱骂、人身攻击、色情、引战或严重违规内容。
     *
     * @param text 待审核文本
     * @return true 表示合规放行；false 表示命中违规
     */
    boolean moderateText(String text);
}
