package com.daomanliu.test4.controller;

import com.daomanliu.test4.common.Result;
import com.daomanliu.test4.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public Result<WeatherService.WeatherInfo> getWeather(@RequestParam("city") String city) {
        WeatherService.WeatherInfo info = weatherService.getWeather(city);
        if (info == null) {
            return Result.fail(404, "找不到城市: " + city);
        }
        return Result.success(info);
    }
}