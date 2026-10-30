package com.learning.financeflow.outbox;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfiguration {

    @Bean
    ProducerFactory<String, String> financeKafkaProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.producer.acks:all}") String acknowledgments,
            @Value("${spring.kafka.producer.properties.enable.idempotence:true}")
                    boolean idempotenceEnabled,
            @Value("${spring.kafka.producer.properties.delivery.timeout.ms:120000}")
                    int deliveryTimeoutMs,
            @Value("${spring.kafka.producer.properties.request.timeout.ms:30000}")
                    int requestTimeoutMs) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, acknowledgments);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, idempotenceEnabled);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    KafkaTemplate<String, String> financeKafkaTemplate(
            ProducerFactory<String, String> financeKafkaProducerFactory) {
        return new KafkaTemplate<>(financeKafkaProducerFactory);
    }
}
