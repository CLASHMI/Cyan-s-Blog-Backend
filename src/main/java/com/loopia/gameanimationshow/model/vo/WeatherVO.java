package com.loopia.gameanimationshow.model.vo;

import java.util.List;

public record WeatherVO(String city, String temp, String windDir, String icon, String text,
                        String obsTime, String link, List<Forecast> daily) {
    public record Forecast(String date, String min, String max, String icon, String text) {}
}
