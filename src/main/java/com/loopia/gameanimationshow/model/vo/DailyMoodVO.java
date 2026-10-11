package com.loopia.gameanimationshow.model.vo;

public record DailyMoodVO(String date, String mood, String label, String emoji,
                          String text, boolean aiGenerated, String weather) {}
