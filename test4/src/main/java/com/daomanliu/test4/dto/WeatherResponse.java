package com.daomanliu.test4.dto;

import lombok.Data;

@Data
public class WeatherResponse {
    private Condition condition;      // 天气现象
    private Temp temperature;         // 温度
    private Temp feelsLike;           // 体感温度
    private Double humidity;          // 湿度（0~1 之间的小数！）
    private Wind wind;                // 风
    private Temp pressure;            // 气压
    private Temp visibility;          // 能见度

    @Data
    public static class Condition {
        private String text;          // 如"晴""少云"
        private String code;
    }

    @Data
    public static class Temp {
        private Double value;
        private String unit;          // 如 "°C"
    }

    @Data
    public static class Wind {
        private Direction direction;
        private Temp speed;
        private Integer scale;        // 风力等级
    }

    @Data
    public static class Direction {
        private Integer degree;
        private String compass;       // 如 "n"、"sw"
    }
}