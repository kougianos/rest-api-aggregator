package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "36000")
@DirtiesContext
@TestPropertySource(properties = {"app.enable-queue-scheduler=true"})
class SchedulerEnabledIT {

    private static final int SLA_SECONDS = 10;

    @Autowired
    QueueManager queueManager;

    @MockBean
    ExternalApiClient externalApiClient;

    @Autowired
    WebTestClient webTestClient;

    /**
     * External API delays 4 seconds for response.
     * Expect response to be returned in less than 10 seconds (Aggregator SLA)
     */
    @Test
    void testQueueScheduler_1param() {

        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get(eq("track"), anyString()))
            .thenReturn(Mono.just(trackResponse).delayElement(Duration.ofSeconds(4)));

        var expectedResponse = new LinkedHashMap<String, GenericMap>();
        expectedResponse.put("track", new GenericMap(trackResponse));
        expectedResponse.get("track").remove("4");
        expectedResponse.get("track").remove("5");

        var timestamp1 = Instant.now();
        webTestClient.get()
            .uri("/aggregation?track=1,2,3")
            .exchange()
            .expectStatus().isOk()
            .expectBody(GenericMap.class)
            .value(response -> {
                assertEquals(expectedResponse, response);
            });
        var timestamp2 = Instant.now();

        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));
        assertTrue(timestamp2.getEpochSecond() - timestamp1.getEpochSecond() < SLA_SECONDS);

    }

}
