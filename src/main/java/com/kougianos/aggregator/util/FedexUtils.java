package com.kougianos.aggregator.util;

import com.kougianos.aggregator.dto.GenericMap;

import java.util.Map;

public final class FedexUtils {

    private FedexUtils() {
        throw new IllegalStateException("Util class");
    }

    public static void removeEmptyEntriesFromMap(Map<String, GenericMap> map) {
        map.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
