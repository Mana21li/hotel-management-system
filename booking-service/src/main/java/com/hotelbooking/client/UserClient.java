package com.hotelbooking.client;

import com.hotelbooking.exception.UserNotFoundException;
import com.hotelbooking.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sync user lookups from user-service after users table moved out of hotel_booking.
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@Value("${user.service.base-url:http://127.0.0.1:8088}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public UserSummaryDto getActiveUser(Long userId) {
        try {
            return restClient.get()
                    .uri("/internal/users/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new UserNotFoundException(userId);
                    })
                    .body(UserSummaryDto.class);
        } catch (UserNotFoundException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new UserNotFoundException(userId);
            }
            throw new UserServiceUnavailableException("User service rejected request: " + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            throw new UserServiceUnavailableException("User service unreachable", ex);
        }
    }
}
