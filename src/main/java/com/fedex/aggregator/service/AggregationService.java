package com.fedex.aggregator.service;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.util.FedexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Map.Entry;

import static com.fedex.aggregator.dto.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final ExternalApiClient client;

    public Mono<Map<String, GenericMap>> getAggregatedResponse(Map<String, String> parameters) {

        List<Mono<Entry<String, GenericMap>>> monoList = new ArrayList<>();
        parameters.forEach((apiName, params) -> callExternalApiAndPopulateList(monoList, apiName, params));
        Mono<List<Entry<String, GenericMap>>> zippedMono = zipApiResponses(monoList);

        return zippedMono.map(this::transformToAggregatedResponse);
    }

    private void callExternalApiAndPopulateList(List<Mono<Entry<String, GenericMap>>> monoList, String apiName,
                                                String params) {
        var paramList = Arrays.stream(params.split(",")).distinct();
        paramList.forEach(param -> {
            var apiCallMono = client.get(apiName, param, GenericMap.class)
                .defaultIfEmpty(new GenericMap())
                .map(response -> {
                    GenericMap field = new GenericMap();
                    field.put(param, response.get(param));
                    return Map.entry(apiName, field);
                });
            monoList.add(apiCallMono);
        });
    }

    private Mono<List<Entry<String, GenericMap>>> zipApiResponses(List<Mono<Entry<String, GenericMap>>> monoList) {
        return Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(obj -> (Entry<String, GenericMap>) obj)
            .toList());
    }

    private Map<String, GenericMap> transformToAggregatedResponse(List<Entry<String, GenericMap>> responseList) {
        Map<String, GenericMap> aggregatedResponse = new HashMap<>();
        aggregatedResponse.put(TRACK, new GenericMap());
        aggregatedResponse.put(SHIPMENTS, new GenericMap());
        aggregatedResponse.put(PRICING, new GenericMap());
        responseList.forEach(responseEntry -> {
            var apiResponse = responseEntry.getValue();

            aggregatedResponse.get(String.valueOf(responseEntry.getKey()))
                .put(apiResponse.firstEntry().getKey(), apiResponse.firstEntry().getValue());

        });

        FedexUtils.removeEmptyEntriesFromMap(aggregatedResponse);

        return aggregatedResponse;
    }

}
