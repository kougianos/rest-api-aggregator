package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
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
class SchedulerDisabledIT {

    protected static MockWebServer server;

    @Autowired
    QueueManager queueManager;

    @MockBean
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
    void testTrackResponse_EmptyResponseFromExternalClient() {
        Mockito.when(externalApiClient.get("track", "1,2,3,4,5"))
            .thenReturn(Mono.just(new GenericMap()));

        var expectedResponse = new GenericMap();
        expectedResponse.put("track", null);

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

    /**
     * First call: /aggregation?track=1,2,3
     * Second call: /aggregation?track=4,5
     * -
     * Verify responses.
     * Verify queues empty.
     */
    @Test
    void testQueueScenario1() {
        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get(eq("track"), anyString()))
            .thenReturn(Mono.just(trackResponse));

        var expectedResponse1 = new LinkedHashMap<String, GenericMap>();
        expectedResponse1.put("track", new GenericMap(trackResponse));
        expectedResponse1.get("track").remove("4");
        expectedResponse1.get("track").remove("5");

        var expectedResponse2 = new LinkedHashMap<String, GenericMap>();
        expectedResponse2.put("track", new GenericMap(trackResponse));
        expectedResponse2.get("track").remove("1");
        expectedResponse2.get("track").remove("2");
        expectedResponse2.get("track").remove("3");

        // Perform the first webTestClient call
        CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() -> {
            webTestClient.get()
                .uri("/aggregation?track=1,2,3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse1, response);
                });
        });

        // Perform the second webTestClient call
        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() -> {
            sleep(200);
            webTestClient.get()
                .uri("/aggregation?track=4,5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse2, response);
                });
        });

        CompletableFuture.allOf(firstCall, secondCall).join();
        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

    /**
     * First call: /aggregation?track=1,2,3,4,5&shipments=1,2,3
     * Second call: /aggregation?shipments=4,5,6
     * -
     * Verify responses.
     * Verify queues empty.
     */
    @Test
    void testQueueScenario2() {
        //Mock Track Response
        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get(eq("track"), anyString()))
            .thenReturn(Mono.just(trackResponse));

        //Mock Shipments Response
        var shipmentsResponse = new GenericMap();
        shipmentsResponse.put("1", List.of("envelope"));
        shipmentsResponse.put("2", List.of("box", "pallet"));
        shipmentsResponse.put("3", List.of("envelope", "envelope", "pallet"));
        shipmentsResponse.put("4", List.of("pallet", "envelope", "envelope", "pallet"));
        shipmentsResponse.put("5", List.of("box", "box", "envelope", "pallet", "envelope"));
        shipmentsResponse.put("6", List.of("box", "box", "envelope", "pallet", "envelope","pallet"));
        Mockito.when(externalApiClient.get(eq("shipments"), anyString()))
            .thenReturn(Mono.just(shipmentsResponse));

        // Expected response 1
        var expectedResponse1 = new LinkedHashMap<String, GenericMap>();
        expectedResponse1.put("track", new GenericMap(trackResponse));
        expectedResponse1.put("shipments", new GenericMap(shipmentsResponse));
        expectedResponse1.get("shipments").remove("4");
        expectedResponse1.get("shipments").remove("5");
        expectedResponse1.get("shipments").remove("6");

        // Expected response 2
        var expectedResponse2 = new LinkedHashMap<String, GenericMap>();
        expectedResponse2.put("shipments", new GenericMap(shipmentsResponse));
        expectedResponse2.get("shipments").remove("1");
        expectedResponse2.get("shipments").remove("2");
        expectedResponse2.get("shipments").remove("3");

        // Perform the first webTestClient call
        CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() -> {
            webTestClient.get()
                .uri("/aggregation?track=1,2,3,4,5&shipments=1,2,3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse1, response);
                });
        });

        // Perform the second webTestClient call
        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() -> {
            sleep(200);
            webTestClient.get()
                .uri("/aggregation?shipments=4,5,6")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse2, response);
                });
        });

        CompletableFuture.allOf(firstCall, secondCall).join();
        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

    /**
     * First call: /aggregation?track=1,2,3,4,5&shipments=1
     * Second call: /aggregation?shipments=2,3
     * Third call: /aggregation?shipments=4,5,6&pricing=NL,CN,CH,GB,DE
     * -
     * Verify responses.
     * Verify queues empty.
     */
    @Test
    void testQueueScenario3() {
        //Mock Track Response
        var trackResponse = new GenericMap();
        trackResponse.put("1", "DELIVERING");
        trackResponse.put("2", "COLLECTING");
        trackResponse.put("3", "COLLECTED");
        trackResponse.put("4", "NEW");
        trackResponse.put("5", "NEW");
        Mockito.when(externalApiClient.get(eq("track"), anyString()))
            .thenReturn(Mono.just(trackResponse));

        //Mock Shipments Response
        var shipmentsResponse = new GenericMap();
        shipmentsResponse.put("1", List.of("envelope"));
        shipmentsResponse.put("2", List.of("box", "pallet"));
        shipmentsResponse.put("3", List.of("envelope", "envelope", "pallet"));
        shipmentsResponse.put("4", List.of("pallet", "envelope", "envelope", "pallet"));
        shipmentsResponse.put("5", List.of("box", "box", "envelope", "pallet", "envelope"));
        shipmentsResponse.put("6", List.of("box", "box", "envelope", "pallet", "envelope","pallet"));
        Mockito.when(externalApiClient.get(eq("shipments"), anyString()))
            .thenReturn(Mono.just(shipmentsResponse));

        //Mock Pricing Response
        var pricingResponse = new GenericMap();
        pricingResponse.put("DE", 38.501620847664405);
        pricingResponse.put("CH", 1.8747583717610317);
        pricingResponse.put("CN", 94.16635949653589);
        pricingResponse.put("GB", 99.50306403621781);
        pricingResponse.put("NL", 53.786320622647764);
        Mockito.when(externalApiClient.get(eq("pricing"), anyString()))
            .thenReturn(Mono.just(pricingResponse));

        // Expected response 1
        var expectedResponse1 = new LinkedHashMap<String, GenericMap>();
        expectedResponse1.put("track", new GenericMap(trackResponse));
        expectedResponse1.put("shipments", new GenericMap(shipmentsResponse));
        expectedResponse1.get("shipments").remove("2");
        expectedResponse1.get("shipments").remove("3");
        expectedResponse1.get("shipments").remove("4");
        expectedResponse1.get("shipments").remove("5");
        expectedResponse1.get("shipments").remove("6");

        // Expected response 2
        var expectedResponse2 = new LinkedHashMap<String, GenericMap>();
        expectedResponse2.put("shipments", new GenericMap(shipmentsResponse));
        expectedResponse2.get("shipments").remove("1");
        expectedResponse2.get("shipments").remove("4");
        expectedResponse2.get("shipments").remove("5");
        expectedResponse2.get("shipments").remove("6");

        // Expected response 3
        var expectedResponse3 = new LinkedHashMap<String, GenericMap>();
        expectedResponse3.put("shipments", new GenericMap(shipmentsResponse));
        expectedResponse3.get("shipments").remove("1");
        expectedResponse3.get("shipments").remove("2");
        expectedResponse3.get("shipments").remove("3");
        expectedResponse3.put("pricing", new GenericMap(pricingResponse));

        // Perform the first webTestClient call
        CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() -> {
            webTestClient.get()
                .uri("/aggregation?track=1,2,3,4,5&shipments=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse1, response);
                });
        });

        // Perform the second webTestClient call
        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() -> {
            sleep(200);
            webTestClient.get()
                .uri("/aggregation?shipments=2,3")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse2, response);
                });
        });

        // Perform the third webTestClient call
        CompletableFuture<Void> thirdCall = CompletableFuture.runAsync(() -> {
            sleep(500);
            webTestClient.get()
                .uri("/aggregation?shipments=4,5,6&pricing=NL,CN,CH,GB,DE")
                .exchange()
                .expectStatus().isOk()
                .expectBody(GenericMap.class)
                .value(response -> {
                    assertEquals(expectedResponse3, response);
                });
        });

        CompletableFuture.allOf(firstCall, secondCall, thirdCall).join();
        queueManager.getApiQueues().values().forEach(queue -> assertTrue(queue.isEmpty()));

    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
