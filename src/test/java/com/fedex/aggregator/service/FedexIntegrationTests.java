package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "36000")
@ExtendWith(MockitoExtension.class)
class FedexIntegrationTests {

    protected static MockWebServer server;

    @Autowired
    QueueManager queueManager;

    @MockBean
    ExternalApiClient externalApiClient;

    @BeforeEach
    void setup() {

    }

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

    @Autowired
    WebTestClient webTestClient;

    @Test
    void testTrackResponse_OK() {

        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get("track", "1,2,3,4,5"))
            .thenReturn(Mono.just(trackResponse));

        var expectedResponse = new GenericMap();
        expectedResponse.put("track", trackResponse);

//        server.enqueue(new MockResponse()
//            .setResponseCode(HttpStatus.OK.value())
//            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//            .setBody("""
//                 {
//                     "1": "DELIVERING",
//                     "2": "COLLECTING",
//                     "3": "COLLECTED",
//                     "4": "NEW",
//                     "5": "NEW"
//                 }"""));

        webTestClient.get()
            .uri("/aggregation?track=1,2,3,4,5")
            .exchange()
            .expectStatus().isOk()
            .expectBody(GenericMap.class)
            .value(response -> {
                assertEquals(expectedResponse, response);
            });

        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

}
