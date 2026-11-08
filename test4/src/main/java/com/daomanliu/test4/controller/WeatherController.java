package com.daomanliu.test4.controller;

import com.daomanliu.test4.common.Result;
import com.daomanliu.test4.dto.GeoResponse;
import com.daomanliu.test4.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    @Value("${qweather.host}")
    private String host;

    @Value("${qweather.key}")
    private String key;

    private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

    @GetMapping
    public Result<WeatherResponse> getWeather(@RequestParam("city") String city) {
        URI geoUri = UriComponentsBuilder
                .fromUriString(host + "/geo/v2/city/lookup")
                .queryParam("location", city)
                .queryParam("key", key)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        GeoResponse geo = restTemplate.getForObject(geoUri, GeoResponse.class);
        if (geo == null || geo.getLocation() == null || geo.getLocation().isEmpty()) {
            return Result.fail(404, "找不到城市" + city);
        }

        GeoResponse.Location loc =  geo.getLocation().get(0);

        String lat = String.format("%.2f", Double.parseDouble(loc.getLat()));
        String lon = String.format("%.2f", Double.parseDouble(loc.getLon()));

        String url = host + "/weather/v1/current/" + lat + "/" + lon + "?key=" + key;

        WeatherResponse weather = restTemplate.getForObject(url, WeatherResponse.class);

        if (weather == null || weather.getCondition() == null) {
            return Result.fail(500, "天气查询失败");
        }
        return Result.success(loc.getName() + "(" + loc.getAdm1() + ")", weather);
    }
}