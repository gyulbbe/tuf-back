package io.github.gyulbbe.user.repository;

import io.github.gyulbbe.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUserIdAndStatus(String userId, String status);
}
