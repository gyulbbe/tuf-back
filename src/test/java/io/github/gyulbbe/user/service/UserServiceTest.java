package io.github.gyulbbe.user.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.UserSearchDto;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({UserService.class, UserServiceTest.TestConfig.class})
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:usersearchdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void userId_부분검색으로_active_유저목록을_반환한다() {
        saveUser("blackmagic", "Black", "ACTIVE");
        saveUser("BlueDragon", "Blue", "ACTIVE");
        saveUser("xblaster", "X", "ACTIVE");
        saveUser("blackout", "Inactive", "INACTIVE");

        ResponseDto<List<UserSearchDto>> response = userService.searchUsers("bl", 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData())
                .extracting(UserSearchDto::getUserId)
                .containsExactly("blackmagic", "BlueDragon", "xblaster");
    }

    @Test
    void 검색어가_비어있으면_빈목록을_반환한다() {
        ResponseDto<List<UserSearchDto>> response = userService.searchUsers("   ", 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void 검색결과는_limit_개수만큼만_반환한다() {
        saveUser("blackmagic", "Black", "ACTIVE");
        saveUser("BlueDragon", "Blue", "ACTIVE");
        saveUser("xblaster", "X", "ACTIVE");

        ResponseDto<List<UserSearchDto>> response = userService.searchUsers("bl", 2);

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData())
                .extracting(UserSearchDto::getUserId)
                .containsExactly("blackmagic", "BlueDragon");
    }

    private void saveUser(String userId, String name, String status) {
        userRepository.save(UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status(status)
                .userType("ROLE_USER")
                .photo("default.jpg")
                .coin(1000L)
                .build());
    }

    static class TestConfig {
        @Bean
        BCryptPasswordEncoder bCryptPasswordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
