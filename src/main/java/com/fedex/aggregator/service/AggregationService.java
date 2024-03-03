package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static com.fedex.aggregator.dto.Constants.QUEUE_SIZE;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final ExternalApiClient client;
    private final QueueManager queueManager;
    private final Map<String, Mono<Entry<String, GenericMap>>> monoMap = new ConcurrentHashMap<>();

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {

        // populate queues, if any queue exceeds size 5 notify all threads waiting on that queue.
        parameters.forEach((apiName, params) -> {
            var queue = queueManager.get(apiName);
            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            paramList.forEach(param -> {

                if (!queue.contains(param)) {
                    synchronized (queue) {
                        queue.add(param);
                        log.info("Adding {} {}", param, queue);
                    }
                }

            });

            if (queue.size() >= QUEUE_SIZE) {
                synchronized (queue) {
                    queue.notifyAll();
                }
            }

        });

        // iterate parameters, wait for queues with size < 5
        parameters.forEach((apiName, params) -> {
            var queue = queueManager.get(apiName);

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
                    .doOnNext(response -> queue.clear())
                    .map(response -> Map.entry(apiName, response));

                monoMap.putIfAbsent(apiName, apiCallMono);

            }

        });

        var monosFromParams = new HashMap<>(monoMap);
        monosFromParams.keySet().removeIf(key -> !parameters.containsKey(key));
        Mono<List<Entry<String, GenericMap>>> zippedMono = zipApiResponses(monosFromParams.values().stream().toList());

        return zippedMono.map(list -> transformToAggregatedResponse(list, parameters))
            .doOnNext(m -> monoMap.clear());
    }

    private Mono<List<Entry<String, GenericMap>>> zipApiResponses(List<Mono<Entry<String, GenericMap>>> monoList) {
        return Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(obj -> (Entry<String, GenericMap>) obj)
            .toList());
    }

    private Map<String, GenericMap> transformToAggregatedResponse(List<Entry<String, GenericMap>> responseList,
                                                                  Map<String, String> parameters) {
        Map<String, GenericMap> aggregatedResponse = new HashMap<>();
        parameters.keySet().forEach(apiName -> aggregatedResponse.put(apiName, new GenericMap()));
        responseList.forEach(responseEntry -> {
            var apiResponse = new GenericMap(responseEntry.getValue());
            apiResponse.keySet().removeIf(key -> !Arrays.stream(parameters.get(responseEntry.getKey()).split(","))
                .toList().contains(key));
            aggregatedResponse.put(responseEntry.getKey(), apiResponse.isEmpty() ? null : apiResponse);
        });

        return aggregatedResponse;
    }

    @Scheduled(fixedRate = 4000)
    public void logQueues() {
        log.info("QUEUES {}\n", queueManager.getApiQueues());
    }

}
