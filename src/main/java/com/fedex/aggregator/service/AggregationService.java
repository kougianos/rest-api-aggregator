package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.queue.FedexQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static com.fedex.aggregator.dto.Constants.QUEUE_SIZE;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final ExternalApiClient client;
    private final QueueManager queueManager;
    private final Map<String, Mono<Entry<String, GenericMap>>> apiCallMap = new ConcurrentHashMap<>();
    private final Semaphore semaphore = new Semaphore(1);

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {
        var completedApis = new HashSet<>();

        // populate queues, if any queue exceeds size 5 notify all threads waiting on that queue,
        // and add API call to shared map.
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
                completedApis.add(apiName);

                synchronized (queue) {
                    queue.notifyAll();
                }

                callAPIAndAddToMap(queue, apiName);
            }

        });

        // iterate parameters, wait for queues with size < 5. Skip completed APIs.
        parameters.forEach((apiName, params) -> {
            if (completedApis.contains(apiName)) {
                return;
            }

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

                callAPIAndAddToMap(queue, apiName);

            }

        });

        var localApiCall = new HashMap<>(apiCallMap);
        // Call only the APIs that were requested by this thread
        localApiCall.keySet().removeIf(key -> !parameters.containsKey(key));
        Mono<List<Entry<String, GenericMap>>> zippedApiCalls = zipApiResponses(localApiCall.values().stream().toList());

        return zippedApiCalls.map(list -> transformToAggregatedResponse(list, parameters));
    }

    private void callAPIAndAddToMap(FedexQueue queue, String apiName) {
        String p = String.join(",", queue.stream().toList());
        var apiCallMono = client.get(apiName, p)
            .doOnNext(response -> {
                queue.clear();
                apiCallMap.remove(apiName);
            })
            .map(response -> Map.entry(apiName, response));
        apiCallMap.putIfAbsent(apiName, apiCallMono);
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
