package org.wjh.http.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(value = "http.inbound.logging", havingValue = "true", matchIfMissing = false)
public class LoggingServerHttpWebFilterAutoConfiguration {

    @Bean
    public LoggingServerHttpWebFilter serverHttpLoggingWebFilter() {
        return new LoggingServerHttpWebFilter();
    }

}
