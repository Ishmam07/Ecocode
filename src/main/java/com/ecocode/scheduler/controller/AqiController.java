package com.ecocode.scheduler.controller;

import com.ecocode.scheduler.model.AqiMeasurement;
import com.ecocode.scheduler.service.AqiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aqi")
public class AqiController {

    private final AqiClient aqiClient;

    public AqiController(AqiClient aqiClient) {
        this.aqiClient = aqiClient;
    }

    // GET http://localhost:8080/api/v1/aqi/latest?city=Dhaka
    @GetMapping("/latest")
    public AqiMeasurement getLatest(@RequestParam(defaultValue = "Dhaka") String city) {
        return aqiClient.fetchLatest(city);
    }
}
