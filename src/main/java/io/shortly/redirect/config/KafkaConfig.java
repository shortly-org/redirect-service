package io.shortly.redirect.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.shortly.redirect.producer.ClickProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConfig.class);

    /** Counts the clicks Kafka did not take, instead of logging each one. */
    @Bean
    ProducerListener<Object, Object> droppedClicks(MeterRegistry meters) {
        Counter dropped = meters.counter(ClickProducer.DROPPED);
        return new ProducerListener<>() {
            @Override
            public void onError(
                    @NonNull ProducerRecord<Object, Object> producerRecord,
                    @Nullable RecordMetadata recordMetadata,
                    @NonNull Exception exception) {
                dropped.increment();
                LOG.debug("dropped a click: {}", exception.getMessage());
            }
        };
    }
}
