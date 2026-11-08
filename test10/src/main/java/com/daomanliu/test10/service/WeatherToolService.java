package com.daomanliu.test10.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherToolService {
    private final WeatherService weatherService;

    public WeatherToolService(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(description = "查询指定中国城市的实时天气，包括天气状况、温度、体感温度、湿度、风向风速、气压、能见度。当用户询问任何城市的天气时必须调用。")
    public String getWeather(String city) {
        WeatherService.WeatherInfo info = weatherService.getWeather(city);
        if (info == null) {
            return "未找到城市或查询失败: " + city;
        }
        var w = info.weather();
        var loc = info.location();
        return String.format(
                "%s（%s）当前天气：%s，温度 %.1f%s，体感 %.1f%s，湿度 %d%%，风向 %s，风速 %.1f %s（%d级），气压 %.0f %s，能见度 %.1f km",
                loc.getName(), loc.getAdm1(),
                w.getCondition().getText(),
                w.getTemperature().getValue(), w.getTemperature().getUnit(),
                w.getFeelsLike().getValue(), w.getFeelsLike().getUnit(),
                (int) Math.round(w.getHumidity() * 100),
                w.getWind().getDirection().getCompass().toUpperCase(),
                w.getWind().getSpeed().getValue(), w.getWind().getSpeed().getUnit(),
                w.getWind().getScale(),
                w.getPressure().getValue(), w.getPressure().getUnit(),
                w.getVisibility().getValue() / 1000
        );
    }
}


























