package io.github.gyulbbe.user.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.UserAdminCreateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminRoleUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminResponseDto;
import io.github.gyulbbe.user.dto.UserAdminStatusUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminUpdateRequestDto;
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
@Import({UserService.class, UserAdminServiceTest.TestConfig.class})
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:useradmindb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserAdminServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Test
    void searchAdminUsers_filtersByStatusAndKeywordIgnoringCase() {
        saveUser("blackmagic", "Black", "ACTIVE");
        saveUser("BlueDragon", "Blue", "ACTIVE");
        saveUser("blackout", "Inactive Black", "INACTIVE");
        saveUser("white", "White", "ACTIVE");

        ResponseDto<List<UserAdminResponseDto>> response = userService.searchAdminUsers("bl", "ALL");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData())
                .extracting(UserAdminResponseDto::getUserId)
                .containsExactly("blackmagic", "blackout", "BlueDragon");
        assertThat(response.getData())
                .extracting(UserAdminResponseDto::getStatus)
                .containsExactly("ACTIVE", "INACTIVE", "ACTIVE");
    }

    @Test
    void createAdminUser_returnsConflictWhenUserIdAlreadyExistsIgnoringCase() {
        saveUser("blackmagic", "Black", "ACTIVE");

        UserAdminCreateRequestDto requestDto = new UserAdminCreateRequestDto();
        requestDto.setUserId("BlackMagic");
        requestDto.setPassword("secret");
        requestDto.setName("Another");
        requestDto.setRace("T");
        requestDto.setTier("S");

        ResponseDto<UserAdminResponseDto> response = userService.createAdminUser(requestDto);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("이미 사용 중인 userId입니다.");
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("CONFLICT");
    }

    @Test
    void createAdminUser_savesActiveUserAndReturnsCreatedData() {
        UserAdminCreateRequestDto requestDto = new UserAdminCreateRequestDto();
        requestDto.setUserId("newuser");
        requestDto.setPassword("secret");
        requestDto.setName("New User");
        requestDto.setRace("P");
        requestDto.setTier("A");

        ResponseDto<UserAdminResponseDto> response = userService.createAdminUser(requestDto);
        UserEntity savedUser = userRepository.findByUserIdIgnoreCase("newuser");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getUserId()).isEqualTo("newuser");
        assertThat(response.getData().getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getData().getUserType()).isEqualTo("ROLE_USER");
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(bCryptPasswordEncoder.matches("secret", savedUser.getPassword())).isTrue();
    }

    @Test
    void updateAdminUser_returnsConflictWhenAnotherUserAlreadyUsesUserId() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();
        saveUser("target", "Target", "ACTIVE");

        UserAdminUpdateRequestDto requestDto = new UserAdminUpdateRequestDto();
        requestDto.setUserId("target");
        requestDto.setName("Updated");
        requestDto.setRace("Z");
        requestDto.setTier("S");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUser(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("이미 사용 중인 userId입니다.");
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("CONFLICT");
    }

    @Test
    void updateAdminUser_updatesProfileAndUserTypeTogether() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();

        UserAdminUpdateRequestDto requestDto = new UserAdminUpdateRequestDto();
        requestDto.setUserId("updated");
        requestDto.setName("Updated");
        requestDto.setRace("Z");
        requestDto.setTier("S");
        requestDto.setRole("ROLE_MANAGER");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUser(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getUserId()).isEqualTo("updated");
        assertThat(response.getData().getUserType()).isEqualTo("ROLE_MANAGER");
        UserEntity savedUser = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser.getUserId()).isEqualTo("updated");
        assertThat(savedUser.getUserType()).isEqualTo("ROLE_MANAGER");
    }

    @Test
    void updateAdminUser_rejectsUnsupportedUserTypeWithoutChangingProfile() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();

        UserAdminUpdateRequestDto requestDto = new UserAdminUpdateRequestDto();
        requestDto.setUserId("updated");
        requestDto.setName("Updated");
        requestDto.setRace("Z");
        requestDto.setTier("S");
        requestDto.setRole("ROLE_ROOT");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUser(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).contains("userType must be one of");
        UserEntity savedUser = userRepository.findById(userId).orElseThrow();
        assertThat(savedUser.getUserId()).isEqualTo("blackmagic");
        assertThat(savedUser.getUserType()).isEqualTo("ROLE_USER");
    }

    @Test
    void updateAdminUserStatus_updatesInactiveStatus() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();
        UserAdminStatusUpdateRequestDto requestDto = new UserAdminStatusUpdateRequestDto();
        requestDto.setStatus("inactive");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUserStatus(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getStatus()).isEqualTo("INACTIVE");
        assertThat(userRepository.findById(userId)).isPresent();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void updateAdminUserRole_updatesUserType() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();
        UserAdminRoleUpdateRequestDto requestDto = new UserAdminRoleUpdateRequestDto();
        requestDto.setUserType("admin");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUserRole(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getUserType()).isEqualTo("ROLE_ADMIN");
        assertThat(userRepository.findById(userId)).isPresent();
        assertThat(userRepository.findById(userId).orElseThrow().getUserType()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void updateAdminUserRole_rejectsUnsupportedUserType() {
        Long userId = saveUser("blackmagic", "Black", "ACTIVE").getId();
        UserAdminRoleUpdateRequestDto requestDto = new UserAdminRoleUpdateRequestDto();
        requestDto.setUserType("ROLE_ROOT");

        ResponseDto<UserAdminResponseDto> response = userService.updateAdminUserRole(userId, requestDto);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).contains("userType must be one of");
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(userRepository.findById(userId).orElseThrow().getUserType()).isEqualTo("ROLE_USER");
    }

    private UserEntity saveUser(String userId, String name, String status) {
        return userRepository.save(UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .tier("A")
                .race("P")
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
