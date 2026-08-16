package com.eventplatform.kafka;

import com.eventplatform.contracts.CorrelationIds;
import com.eventplatform.contracts.KafkaEventHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@AutoConfiguration
@ConditionalOnClass(DefaultErrorHandler.class)
@EnableConfigurationProperties(KafkaReliabilityProperties.class)
public class KafkaReliabilityAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaReliabilityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    CommonErrorHandler platformKafkaCommonErrorHandler(
            KafkaTemplate kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaReliabilityProperties properties) {
        Counter retries = meterRegistry.counter("platform.kafka.consumer.retries");
        Counter deadLetters = meterRegistry.counter("platform.kafka.consumer.dead.lettered");
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.getDeadLetterSuffix(), record.partition()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(
                properties.getMaxRetries());
        backOff.setInitialInterval(properties.getInitialBackoff().toMillis());
        backOff.setMultiplier(properties.getMultiplier());
        backOff.setMaxInterval(properties.getMaxBackoff().toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            deadLetters.increment();
            LOGGER.error(
                    "Kafka record dead-lettered topic={} partition={} offset={} key={} messageId={} eventType={} correlationId={} exception={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    messageId(record), eventType(record), correlationId(record), exception.toString());
            recoverer.accept(record, exception);
        }, backOff);
        handler.setRetryListeners((record, exception, deliveryAttempt) -> {
            retries.increment();
            LOGGER.warn(
                    "Kafka record retry topic={} partition={} offset={} deliveryAttempt={} messageId={} eventType={} correlationId={} exception={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt,
                    messageId(record), eventType(record), correlationId(record), exception.toString());
        });
        handler.setAckAfterHandle(true);
        handler.setCommitRecovered(true);
        return handler;
    }

    private static String messageId(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
        String messageId = KafkaEventHeaders.value(record.headers(), KafkaEventHeaders.MESSAGE_ID);
        return messageId == null
                ? KafkaEventHeaders.value(record.headers(), KafkaEventHeaders.LEGACY_EVENT_ID)
                : messageId;
    }

    private static String eventType(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
        return KafkaEventHeaders.value(record.headers(), KafkaEventHeaders.EVENT_TYPE);
    }

    private static String correlationId(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
        return KafkaEventHeaders.value(record.headers(), CorrelationIds.KAFKA_HEADER);
    }
}
