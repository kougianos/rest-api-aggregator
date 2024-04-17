package com.kougianos.aggregator.service;

import com.kougianos.aggregator.queue.CustomQueue;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.kougianos.aggregator.dto.Constants.*;

@Getter
@Service
public class QueueManager {

    private final ConcurrentMap<String, CustomQueue> apiQueues;

    public QueueManager() {
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new CustomQueue());
        this.apiQueues.put(TRACK, new CustomQueue());
        this.apiQueues.put(SHIPMENTS, new CustomQueue());
    }

    public CustomQueue get(String apiName) {
        return apiQueues.get(apiName);
    }

}
