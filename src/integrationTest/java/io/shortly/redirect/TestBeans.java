package io.shortly.redirect;

import io.shortly.redirect.producer.ClickProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration(proxyBeanMethods = false)
class TestBeans {

    @Bean
    NewTopic clicksTopic() {
        return TopicBuilder.name(ClickProducer.CLICKS).partitions(6).build();
    }

    @Bean
    ContractDatabase contractDatabase() {
        return new ContractDatabase(AbstractIntegrationTest.POSTGRES);
    }
}
