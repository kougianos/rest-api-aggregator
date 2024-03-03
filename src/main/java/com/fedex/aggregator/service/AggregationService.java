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
    private final Map<String, Mono<Entry<String, GenericMap>>> monoMap = new ConcurrentHashMap<>();

    public AggregationService(ExternalApiClient client) {
        this.client = client;
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new LinkedBlockingQueue<>());
        this.apiQueues.put(TRACK, new LinkedBlockingQueue<>());
        this.apiQueues.put(SHIPMENTS, new LinkedBlockingQueue<>());
    }

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {

        // populate queues, if any queue exceeds size 5 notify all threads waiting on that queue.
        parameters.forEach((apiName, params) -> {
            var queue = apiQueues.get(apiName);
            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            paramList.forEach(param -> {

                if (!queue.contains(param)) {
                    synchronized (queue) {
                        queue.add(param);
                        log.error("Adding {} {}", param, queue);
                    }
                }

            });

            if (queue.size() >= QUEUE_SIZE) {
                synchronized (queue) {
                    queue.notifyAll();
                }
            }

        });

        parameters.forEach((apiName, params) -> {
            var queue = apiQueues.get(apiName);
            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            synchronized (queue) {
                while (queue.size() < QUEUE_SIZE) {
                    log.info("Thread is waiting for queue {} ... {}", apiName, queue);
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.info("Queue {} size is >= 5 {}", apiName, queue);

                String p = String.join(",", queue.stream().toList());

                var apiCallMono = client.get(apiName, p)
                    .doOnNext(response -> {
                        queue.clear();
                    })
                    .map(response -> Map.entry(apiName, response));
                monoMap.putIfAbsent(apiName, apiCallMono);

            }

        });

        var monosFromParams = new HashMap<>(monoMap);
        monosFromParams.keySet().removeIf(key -> !parameters.containsKey(key));
        Mono<List<Entry<String, GenericMap>>> zippedMono = zipApiResponses(monosFromParams.values().stream().toList());
        log.info("Calling APIS {}", monosFromParams.keySet());

        return zippedMono.map(list -> transformToAggregatedResponse(list, parameters))
            .doOnNext(m -> {
                monoMap.clear();
            });
    }

    private Mono<List<Entry<String, GenericMap>>> zipApiResponses(List<Mono<Entry<String, GenericMap>>> monoList) {
        return Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(obj -> (Entry<String, GenericMap>) obj)
            .toList());
    }

    private Map<String, GenericMap> transformToAggregatedResponse(List<Entry<String, GenericMap>> responseList, Map<String, String> parameters) {
        Map<String, GenericMap> aggregatedResponse = new HashMap<>();
        aggregatedResponse.put(TRACK, new GenericMap());
        aggregatedResponse.put(SHIPMENTS, new GenericMap());
        aggregatedResponse.put(PRICING, new GenericMap());
        responseList.forEach(responseEntry -> {
            var apiResponse = new GenericMap(responseEntry.getValue());
            apiResponse.keySet().removeIf(key -> !Arrays.stream(parameters.get(responseEntry.getKey()).split(",")).toList().contains(key));
            aggregatedResponse.put(responseEntry.getKey(), apiResponse);
        });

        FedexUtils.removeEmptyEntriesFromMap(aggregatedResponse);

        return aggregatedResponse;
    }

    @Scheduled(fixedRate = 4000)
    public void logQueues() {
        log.info("QUEUES {}\n", apiQueues);
    }

}
