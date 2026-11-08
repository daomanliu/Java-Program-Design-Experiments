package com.daomanliu.test4.service;

import com.daomanliu.test4.dto.GeoResponse;
import com.daomanliu.test4.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class WeatherService {

    @Value("${qweather.host}")
    private String host;

    @Value("${qweather.key}")
    private String key;

    private final RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

    public WeatherInfo getWeather(String city) {
        URI geoUri = UriComponentsBuilder
                .fromUriString(host + "/geo/v2/city/lookup")
                .queryParam("location", city)
                .queryParam("key", key)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        GeoResponse geo = restTemplate.getForObject(geoUri, GeoResponse.class);
        if (geo == null || geo.getLocation() == null || geo.getLocation().isEmpty()) {
            return null;
        }

        GeoResponse.Location loc = geo.getLocation().get(0);
        String url = host + "/weather/v1/current/" + loc.getLat() + "/" + loc.getLon() + "?key=" + key;
        WeatherResponse w = restTemplate.getForObject(url, WeatherResponse.class);
        if (w == null || w.getCondition() == null) {
            return null;
        }
        return new WeatherInfo(loc, w);
    }

    public record WeatherInfo(GeoResponse.Location location, WeatherResponse weather) {}

}