package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.util.FedexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import static com.fedex.aggregator.dto.Constants.*;

@Service
@Slf4j
public class AggregationService {

    private final ExternalApiClient client;
    private final ConcurrentMap<String, BlockingQueue<String>> apiQueues;
    private static final int QUEUE_SIZE = 5;
    private final Map<String, Mono<Entry<String, GenericMap>>> monoMap = new ConcurrentHashMap<>();
    private final Semaphore queueSemaphore = new Semaphore(1);
    private final ConcurrentMap<String, String> paramMap = new ConcurrentHashMap<>();

    public AggregationService(ExternalApiClient client) {
        this.client = client;
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new LinkedBlockingQueue<>());
        this.apiQueues.put(TRACK, new LinkedBlockingQueue<>());
        this.apiQueues.put(SHIPMENTS, new LinkedBlockingQueue<>());

    }

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {

        Set<String> completedApis = new HashSet<>();

        // populate queues, if queue reaches size 5 call API.
        parameters.forEach((apiName, params) -> {
            var queue = apiQueues.get(apiName);

            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            paramList.forEach(param -> {

                var emptyQueueFlag = queue.isEmpty();

                if (!queue.contains(param)) {
                    synchronized (queue) {
                        queue.add(param);
//                        queue.notifyAll();
                        log.error("Adding {} {}", param, queue);
                    }
                }

            });

            if (queue.size() >= QUEUE_SIZE) {

                synchronized (queue) {
                    queue.notifyAll();
                }

                completedApis.add(apiName);

                String p = String.join(",", queue.stream().toList());

                var apiCallMono = client.get(apiName, p).doOnNext(response -> {
                    monoMap.remove(apiName);
                }).map(response -> Map.entry(apiName, response));
                monoMap.putIfAbsent(apiName, apiCallMono);

            }

        });

        parameters.forEach((apiName, params) -> {
            var paramList = Arrays.stream(params.split(",")).distinct().toList();

            if (completedApis.contains(apiName)) {
                return;
            }

            var queue = apiQueues.get(apiName);

//            acquire();

            synchronized (queue) {
                while (queue.size() < QUEUE_SIZE) {
                    log.info("Thread is waiting for queue {} to become 5... {}", apiName, queue);
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.info("Queue {} size is 5 now {}", apiName, queue);

                String p = String.join(",", queue.stream().toList());

                var apiCallMono = client.get(apiName, p)
//                    .doOnNext(response -> {
//                        monoMap.remove(apiName);
//                    })
                    .map(response -> Map.entry(apiName, response));
                monoMap.putIfAbsent(apiName, apiCallMono);

            }

//            release();

        });

        var monosFromParams = new HashMap<>(monoMap);
        monosFromParams.keySet().removeIf(key -> !parameters.containsKey(key));
        Mono<List<Entry<String, GenericMap>>> zippedMono = zipApiResponses(monosFromParams.values().stream().toList());
        log.info("Calling APIS {}", monosFromParams.keySet());

        return zippedMono.map(list -> transformToAggregatedResponse(list, parameters)).doOnNext(m -> {
            monoMap.clear();

            apiQueues.values().forEach(q -> {
                q.clear();
            });

        });
    }

    private boolean tryAcquire() {
        var b = queueSemaphore.tryAcquire();
        log.info("TRY ACQUIRE {}", b);
        return b;
    }

    private void acquire() {
        log.info("BLOCK SEMAPHORE");
        queueSemaphore.acquireUninterruptibly();
        log.info("UNBLOCK SEMAPHORE");
    }

    private void release() {
        queueSemaphore.release();
        log.info("RELEASE SEMAPHORE");
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<List<Entry<String, GenericMap>>> zipApiResponses(List<Mono<Entry<String, GenericMap>>> monoList) {
        return Mono.zip(monoList, objects -> Arrays.stream(objects).map(obj -> (Entry<String, GenericMap>) obj).toList());
    }

    private Map<String, GenericMap> transformToAggregatedResponse(List<Entry<String, GenericMap>> responseList, Map<String, String> parameters) {
        Map<String, GenericMap> aggregatedResponse = new HashMap<>();
        aggregatedResponse.put(TRACK, new GenericMap());
        aggregatedResponse.put(SHIPMENTS, new GenericMap());
        aggregatedResponse.put(PRICING, new GenericMap());
        responseList.forEach(responseEntry -> {
            var apiResponse = new GenericMap(responseEntry.getValue());
            apiResponse.keySet().removeIf(key -> !parameters.get(responseEntry.getKey()).contains(key));
            aggregatedResponse.put(responseEntry.getKey(), apiResponse);
        });

        FedexUtils.removeEmptyEntriesFromMap(aggregatedResponse);

        return aggregatedResponse;
    }

    private String takeFiveElements(BlockingQueue<String> queue) {
        List<String> first5paramsInQueue = new ArrayList<>();
        log.info("Trying to take 5 elements from Queue {}...", queue);
        try {
            for (int i = 0; i < 5; i++) {
                first5paramsInQueue.add(queue.take());
            }
            log.info("Get 5 elements from Queue {}", queue);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return String.join(",", first5paramsInQueue);
    }

    private String readFiveElements(BlockingQueue<String> queue) {
        Object[] array = queue.toArray();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 5 && i < array.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            result.append(array[i]);
        }
        return result.toString();
    }

    private Set<String> readFiveElementsSet(BlockingQueue<String> queue) {
        return new HashSet<>(Arrays.asList(readFiveElements(queue).split(",")));
    }

    @Scheduled(fixedRate = 4000)
    public void x() {
        log.info("SEMAPHORE {}", queueSemaphore.availablePermits());
        log.info("QUEUES {}\n", apiQueues);
    }

}
