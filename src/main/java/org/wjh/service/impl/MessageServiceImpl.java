package org.wjh.service.impl;

import static java.lang.System.currentTimeMillis;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.web.reactive.function.BodyInserters.fromFormData;
import static org.springframework.web.util.UriComponentsBuilder.fromHttpUrl;

import java.net.URI;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.wjh.service.MessageService;

import reactor.core.publisher.Mono;

@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    @Value("${echo.prefix}")
    private String prefix;

    @Value("${echo.remoteUrl}")
    private String remoteUrl;

    private final WebClient webClient;

    private MessageServiceImpl(WebClient.Builder builder) {
        webClient = builder.build();
    }

    @Override
    public Mono<String> get(String input) {
        logger.trace("Calling get({}) ...", input);

        Mono<String> result = isRemoteDefined() ? remoteGet(input) : Mono.just(input);

        return result.map(this::process);
    }

    private Mono<String> remoteGet(String input) {
        URI url = fromHttpUrl(remoteUrl).queryParam("input", input).build().toUri();

        return webClient.get()//@formatter:off
                .uri(url)
                .retrieve()
                .bodyToMono(String.class);//@formatter:on
    }

    @Override
    public Mono<String> post(String input) {
        logger.trace("Calling post({}) ...", input);

        Mono<String> result = isRemoteDefined() ? remotePost(input) : Mono.just(input);

        return result.map(this::process);
    }

    private Mono<String> remotePost(String input) {
        URI url = fromHttpUrl(remoteUrl).build().toUri();

        return webClient.post()//@formatter:off
                 .uri(url)
                 .contentType(APPLICATION_FORM_URLENCODED)
                 .body(fromFormData("input", input)
                         .with("timestamp", String.valueOf(currentTimeMillis()))
                         .with("test", "value contains = & $ special chars.")
                         )
                 .retrieve()
                 .bodyToMono(String.class);//@formatter:on
    }

    private boolean isRemoteDefined() {
        return remoteUrl != null && remoteUrl.startsWith("http");
    }

    private final Random random = new Random();
    private String process(String input) {
        try {
            Thread.sleep(200L + random.nextInt(100));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return prefix + input;
    }
}
