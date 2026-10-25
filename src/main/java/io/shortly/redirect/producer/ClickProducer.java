package io.shortly.redirect.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.shortly.redirect.config.ClickProperties;
import io.shortly.redirect.model.ClickEvent;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes clicks to the {@value #CLICKS} topic, keyed by code, from one background sender: the
 * caller never waits on Kafka. Clicks queue while Kafka is away; when the queue is full, or Kafka
 * has not taken a click within its timeouts, the click is dropped and counted.
 */
@Component
public class ClickProducer {

    public static final String CLICKS = "clicks";

    public static final String DROPPED = "shortly.clicks.dropped";

    private static final Logger LOG = LoggerFactory.getLogger(ClickProducer.class);

    private final KafkaTemplate<String, ClickEvent> kafka;
    private final Counter dropped;
    private final ThreadPoolExecutor sender;

    public ClickProducer(
            KafkaTemplate<String, ClickEvent> kafka, MeterRegistry meters, ClickProperties props) {
        this.kafka = kafka;
        this.dropped = meters.counter(DROPPED);
        this.sender =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(props.queueSize()),
                        Thread.ofPlatform().name("click-sender").daemon().factory(),
                        (click, executor) -> dropped.increment());
        Gauge.builder("shortly.clicks.queued", sender, executor -> executor.getQueue().size())
                .register(meters);
    }

    public void send(ClickEvent event) {
        sender.execute(() -> publish(event));
    }

    /** Gives the clicks still queued a few seconds to reach the producer, which flushes them. */
    @PreDestroy
    void stop() throws InterruptedException {
        sender.shutdown();
        if (!sender.awaitTermination(10, TimeUnit.SECONDS)) {
            dropped.increment(sender.shutdownNow().size());
        }
    }

    private void publish(ClickEvent event) {
        try {
            kafka.send(CLICKS, event.code(), event);
        } catch (KafkaException e) {
            LOG.debug("dropped a click for {}: {}", event.code(), e.getMessage());
        } catch (RuntimeException e) {
            dropped.increment();
            LOG.debug("dropped a click for {}: {}", event.code(), e.getMessage());
        }
    }
}
