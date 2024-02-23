package com.fedex.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TrackingStatus {
    NEW,
    IN_TRANSIT("IN TRANSIT"),
    COLLECTING,
    COLLECTED,
    DELIVERING,
    DELIVERED;

    private final String displayName;

    TrackingStatus() {
        this.displayName = name();
    }

    TrackingStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @JsonCreator
    public static TrackingStatus fromString(String value) {
        return Arrays.stream(TrackingStatus.values())
            .filter(status -> status.getDisplayName().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid tracking status: " + value +
                ". Expected tracking statuses: " + Arrays.toString(TrackingStatus.values())));
    }
}