package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ExternalApiClientTest {

    protected static MockWebServer server;

    @Autowired
    ExternalApiClient externalApiClient;

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void afterAll() throws IOException {
        server.shutdown();
    }

    @DynamicPropertySource
    @SneakyThrows
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.external-api.url",
            () -> "http://localhost:" + server.getPort());
    }

    @Test
    void testHappyPath() {

        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");

        server.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "1": "DELIVERING",
                    "2": "COLLECTING",
                    "3": "COLLECTED",
                    "4": "NEW",
                    "5": "NEW"
                }"""));

        StepVerifier.create(externalApiClient.get("track", "1,2,3,4,5"))
            .expectNextMatches(response -> response.equals(trackResponse))
            .verifyComplete();

    }

    /**
     * External API takes more than 5 seconds (its SLA) to respond.
     * Client should throw readTimeout at 5 seconds and return empty response.
     */
    @Test
    void testLongResponseFromServer() {
        var t1 = Instant.now();

        server.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("""
                {
                    "1": "DELIVERING",
                    "2": "COLLECTING",
                    "3": "COLLECTED",
                    "4": "NEW",
                    "5": "NEW"
                }""")
            .setBodyDelay(8, TimeUnit.SECONDS));

        StepVerifier.create(externalApiClient.get("track", "1,2,3,4,5"))
            .expectNextMatches(response -> {
                var t2 = Instant.now();
                return response.equals(new GenericMap()) && t2.getEpochSecond() - t1.getEpochSecond() <= 5;
            })
            .verifyComplete();

    }

    /**
     * External API returns 503 UNAVAILABLE.
     * Client should handle the error and return empty map.
     */
    @Test
    void testErrorFromServer() {
        var t1 = Instant.now();

        server.enqueue(new MockResponse()
            .setResponseCode(HttpStatus.SERVICE_UNAVAILABLE.value())
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        );

        StepVerifier.create(externalApiClient.get("track", "1,2,3,4,5"))
            .expectNextMatches(response -> response.equals(new GenericMap()))
            .verifyComplete();

    }

}
