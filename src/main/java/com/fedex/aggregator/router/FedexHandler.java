package com.fedex.aggregator.router;

import com.fedex.aggregator.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.fedex.aggregator.dto.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FedexHandler {

    // can be moved to app properties
    private static final List<String> acceptableParameters = List.of(PRICING, TRACK, SHIPMENTS);
    private final AggregationService aggregationService;

    public Mono<ServerResponse> getAggregatedResponse(ServerRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("REQUEST: {}", request.queryParams().toSingleValueMap());
        Map<String, String> parameters = cleanQueryParameters(request);

        var serverResponse = aggregationService.getAggregatedResponse(parameters);

        return serverResponse.flatMap(resp -> {
            long endTime = System.currentTimeMillis();
            log.info("RESPONSE ({}ms): {}", endTime - startTime, resp);
            return ServerResponse.ok().bodyValue(resp);
        });

    }

    private Map<String, String> cleanQueryParameters(ServerRequest request) {
        var map = new HashMap<>(request.queryParams());
        map.keySet().retainAll(acceptableParameters);
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().get(0)));
    }

}
