package com.hotelbooking.search.web;

import com.hotelbooking.search.kafka.HotelSearchEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchStatsController.class)
class SearchStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelSearchEventConsumer hotelSearchEventConsumer;

    @Test
    void stats_returnsProcessedCount() throws Exception {
        when(hotelSearchEventConsumer.getProcessedCount()).thenReturn(4);

        mockMvc.perform(get("/internal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(4));
    }
}
