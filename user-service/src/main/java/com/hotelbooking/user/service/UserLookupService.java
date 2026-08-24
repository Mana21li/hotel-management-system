package com.hotelbooking.user.service;

import com.hotelbooking.user.dto.UserSummaryResponse;
import com.hotelbooking.user.entity.User;
import com.hotelbooking.user.exception.UserNotFoundException;
import com.hotelbooking.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserLookupService {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserSummaryResponse getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return new UserSummaryResponse(user.getId(), user.getFullName(), user.isActive());
    }
}
