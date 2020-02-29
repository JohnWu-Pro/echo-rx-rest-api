package org.wjh.http.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.wjh.http.server.ServerHttpLoggingHandler.LoggingServerHttpRequest;

import brave.Tracer;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(value = "server.http.logging", havingValue = "true", matchIfMissing = false)
public class ServerHttpLoggingWebFilter implements WebFilter, Ordered {

    private final Tracer tracer;

    private ServerHttpLoggingWebFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public int getOrder() {
        // On the way in, the filter should be after the content decryption filter (if any), and prior to spring.security.filter (order=-100)
        // On the way out, the filter is not applicable
        return -1000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).doOnSubscribe(s -> triggerRequestLoggingIfApplicable(exchange));
    }

    private void triggerRequestLoggingIfApplicable(ServerWebExchange exchange) {
        if (exchange.getRequest() instanceof LoggingServerHttpRequest) {
            LoggingServerHttpRequest decorated = (LoggingServerHttpRequest) exchange.getRequest();
            decorated.triggerLogging(tracer.currentSpan());
        }
    }

}
