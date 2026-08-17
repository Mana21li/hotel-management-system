package com.hotelbooking.kafka;

import com.hotelbooking.config.KafkaConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Spring Boot can reach the local Kafka broker (Milestone 2).
 * <p>
 * Requires: {@code docker compose up -d kafka} (plus Postgres/Redis/ES for app context).
 */
@SpringBootTest
class KafkaConnectionIT {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void springBoot_canReachKafkaBroker() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(10, TimeUnit.SECONDS);
            int brokerCount = cluster.nodes().get(10, TimeUnit.SECONDS).size();

            assertThat(clusterId).isNotBlank();
            assertThat(brokerCount).isGreaterThanOrEqualTo(1);

            // Topic declared by KafkaConfig.NewTopic should exist after KafkaAdmin runs.
            var topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
            assertThat(topics).contains(KafkaConfig.BOOKING_EVENTS_TOPIC);
        }
    }
}
