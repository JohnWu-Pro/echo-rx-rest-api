package org.wjh.http.server;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.wjh.http.server.ServerHttpLoggingHandler.LoggingServerHttpRequest;

import brave.Span;
import reactor.core.publisher.Mono;

class ServerHttpLoggingWebFilter implements WebFilter, Ordered {

    private static final String TRACE_REQUEST_ATTR = "org.springframework.cloud.sleuth.instrument.web.TraceWebFilter.TRACE";

    @Override
    public int getOrder() {
        // On the way in, the filter should be after the content decryption filter (if any), and prior to spring.security.filter (order=-100)
        // On the way out, the filter should be (almost) the last one
        return -1000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        triggerRequestLoggingIfApplicable(exchange);
        return chain.filter(exchange);
    }

    private void triggerRequestLoggingIfApplicable(ServerWebExchange exchange) {
        if (exchange.getRequest() instanceof LoggingServerHttpRequest) {
            LoggingServerHttpRequest decorated = (LoggingServerHttpRequest) exchange.getRequest();
            decorated.triggerLogging(currentSpan(exchange));
        }
    }

    private Span currentSpan(ServerWebExchange exchange) {
        return (Span) exchange.getAttributes().get(TRACE_REQUEST_ATTR);
    }

}
