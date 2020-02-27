package org.wjh.reactor;

import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class WiretapTests {

    private static final Logger logger = LoggerFactory.getLogger(WiretapTests.class);

    @Test
    void givenNoPublisher_whenSubscribe_thenEmitEmptyImmediately() {
        Wiretap wiretap = new Wiretap(null);

        logger.debug("Subscribing to the wiretap content ...");
        wiretap.getContent().subscribe(value -> assertThat(value).isEmpty());
    }

    @Test
    void givenColdPublisher_whenSubscribe_thenEmitConcatImmediately() {
        Publisher<String> publisher = coldPublisher();

        Wiretap wiretap = new Wiretap(publisher);
        Flux.from(wiretap.getPublisher()).subscribe(value -> {
            logger.debug("Got an element '{}' from the wiretapped publisher.", value);
        });

        logger.debug("Subscribing to the wiretap content ...");
        assertThat(wiretap.getContent().block()).isEqualTo("ABC");
    }

    private Publisher<String> coldPublisher() {
        return Flux.just("A", "B", "C");
    }

    @Test
    void givenUnconnectedHotPublisher_whenSubscribe_thenNoContent() throws InterruptedException {
        ConnectableFlux<String> publisher = hotAsyncPublisher(10L, 33L, "A", "B", "C");

        Wiretap wiretap = new Wiretap(publisher);
        Mono<String> content = wiretap.getContent();

        logger.debug("Going to subscribe to the wiretapped publisher ...");
        Flux.from(wiretap.getPublisher()).subscribe(value -> {
            logger.debug("Got the element '{}' from the wiretapped publisher.", value);
        });

        logger.debug("BEFORE CONNECT: Subscribing to the wiretap content ...");
        try {
            assertThat(content.block(ofMillis(199L))).isEqualTo("ABC");
            fail("Should NOT get here.");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Timeout on blocking read for 199 MILLISECONDS");
            logger.debug("Expected IllegalStateException occurred.");
        }

        Thread.sleep(300L);

        logger.debug("Test succeeded.");
    }

    @Test
    void givenHotPublisher_whenSubscribe_thenEmitConcatOnlyAfterConnection() throws InterruptedException {
        ConnectableFlux<String> publisher = hotAsyncPublisher(10L, 33L, "A", "B", "C");

        Wiretap wiretap = new Wiretap(publisher);
        Mono<String> content = wiretap.getContent();

        logger.debug("Going to subscribe to the wiretapped publisher ...");
        Flux.from(wiretap.getPublisher()).subscribe(value -> {
            logger.debug("Got the element '{}' from the wiretapped publisher.", value);
        });

        logger.debug("Going to connect to the original publisher ...");
        publisher.connect();

        logger.debug("AFTER CONNECT: Subscribing to the wiretap content ...");
        content.subscribe(value -> {
            assertThat(value).isEqualTo("ABC");
            logger.debug("AFTER CONNECT: Got value '{}' from wiretap content.", value);
        });
        // assertThat(content.block(ofMillis(199L))).isEqualTo("ABC");

        Thread.sleep(300L);

        logger.debug("Test succeeded.");
    }

    private <T> ConnectableFlux<T> hotAsyncPublisher(long delay, long interval, @SuppressWarnings("unchecked") T... values) {
        Iterator<T> iterator = asList(values).iterator();

        return Flux.create((FluxSink<T> emitter) -> {
            new Timer().schedule(new TimerTask() {
                @Override
                @SuppressWarnings("unchecked")
                public void run() {
                    if (iterator.hasNext()) {
                        String value = String.valueOf(iterator.next());
                        logger.debug("Going to emit element '{}' to the original publisher.", value);
                        emitter.next((T) value);
                    } else {
                        logger.debug("Going to complete the original publisher.");
                        emitter.complete();
                        cancel();
                    }
                }
            }, delay, interval);
        }).publishOn(Schedulers.parallel()).publish();
    }

}
