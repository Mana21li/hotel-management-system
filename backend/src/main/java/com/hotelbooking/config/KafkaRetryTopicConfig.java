package com.hotelbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;

/**
 * Enables non-blocking retry topics ({@code @RetryableTopic}).
 * <p>
 * Unlike blocking {@code DefaultErrorHandler}, failed records are published to
 * {@code <topic>-retry-N} so the main partition consumer can keep polling other messages.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaRetryTopicConfig {
}
