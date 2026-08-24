package com.hotelbooking.recommendation.web;

import com.hotelbooking.recommendation.kafka.RecommendationBookingConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationStatsController.class)
class RecommendationStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationBookingConsumer recommendationBookingConsumer;

    @Test
    void stats_returnsProcessedAndFailureCounts() throws Exception {
        when(recommendationBookingConsumer.getProcessedCount()).thenReturn(4);
        when(recommendationBookingConsumer.getFailureCount()).thenReturn(0);

        mockMvc.perform(get("/internal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(4))
                .andExpect(jsonPath("$.failureCount").value(0));
    }
}
