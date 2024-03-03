package com.fedex.aggregator.service;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import static com.fedex.aggregator.dto.Constants.*;

@Getter
@Service
public class QueueManager {

    private final ConcurrentMap<String, BlockingQueue<String>> apiQueues;

    public QueueManager() {
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new LinkedBlockingQueue<>());
        this.apiQueues.put(TRACK, new LinkedBlockingQueue<>());
        this.apiQueues.put(SHIPMENTS, new LinkedBlockingQueue<>());
    }

    public BlockingQueue<String> get(String apiName) {
        return apiQueues.get(apiName);
    }

}
