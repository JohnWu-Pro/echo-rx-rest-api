package org.wjh.reactor;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

class Wiretap {

    private static final Logger logger = LoggerFactory.getLogger(Wiretap.class);

    @Nullable
    private final Flux<? extends String> publisher;

    private final StringBuilder buffer = new StringBuilder();

    private final MonoProcessor<String> content = MonoProcessor.create();

    private volatile boolean hasContentConsumer;

    public Wiretap(@Nullable Publisher<? extends String> publisher) {

        this.publisher = publisher == null ? null : //@formatter:off
                Flux.from(publisher)
                        .doOnSubscribe(s -> this.hasContentConsumer = true)
                        .doOnNext(this::handleOnNext)
                        .doOnError(this::handleOnError)
                        .doOnCancel(this::handleOnComplete)
                        .doOnComplete(this::handleOnComplete)
                ; //@formatter:on

        if (publisher == null) {
            this.content.onComplete();
        }
    }

    public Publisher<? extends String> getPublisher() {
        logger.trace("Calling getPublisher() ...");

        Assert.notNull(publisher, "Publisher not in use.");
        return publisher;
    }

    public Mono<String> getContent() {
        logger.trace("Calling getContent() ...");

        return Mono.defer(() -> {
            if (content.isTerminated()) {
                return content;
            }
            if (!hasContentConsumer) {
                // Couple of possible cases:
                // 1. Mock server never consumed request body (e.g. error before read)
                // 2. FluxExchangeResult: getResponseBodyContent called before getResponseBody
                // noinspection ConstantConditions
                publisher //@formatter:off
                        .onErrorMap(ex -> new IllegalStateException("Content has not been consumed, and an error was raised while attempting to produce it.", ex))
                        //.subscribe()
                        ; //@formatter:on
            }
            return content;
        });
    }

    private void handleOnNext(String value) {
        logger.trace("Calling handleOnNext({}) ...", value);

        buffer.append(value);
    }

    private void handleOnError(Throwable ex) {
        logger.trace("Calling handleOnError({}) ...", ex);

        if (!content.isTerminated()) {
            content.onError(ex);
        }
    }

    private void handleOnComplete() {
        logger.trace("Calling handleOnComplete() ...");

        if (!content.isTerminated()) {
            content.onNext(buffer.toString());
            content.onComplete();
        }
    }
}
