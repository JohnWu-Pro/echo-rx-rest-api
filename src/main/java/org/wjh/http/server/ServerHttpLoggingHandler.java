package org.wjh.http.server;

import static java.time.Duration.ofMillis;
import static org.wjh.http.logging.HttpLogger.EMPTY_BODY_MONO;
import static org.wjh.http.logging.HttpLogger.MessageDirection.Inbound;
import static org.wjh.http.logging.HttpLogger.MessageDirection.Outbound;
import static org.wjh.tracing.TracingUtils.executeInContext;

import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.wjh.http.logging.HttpLogger;
import org.wjh.http.logging.WiretapRecorder;
import org.wjh.tracing.TracingUtils.TracingContext;

import brave.Span;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ServerHttpLoggingHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerHttpLoggingHandler.class);

    private final HttpHandler delegate;
    private final HttpLogger httpLogger;

    ServerHttpLoggingHandler(HttpHandler delegate, HttpLogger httpLogger) {
        this.delegate = delegate;
        this.httpLogger = httpLogger;
    }

    @Override
    public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
        boolean shouldLog = httpLogger.shouldLog(request.getMethod(), request.getURI());
        if (shouldLog) {
            TracingContext context = new TracingContext();
            return delegate.handle(new LoggingServerHttpRequest(request, context), new LoggingServerHttpResponse(response, context));
        } else {
            return delegate.handle(request, response);
        }
    }

    private void logRequest(LoggingServerHttpRequest request) {
        logger.trace("Calling logRequest({}) ...", request);

        HttpHeaders headers = request.getHeaders();
        boolean shouldLogBody = httpLogger.shouldLogRequestBody(request.getMethod(), headers);
        Mono<byte[]> bodyMono = shouldLogBody ? request.getRecorder().getContent().checkpoint("LoggingServerHttpRequest") : EMPTY_BODY_MONO;

        bodyMono.delaySubscription(ofMillis(1L)).subscribe(body -> executeInContext(request.getTracingContext(), //@formatter:off
                () -> httpLogger.logRequest(Inbound, request.getMethod().name(), request.getURI(), headers, body)
                )); //@formatter:on
    }

    private void logResponse(LoggingServerHttpResponse response) {
        logger.trace("Calling logResponse({}) ...", response);

        HttpStatus status = response.getStatusCode();
        HttpHeaders headers = response.getHeaders();
        boolean shouldLogBody = httpLogger.shouldLogResponseBody(status.value(), headers);
        Mono<byte[]> bodyMono = shouldLogBody ? response.getRecorder().getContent().checkpoint("LoggingServerHttpResponse") : EMPTY_BODY_MONO;

        bodyMono.delaySubscription(ofMillis(1L)).subscribe(body -> executeInContext(response.getTracingContext(), //@formatter:off
                () -> httpLogger.logResponse(Outbound, status.value(), status.getReasonPhrase(), headers, body)
                )); //@formatter:on
    }

    class LoggingServerHttpRequest extends ServerHttpRequestDecorator {

        private final TracingContext context;

        private final WiretapRecorder recorder;

        public LoggingServerHttpRequest(ServerHttpRequest delegate, TracingContext context) {
            super(delegate);
            this.context = context;
            this.recorder = new WiretapRecorder(super.getBody(), null);
        }

        public TracingContext getTracingContext() {
            return context;
        }

        public WiretapRecorder getRecorder() {
            return recorder;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            logger.trace("Calling getBody() ...");
            return Flux.from(recorder.getPublisher());
        }

        public void triggerLogging(Span span) {
            context.span = span;
            executeInContext(context, () -> logRequest(this));
        }
    }

    class LoggingServerHttpResponse extends ServerHttpResponseDecorator {

        private final TracingContext context;

        @Nullable
        private WiretapRecorder recorder;

        public LoggingServerHttpResponse(ServerHttpResponse delegate, TracingContext context) {
            super(delegate);
            this.context = context;
        }

        public TracingContext getTracingContext() {
            return context;
        }

        public WiretapRecorder getRecorder() {
            Assert.notNull(recorder, "No WiretapRecorder: was the client request written?");
            return recorder;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> publisher) {
            logger.trace("Calling writeWith({}) ...", publisher);

            recorder = new WiretapRecorder(publisher, null);
            triggerLogging();
            return super.writeWith(recorder.getPublisher());
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> publisher) {
            logger.trace("Calling writeAndFlushWith({}) ...", publisher);

            recorder = new WiretapRecorder(null, publisher);
            triggerLogging();
            return super.writeAndFlushWith(recorder.getNestedPublisher());
        }

        @Override
        public Mono<Void> setComplete() {
            logger.trace("Calling setComplete() ...");

            recorder = new WiretapRecorder(null, null);
            triggerLogging();
            return super.setComplete();
        }

        private AtomicInteger logCount = new AtomicInteger(0);

        private void triggerLogging() {
            if (logCount.getAndIncrement() == 0) {
                executeInContext(context, () -> logResponse(this));
            } else {
                logger.debug("Suppressed of triggering logging.");
            }
        }
    }

}
