package com.fedex.aggregator.queue;

import lombok.Getter;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

@Getter
public class FedexQueue extends LinkedBlockingQueue<String> {

    private Instant oldestElementInsertTimestamp;

    public FedexQueue() {
        this.oldestElementInsertTimestamp = Instant.now();
    }

    @Override
    public boolean add(@NonNull String e) {
        boolean success = super.add(e);
        if (success && size() == 1) {
            oldestElementInsertTimestamp = Instant.now();
        }
        return success;
    }

    @Override
    public void clear() {
        oldestElementInsertTimestamp = Instant.now();
        super.clear();
    }

}
