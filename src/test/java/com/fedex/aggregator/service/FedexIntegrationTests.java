package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.SneakyThrows;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

        verify((externalApiClient), times(1)).get(anyString(), anyString());
        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

    @Test
    void testAllParameters_OK() {
        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get("track", "1,2,3,4,5"))
            .thenReturn(Mono.just(trackResponse));

        var pricingResponse = new GenericMap();
        pricingResponse.put("DE", 38.501620847664405);
        pricingResponse.put("CH", 1.8747583717610317);
        pricingResponse.put("CN", 94.16635949653589);
        pricingResponse.put("GB", 99.50306403621781);
        pricingResponse.put("NL", 53.786320622647764);
        Mockito.when(externalApiClient.get("pricing", "NL,CN,CH,GB,DE"))
            .thenReturn(Mono.just(pricingResponse));

        var shipmentsResponse = new GenericMap();
        shipmentsResponse.put("1", List.of("envelope"));
        shipmentsResponse.put("2", List.of("box", "pallet"));
        shipmentsResponse.put("3", List.of("envelope", "envelope", "pallet"));
        shipmentsResponse.put("4", List.of("pallet", "envelope", "envelope", "pallet"));
        shipmentsResponse.put("5", List.of("box", "box", "envelope", "pallet", "envelope"));
        Mockito.when(externalApiClient.get("shipments", "1,2,3,4,5"))
            .thenReturn(Mono.just(shipmentsResponse));

        var expectedResponse = new GenericMap();
        expectedResponse.put("track", trackResponse);
        expectedResponse.put("pricing", pricingResponse);
        expectedResponse.put("shipments", shipmentsResponse);

        webTestClient.get()
            .uri("/aggregation?pricing=NL,CN,CH,GB,DE&track=1,2,3,4,5&shipments=1,2,3,4,5")
            .exchange()
            .expectStatus().isOk()
            .expectBody(GenericMap.class)
            .value(response -> {
                assertEquals(expectedResponse, response);
            });

        verify((externalApiClient), times(3)).get(anyString(), anyString());
        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

}
