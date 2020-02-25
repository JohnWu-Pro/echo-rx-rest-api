package org.wjh.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;

public abstract class TracingUtils {

    private static final Logger logger = LoggerFactory.getLogger(TracingUtils.class);

    public static void executeInContext(TracingContext context, Runnable runable) {
        try (SpanInScope ws = Tracing.currentTracer().withSpanInScope(context.span)) {
            runable.run();
        } catch (RuntimeException | Error e) {
            context.span.error(e);
            logger.error("Unexpected error occurred.", e);
            //throw e;
        }
    }

    public static class TracingContext {
        public Span span;
    }

}
