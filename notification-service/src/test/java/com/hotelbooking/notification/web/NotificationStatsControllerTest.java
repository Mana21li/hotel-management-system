package com.hotelbooking.notification.web;

import com.hotelbooking.notification.kafka.NotificationBookingConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationStatsController.class)
class NotificationStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationBookingConsumer notificationBookingConsumer;

    @Test
    void stats_returnsProcessedAndFailureCounts() throws Exception {
        when(notificationBookingConsumer.getProcessedCount()).thenReturn(3);
        when(notificationBookingConsumer.getFailureCount()).thenReturn(1);

        mockMvc.perform(get("/internal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(1));
    }
}
