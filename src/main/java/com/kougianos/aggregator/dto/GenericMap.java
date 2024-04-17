package com.kougianos.aggregator.dto;

import java.util.LinkedHashMap;

public class GenericMap extends LinkedHashMap<String, Object> {

    public GenericMap() {
    }

    public GenericMap(GenericMap value) {
        super(value);
    }
}