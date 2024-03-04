package com.fedex.aggregator.service;

import com.fedex.aggregator.queue.FedexQueue;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.fedex.aggregator.dto.Constants.*;

@Getter
@Service
public class QueueManager {

    private final ConcurrentMap<String, FedexQueue> apiQueues;

    public QueueManager() {
        this.apiQueues = new ConcurrentHashMap<>();
        this.apiQueues.put(PRICING, new FedexQueue());
        this.apiQueues.put(TRACK, new FedexQueue());
        this.apiQueues.put(SHIPMENTS, new FedexQueue());
    }

    public FedexQueue get(String apiName) {
        return apiQueues.get(apiName);
    }

}
