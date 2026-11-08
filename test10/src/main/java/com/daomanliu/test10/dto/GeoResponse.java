package com.daomanliu.test10.dto;

import lombok.Data;

import java.util.List;

@Data
public class GeoResponse {
    private String code;
    private List<Location> location;

    @Data
    public static class Location {
        private String name;    // 城市名
        private String lat;     // 经度
        private String lon;     // 纬度
        private String adm1;    // 省份
    }
}






























