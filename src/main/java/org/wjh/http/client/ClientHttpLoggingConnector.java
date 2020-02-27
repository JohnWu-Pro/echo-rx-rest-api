package org.wjh.http.client;

import static org.wjh.http.logging.HttpLogger.EMPTY_BODY_MONO;
import static org.wjh.http.logging.HttpLogger.MessageDirection.Inbound;
import static org.wjh.http.logging.HttpLogger.MessageDirection.Outbound;
import static org.wjh.tracing.TracingUtils.executeInContext;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpResponseDecorator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.wjh.http.logging.HttpLogger;
import org.wjh.http.logging.WiretapRecorder;
import org.wjh.tracing.TracingUtils.TracingContext;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext.Extractor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ClientHttpLoggingConnector implements ClientHttpConnector {

    private static final Logger logger = LoggerFactory.getLogger(ClientHttpLoggingConnector.class);

    private final ClientHttpConnector delegate;
    private final HttpLogger httpLogger;

    private Extractor<HttpHeaders> extractor;

    ClientHttpLoggingConnector(ClientHttpConnector delegate, HttpLogger httpLogger) {
        this.delegate = delegate;
        this.httpLogger = httpLogger;
    }

    private Extractor<HttpHeaders> extractor() {
        if (extractor == null) {
            extractor = Tracing.current().propagation().extractor(HttpHeaders::getFirst);
        }
        return extractor;
    }

    private Span currentSpan(HttpHeaders headers) {
        return Tracing.currentTracer().toSpan(extractor().extract(headers).context());
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
        if (httpLogger.shouldLog(method, uri)) {
            TracingContext context = new TracingContext();

            return delegate //@formatter:off
                    .connect(method, uri, request -> requestCallback.apply(new LoggingClientHttpRequest(request, context)))
                    .map(response -> new LoggingClientHttpResponse(response, context))
                    .map(LoggingClientHttpResponse::triggerLogging); //@formatter:on
        } else {
            return delegate.connect(method, uri, requestCallback);
        }
    }

    private void logRequest(LoggingClientHttpRequest request) {
        logger.trace("Calling logRequest({}) ...", request);

        HttpHeaders headers = request.getHeaders();
        boolean shouldLogBody = httpLogger.shouldLogRequestBody(request.getMethod(), headers);
        Mono<byte[]> bodyMono = shouldLogBody ? request.getRecorder().getContent().checkpoint("LoggingClientHttpRequest") : EMPTY_BODY_MONO;

        bodyMono.subscribe(body -> executeInContext(request.getTracingContext(), //@formatter:off
                () -> httpLogger.logRequest(Outbound, request.getMethod().name(), request.getURI(), headers, body)
                )); //@formatter:on
    }

    private void logResponse(LoggingClientHttpResponse response) {
        logger.trace("Calling logResponse({}) ...", response);

        HttpHeaders headers = response.getHeaders();
        int statusCode = response.getRawStatusCode();
        boolean shouldLogBody = httpLogger.shouldLogResponseBody(statusCode, headers);
        Mono<byte[]> bodyMono = shouldLogBody ? response.getRecorder().getContent().checkpoint("LoggingClientHttpResponse") : EMPTY_BODY_MONO;

        bodyMono.subscribe(body -> executeInContext(response.getTracingContext(), //@formatter:off
                () -> httpLogger.logResponse(Inbound, statusCode, null, headers, body)
                )); //@formatter:on
    }

    /**
     * ClientHttpRequestDecorator that intercepts and saves the request body.
     */
    class LoggingClientHttpRequest extends ClientHttpRequestDecorator {

        private final TracingContext context;

        @Nullable
        private WiretapRecorder recorder;

        public LoggingClientHttpRequest(ClientHttpRequest delegate, TracingContext context) {
            super(delegate);
            this.context = context;
        }

        public TracingContext getTracingContext() {
            return context;
        }

        public WiretapRecorder getRecorder() {
            Assert.notNull(recorder, "No Wiretap: was the client request written?");
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

                context.span = currentSpan(getHeaders());
                executeInContext(context, () -> logRequest(this));
            } else {
                logger.debug("Suppressed of triggering logging.");
            }
        }
    }

    /**
     * ClientHttpResponseDecorator that intercepts and saves the response body.
     */
    class LoggingClientHttpResponse extends ClientHttpResponseDecorator {

        private final TracingContext context;

        private final WiretapRecorder recorder;

        public LoggingClientHttpResponse(ClientHttpResponse delegate, TracingContext context) {
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

        public LoggingClientHttpResponse triggerLogging() {
            executeInContext(context, () -> logResponse(this));
            return this;
        }
    }

}
