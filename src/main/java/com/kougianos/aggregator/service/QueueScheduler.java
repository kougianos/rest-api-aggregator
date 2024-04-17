package com.kougianos.aggregator.service;

import com.kougianos.aggregator.queue.FedexQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.kougianos.aggregator.dto.Constants.QUEUE_SIZE;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.enable-queue-scheduler", havingValue = "true")
public class QueueScheduler implements InitializingBean {

    private final QueueManager queueManager;

    @Override
    public void afterPropertiesSet() {
        log.info("QueueScheduler is enabled!");
    }

    @Scheduled(fixedRate = 1000)
    public void queueScheduler() {
        queueManager.getApiQueues().keySet().forEach(key -> {

            var queue = queueManager.getApiQueues().get(key);

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
