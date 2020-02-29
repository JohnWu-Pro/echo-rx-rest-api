package org.wjh.http.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import brave.Tracer;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class InjectTraceIdWebFilter implements WebFilter, Ordered {

    private static final String TRACE_ID = "X-B3-TraceId";

    private final Tracer tracer;

    private InjectTraceIdWebFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public int getOrder() {
        // On the way in, the filter is not applicable
        // On the way out, the filter should be prior to logging filter (order=-1000)
        return -900;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).doOnSubscribe(s -> injectTraceId(exchange.getResponse()));
    }

    private void injectTraceId(ServerHttpResponse response) {
        String traceId = tracer.currentSpan().context().traceIdString();
        response.getHeaders().add(TRACE_ID, traceId);
    }
}
