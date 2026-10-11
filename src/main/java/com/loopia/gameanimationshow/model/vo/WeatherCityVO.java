package com.loopia.gameanimationshow.model.vo;

/** 和风地点搜索结果；名称与 ID 仅取自上游，不信任客户端传来的城市名。 */
public record WeatherCityVO(String id, String name, String adm1, String adm2, String country) {}
