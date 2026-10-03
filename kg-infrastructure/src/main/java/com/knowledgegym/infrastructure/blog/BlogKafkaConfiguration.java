package com.knowledgegym.infrastructure.blog;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnExpression(
        "'${app.blog.kafka.enabled:false}' == 'true' or '${app.email.kafka-enabled:false}' == 'true'")
public class BlogKafkaConfiguration {
    @Bean
    CommonErrorHandler blogKafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(
                        record.topic().startsWith("email.") ? "email.events.dlq" : "blog.events.dlq", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
    }
}
