package org.wjh.http.logging;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Tap into a Publisher of data buffers to save the content.
 */
public class WiretapRecorder {

    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private static final Scheduler LOGGING_SCHEDULER = Schedulers.newSingle("http-log");

    @Nullable
    private final Flux<? extends DataBuffer> publisher;

    @Nullable
    private final Flux<? extends Publisher<? extends DataBuffer>> nestedPublisher;

    private final DataBuffer buffer = BUFFER_FACTORY.allocateBuffer();

    private final MonoProcessor<byte[]> content = MonoProcessor.create();

    public WiretapRecorder(@Nullable Publisher<? extends DataBuffer> publisher,
            @Nullable Publisher<? extends Publisher<? extends DataBuffer>> nestedPublisher) {

        if (publisher != null && nestedPublisher != null) {
            throw new IllegalArgumentException("At most one publisher expected");
        }

        this.publisher = publisher == null ? null : //@formatter:off
                Flux.from(publisher)
                        .doOnNext(this.buffer::write)
                        .doOnError(this::handleOnError)
                        .doOnCancel(this::handleOnComplete)
                        .doOnComplete(this::handleOnComplete)
                ; //@formatter:on

        this.nestedPublisher = nestedPublisher == null ? null : //@formatter:off
                Flux.from(nestedPublisher)
                        .map(p -> Flux.from(p).doOnNext(this.buffer::write).doOnError(this::handleOnError))
                        .doOnError(this::handleOnError)
                        .doOnCancel(this::handleOnComplete)
                        .doOnComplete(this::handleOnComplete)
                ; //@formatter:on

        if (publisher == null && nestedPublisher == null) {
            this.content.onComplete();
        }
    }

    public Publisher<? extends DataBuffer> getPublisher() {
        Assert.notNull(publisher, "Publisher not in use.");
        return publisher;
    }

    public Publisher<? extends Publisher<? extends DataBuffer>> getNestedPublisher() {
        Assert.notNull(nestedPublisher, "Nested publisher not in use.");
        return nestedPublisher;
    }

    public Mono<byte[]> getContent() {
        return Mono.defer(() -> content).publishOn(LOGGING_SCHEDULER);
    }

    private void handleOnError(Throwable ex) {
        if (content.isTerminated()) {
            return;
        }

        content.onError(ex);
    }

    private void handleOnComplete() {
        if (content.isTerminated()) {
            return;
        }

        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        content.onNext(bytes);
        content.onComplete();
    }
}
