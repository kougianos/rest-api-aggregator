package com.fedex.aggregator.service;

import com.fedex.aggregator.queue.FedexQueue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.fedex.aggregator.dto.Constants.*;

@Getter
@Service
@Slf4j
public class QueueManager {

    private final ConcurrentMap<String, FedexQueue> apiQueues;

    public QueueManager() {
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new FedexQueue());
        this.apiQueues.put(TRACK, new FedexQueue());
        this.apiQueues.put(SHIPMENTS, new FedexQueue());
    }

    public BlockingQueue<String> get(String apiName) {
        return apiQueues.get(apiName);
    }

    @Scheduled(fixedRate = 1000)
    public void queueScheduler() {
        apiQueues.keySet().forEach(key -> {

            var queue = apiQueues.get(key);

            synchronized (queue) {
                if (!queue.isEmpty() && compareWithNow(queue)) {

                    for (int i = 0; i < QUEUE_SIZE; i++) {
                        queue.add(RandomStringUtils.randomNumeric(9));
                    }
                    queue.notifyAll();

                }
            }

        });
    }

    private boolean compareWithNow(FedexQueue queue) {
        var now = Instant.now();
        var result = now.getEpochSecond() - queue.getOldestElementInsertTimestamp().getEpochSecond() >= 5;
        log.debug("Comparing {} with {} {}",
            now.getEpochSecond(),
            queue.getOldestElementInsertTimestamp().getEpochSecond(),
            result);
        return result;
    }

}
