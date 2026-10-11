package com.loopia.gameanimationshow.model.vo;

/** 当天的公开短句及生成额度状态；日期统一使用上海时区。 */
public record DailyQuoteVO(String date, String text, boolean aiAttempted) {}
