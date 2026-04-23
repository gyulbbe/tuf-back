package io.github.gyulbbe.user.repository;

import io.github.gyulbbe.user.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUserIdIgnoreCase(String userId);

    boolean existsByUserIdIgnoreCase(String userId);

    @Query("""
            select u
            from UserEntity u
            where upper(u.userId) = upper(:userId)
              and u.status = :status
            """)
    UserEntity findByUserIdIgnoreCaseAndStatus(@Param("userId") String userId, @Param("status") String status);

    @Query("""
            select u
            from UserEntity u
            where u.status = :status
              and upper(u.userId) like concat('%', upper(:keyword), '%')
            order by
              case when upper(u.userId) like concat(upper(:keyword), '%') then 0 else 1 end,
              upper(u.userId) asc
            """)
    List<UserEntity> searchByUserIdKeyword(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            select u
            from UserEntity u
            where (:status = 'ALL' or upper(u.status) = :status)
              and (:keyword = '' or upper(u.userId) like concat('%', upper(:keyword), '%'))
            order by
              case
                when :keyword <> '' and upper(u.userId) like concat(upper(:keyword), '%') then 0
                else 1
              end,
              upper(u.userId) asc
            """)
    List<UserEntity> searchAdminUsers(
            @Param("keyword") String keyword,
            @Param("status") String status
    );
}
