package org.wjh.http.server;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.stereotype.Component;
import org.wjh.http.logging.HttpLogger;

@Component
@ConditionalOnProperty(value = "server.http.logging", havingValue = "true", matchIfMissing = false)
public class HttpHandlerBeanPostProcessor implements BeanPostProcessor {

    private final HttpLogger httpLogger;

    private HttpHandlerBeanPostProcessor(HttpLogger httpLogger) {
        this.httpLogger = httpLogger;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HttpHandler) {
            return decorate((HttpHandler) bean);
        }
        return bean;
    }

    private HttpHandler decorate(HttpHandler delegate) {
        return new ServerHttpLoggingHandler(delegate, httpLogger);
    }
}
