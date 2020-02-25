package org.wjh.http.client;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.wjh.http.logging.HttpLogger;

@Component
@ConditionalOnProperty(value = "client.http.logging", havingValue = "true", matchIfMissing = false)
public class WebClientBuilderBeanPostProcessor implements BeanPostProcessor {

    private final ClientHttpConnector connector;
    private final HttpLogger httpLogger;

    private WebClientBuilderBeanPostProcessor(ClientHttpConnector connector, HttpLogger httpLogger) {
        this.connector = connector;
        this.httpLogger = httpLogger;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof WebClient) {
            WebClient webClient = (WebClient) bean;
            return decorate(webClient.mutate()).build();
        } else if (bean instanceof WebClient.Builder) {
            WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
            return decorate(webClientBuilder);
        }
        return bean;
    }

    private WebClient.Builder decorate(WebClient.Builder webClientBuilder) {
        return webClientBuilder //@formatter:off
                .clientConnector(new ClientHttpLoggingConnector(connector, httpLogger))
                ; //@formatter:on
    }
}
