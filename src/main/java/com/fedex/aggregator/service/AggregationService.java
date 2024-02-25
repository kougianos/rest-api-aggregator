package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.util.FedexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import static com.fedex.aggregator.dto.Constants.*;

@Service
@Slf4j
public class AggregationService {

    private final ExternalApiClient client;
    private final ConcurrentMap<String, BlockingQueue<String>> apiQueues;
    private static final int QUEUE_SIZE = 5;
    private final ConcurrentMap<String, GenericMap> apiResponses = new ConcurrentHashMap<>();

    public AggregationService(ExternalApiClient client) {
        this.client = client;
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new LinkedBlockingQueue<>());
        this.apiQueues.put(TRACK, new LinkedBlockingQueue<>());
        this.apiQueues.put(SHIPMENTS, new LinkedBlockingQueue<>());

        apiResponses.put(PRICING, new GenericMap());
        apiResponses.put(TRACK, new GenericMap());
        apiResponses.put(SHIPMENTS, new GenericMap());
    }

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {

        List<Mono<Entry<String, GenericMap>>> monoList = new ArrayList<>();
        parameters.forEach((apiName, params) -> {
            BlockingQueue<String> queue = apiQueues.get(apiName);
            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            paramList.forEach(param -> {
                if (!queue.contains(param)) {
                    queue.add(param);
                }
            });

            executeApiCall(monoList, apiName, queue);

        });

        Mono<List<Entry<String, GenericMap>>> zippedMono = zipApiResponses(monoList);

        return zippedMono.map(this::transformToAggregatedResponse);
    }

    private void executeApiCall(List<Mono<Entry<String, GenericMap>>> monoList, String apiName,
                                BlockingQueue<String> queue) {
        var p = String.join(",", queue.stream().toList());
        var apiCallMono = client.get(apiName, p, GenericMap.class)
            .defaultIfEmpty(new GenericMap())
            .doOnNext(response -> apiResponses.put(apiName, response))
            .map(response -> Map.entry(apiName, response));

        monoList.add(apiCallMono);
    }

    private Mono<List<Entry<String, GenericMap>>> zipApiResponses(List<Mono<Entry<String, GenericMap>>> monoList) {
        return Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(obj -> (Entry<String, GenericMap>) obj)
            .toList());
    }

    private Map<String, GenericMap> transformToAggregatedResponse(List<Entry<String, GenericMap>> responseList) {
        Map<String, GenericMap> aggregatedResponse = new HashMap<>();
        aggregatedResponse.put(TRACK, new GenericMap());
        aggregatedResponse.put(SHIPMENTS, new GenericMap());
        aggregatedResponse.put(PRICING, new GenericMap());
        responseList.forEach(responseEntry -> {
            var apiResponse = responseEntry.getValue();

            aggregatedResponse.put(responseEntry.getKey(), apiResponse);

        });

        FedexUtils.removeEmptyEntriesFromMap(aggregatedResponse);

        return aggregatedResponse;
    }

    @Scheduled(fixedRate = 2000)
    public void x() {
        log.info("API RESPONSES {}", apiResponses);
        log.info("QUEUES {}\n", apiQueues);
    }

}
