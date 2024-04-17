package com.fedex.aggregator.dto;

public final class Constants {

    private Constants() {
        throw new IllegalStateException("Constants class");
    }

    public static final String PRICING = "pricing";
    public static final String TRACK = "track";
    public static final String SHIPMENTS = "shipments";
    public static final int QUEUE_SIZE = 5;

}
