package io.github.gyulbbe.user.repository;

import io.github.gyulbbe.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query("""
            select u
            from UserEntity u
            where upper(u.userId) = upper(:userId)
              and u.status = :status
            """)
    UserEntity findByUserIdIgnoreCaseAndStatus(@Param("userId") String userId, @Param("status") String status);
}
