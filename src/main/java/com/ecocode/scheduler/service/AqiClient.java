package com.ecocode.scheduler.service;

import com.ecocode.scheduler.model.AqiMeasurement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 5 input - fetches real PM2.5 data from OpenAQ v3.
 *
 * IMPORTANT: OpenAQ v3 has no "city" query parameter - locations are found
 * by coordinates + radius, not by city name. So this does it in 2 steps:
 *   1) find nearby monitoring locations for the city's coordinates that
 *      measure PM2.5 (parameters_id=2), and pick one that has actually
 *      reported data (some registered stations never report - their
 *      datetimeLast is null - so those are skipped)
 *   2) fetch that location's latest reading and pick out the PM2.5 sensor
 *
 * If no API key is configured, or no active station is found, or a call
 * fails, this falls back to a clearly-labelled mock reading so the rest
 * of the demo still works end-to-end.
 *
 * City -> coordinates is resolved dynamically via OpenStreetMap's Nominatim
 * geocoding API (free, no key required), so this works for any city name,
 * not just a hardcoded shortlist. Results are cached in-memory since a
 * city's coordinates never change within a run.
 */
@Service
public class AqiClient {

    private static final Logger log = LoggerFactory.getLogger(AqiClient.class);
    private static final int PM25_PARAMETER_ID = 2;
    private static final int SEARCH_RADIUS_METERS = 25000; // OpenAQ's max allowed radius
    private static final int LOCATIONS_TO_CHECK = 10; // how many nearby stations to consider
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    // city name (lowercase) -> [lat, lon], populated lazily via geocode()
    private final Map<String, double[]> geocodeCache = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HealthRiskService healthRiskService;

    @Value("${openaq.api.key}")
    private String apiKey;

    @Value("${openaq.api.url}")
    private String apiUrl;

    public AqiClient(HealthRiskService healthRiskService) {
        this.healthRiskService = healthRiskService;
    }

    public AqiMeasurement fetchLatest(String city) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("openaq.api.key not set - returning a mock AQI reading for {}", city);
            return mockReading(city);
        }

        double[] coords = geocode(city);
        if (coords == null) {
            log.info("Could not geocode city '{}' - returning a mock AQI reading", city);
            return mockReading(city);
        }

        try {
            // Step 1: find nearby locations that measure PM2.5
            String locationsUrl = apiUrl + "/locations?coordinates=" + coords[0] + "," + coords[1]
                    + "&radius=" + SEARCH_RADIUS_METERS + "&parameters_id=" + PM25_PARAMETER_ID
                    + "&limit=" + LOCATIONS_TO_CHECK;
            JsonNode locationsRoot = get(locationsUrl);
            if (locationsRoot == null) {
                return mockReading(city);
            }

            JsonNode results = locationsRoot.path("results");
            if (!results.isArray() || results.isEmpty()) {
                log.warn("No OpenAQ location found near {} - falling back to mock reading", city);
                return mockReading(city);
            }

            // Some registered stations have never actually reported data
            // (datetimeLast is null) - skip those and find one that's live.
            Integer locationId = null;
            Integer pm25SensorId = null;

            for (JsonNode location : results) {
                if (location.path("datetimeLast").isNull()) {
                    continue;
                }
                for (JsonNode sensor : location.path("sensors")) {
                    if ("pm25".equalsIgnoreCase(sensor.path("parameter").path("name").asText())) {
                        locationId = location.path("id").asInt();
                        pm25SensorId = sensor.path("id").asInt();
                        break;
                    }
                }
                if (locationId != null) {
                    break;
                }
            }

            if (locationId == null || pm25SensorId == null) {
                log.warn("No active OpenAQ PM2.5 station found near {} - falling back to mock reading", city);
                return mockReading(city);
            }

            // Step 2: get that location's latest readings and pick the PM2.5 sensor's value
            JsonNode latestRoot = get(apiUrl + "/locations/" + locationId + "/latest");
            if (latestRoot == null) {
                return mockReading(city);
            }

            double pm25 = 35.0;
            boolean found = false;
            for (JsonNode reading : latestRoot.path("results")) {
                if (reading.path("sensorsId").asInt() == pm25SensorId) {
                    pm25 = reading.path("value").asDouble(35.0);
                    found = true;
                    break;
                }
            }
            if (!found) {
                log.warn("No latest PM2.5 reading found for location {} - falling back to mock reading", locationId);
                return mockReading(city);
            }

            double healthRiskIndex = healthRiskService.computeHealthRiskIndex(pm25);
            double asthmaRiskPct = healthRiskService.computeAsthmaRiskPct(healthRiskIndex);
            return new AqiMeasurement(city, pm25, healthRiskIndex, asthmaRiskPct);

        } catch (Exception e) {
            log.warn("OpenAQ call failed ({}) - falling back to mock reading", e.getMessage());
            return mockReading(city);
        }
    }

    /**
     * Resolves a city name to [lat, lon] using Nominatim (OpenStreetMap),
     * caching the result so repeated calls for the same city don't hit the
     * geocoding API again. Returns null if the city can't be found or the
     * geocoding call fails - the caller falls back to a mock reading.
     */
    private double[] geocode(String city) {
        String key = city.toLowerCase(Locale.ROOT).trim();
        double[] cached = geocodeCache.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?q=" + encodedCity + "&format=json&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // Nominatim's usage policy requires a descriptive User-Agent.
                    .header("User-Agent", "EcoCodeScheduler/1.0 (hackathon project)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Nominatim returned status {} for city '{}'", response.statusCode(), city);
                return null;
            }

            JsonNode results = objectMapper.readTree(response.body());
            if (!results.isArray() || results.isEmpty()) {
                log.warn("Nominatim found no match for city '{}'", city);
                return null;
            }

            JsonNode first = results.get(0);
            double lat = first.path("lat").asDouble(Double.NaN);
            double lon = first.path("lon").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                return null;
            }

            double[] coords = new double[]{lat, lon};
            geocodeCache.put(key, coords);
            return coords;

        } catch (Exception e) {
            log.warn("Geocoding failed for city '{}' ({})", city, e.getMessage());
            return null;
        }
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("OpenAQ returned status {} for {} - body: {}", response.statusCode(), url, response.body());
            return null;
        }
        return objectMapper.readTree(response.body());
    }

    private AqiMeasurement mockReading(String city) {
        double mockPm25 = 42.0;
        double healthRiskIndex = healthRiskService.computeHealthRiskIndex(mockPm25);
        double asthmaRiskPct = healthRiskService.computeAsthmaRiskPct(healthRiskIndex);
        return new AqiMeasurement(city, mockPm25, healthRiskIndex, asthmaRiskPct);
    }
}