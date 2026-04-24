package io.github.gyulbbe.user.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.DraftUserSearchDto;
import io.github.gyulbbe.user.dto.UserAdminCreateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminRoleUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminResponseDto;
import io.github.gyulbbe.user.dto.UserAdminStatusUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserDetailDto;
import io.github.gyulbbe.user.dto.UserDto;
import io.github.gyulbbe.user.dto.UserSearchDto;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@AllArgsConstructor
@Slf4j
public class UserService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String ALL = "ALL";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_MANAGER = "ROLE_MANAGER";
    private static final String ROLE_MASTER = "ROLE_MASTER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final Set<String> ADMIN_STATUSES = Set.of(ACTIVE, INACTIVE, ALL);
    private static final Set<String> MANAGED_USER_TYPES = Set.of(ROLE_USER, ROLE_MANAGER, ROLE_MASTER, ROLE_ADMIN);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public ResponseDto<Void> insertUser(UserDto userDto) {
        UserEntity user = UserEntity.builder()
                .userId(userDto.getUserId())
                .password(bCryptPasswordEncoder.encode(userDto.getPassword()))
                .tier(userDto.getTier())
                .name(userDto.getName())
                .phone(userDto.getPhone())
                .race(userDto.getRace())
                .status(ACTIVE)
                .coin(1000L)
                .userType(ROLE_USER)
                .photo("default.jpg")
                .build();
        userRepository.save(user);
        return ResponseDto.success(null);
    }

    public ResponseDto<Void> insertUserList(List<UserDto> userList) {
        if (userList == null || userList.isEmpty()) {
            return ResponseDto.fail("사용자 리스트가 비어있습니다.");
        }

        List<UserEntity> userEntityList = new ArrayList<>();
        for (UserDto userDto : userList) {
            UserEntity user = UserEntity.builder()
                    .userId(userDto.getUserId())
                    .password(bCryptPasswordEncoder.encode(userDto.getPassword()))
                    .tier(userDto.getTier())
                    .name(userDto.getName())
                    .phone(userDto.getPhone())
                    .race(userDto.getRace())
                    .status(ACTIVE)
                    .userType(ROLE_USER)
                    .photo("default.jpg")
                    .coin(1000L)
                    .build();
            userEntityList.add(user);
        }

        userRepository.saveAll(userEntityList);
        return ResponseDto.success(null);
    }

    public ResponseDto<Void> updatePassword(Long id, String newPassword) {
        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseDto.fail("해당 사용자를 찾을 수 없습니다.");
        }
        user.updatePassword(bCryptPasswordEncoder.encode(newPassword));
        return ResponseDto.success(null);
    }

    public UserDetailDto getUserDetail(String userId) {
        UserDetailDto userDetailDto = new UserDetailDto();
        UserEntity user = userRepository.findByUserIdIgnoreCaseAndStatus(userId, ACTIVE);
        userDetailDto.setUserId(user.getUserId());
        userDetailDto.setName(user.getName());
        userDetailDto.setRace(user.getRace());
        userDetailDto.setTier(user.getTier());
        userDetailDto.setBattleTag(user.getBattleTag());
        userDetailDto.setCoin(user.getCoin());
        userDetailDto.setWin(0);
        userDetailDto.setLose(0);
        return userDetailDto;
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<UserSearchDto>> searchUsers(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return ResponseDto.success(List.of());
        }

        int resolvedLimit = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
        List<UserSearchDto> users = userRepository.searchByUserIdKeyword(
                        keyword.trim(),
                        ACTIVE,
                        PageRequest.of(0, resolvedLimit)
                ).stream()
                .map(this::toUserSearchDto)
                .toList();

        return ResponseDto.success(users);
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftUserSearchDto>> searchDraftUsers(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return ResponseDto.success(List.of());
        }

        int resolvedLimit = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
        List<DraftUserSearchDto> users = userRepository.searchByUserIdKeyword(
                        keyword.trim(),
                        ACTIVE,
                        PageRequest.of(0, resolvedLimit)
                ).stream()
                .map(this::toDraftUserSearchDto)
                .toList();

        return ResponseDto.success(users);
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<UserAdminResponseDto>> searchAdminUsers(String keyword, String status) {
        String normalizedStatus = normalizeAdminSearchStatus(status);
        if (normalizedStatus == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "status는 ACTIVE, INACTIVE, ALL 중 하나여야 합니다.");
        }

        List<UserAdminResponseDto> users = userRepository.searchAdminUsers(
                        normalizeKeyword(keyword),
                        normalizedStatus
                ).stream()
                .map(this::toUserAdminResponseDto)
                .toList();

        return ResponseDto.success(users);
    }

    public ResponseDto<UserAdminResponseDto> createAdminUser(UserAdminCreateRequestDto requestDto) {
        if (requestDto == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "요청 본문이 필요합니다.");
        }

        try {
            String userId = normalizeRequired(requestDto.getUserId(), "userId");
            String password = normalizeRequired(requestDto.getPassword(), "password");
            String name = normalizeRequired(requestDto.getName(), "name");
            String race = normalizeRequired(requestDto.getRace(), "race");
            String tier = normalizeRequired(requestDto.getTier(), "tier");

            if (userRepository.existsByUserIdIgnoreCase(userId)) {
                return ResponseDto.fail(HttpServletResponse.SC_CONFLICT, "이미 사용 중인 userId입니다.");
            }

            UserEntity savedUser = userRepository.save(UserEntity.builder()
                    .userId(userId)
                    .password(bCryptPasswordEncoder.encode(password))
                    .name(name)
                    .race(race)
                    .tier(tier)
                    .status(ACTIVE)
                    .coin(1000L)
                    .userType(ROLE_USER)
                    .photo("default.jpg")
                    .build());

            return ResponseDto.success(toUserAdminResponseDto(savedUser));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    public ResponseDto<UserAdminResponseDto> updateAdminUser(Long id, UserAdminUpdateRequestDto requestDto) {
        if (requestDto == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "요청 본문이 필요합니다.");
        }

        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }

        try {
            String userId = normalizeRequired(requestDto.getUserId(), "userId");
            String name = normalizeRequired(requestDto.getName(), "name");
            String race = normalizeRequired(requestDto.getRace(), "race");
            String tier = normalizeRequired(requestDto.getTier(), "tier");

            UserEntity existingUser = userRepository.findByUserIdIgnoreCase(userId);
            if (existingUser != null && !existingUser.getId().equals(id)) {
                return ResponseDto.fail(HttpServletResponse.SC_CONFLICT, "이미 사용 중인 userId입니다.");
            }

            user.updateAdminProfile(userId, name, race, tier);
            return ResponseDto.success(toUserAdminResponseDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    public ResponseDto<UserAdminResponseDto> updateAdminUserStatus(Long id, UserAdminStatusUpdateRequestDto requestDto) {
        if (requestDto == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "요청 본문이 필요합니다.");
        }

        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }

        String normalizedStatus = normalizeManagedUserStatus(requestDto.getStatus());
        if (normalizedStatus == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "status는 ACTIVE 또는 INACTIVE 중 하나여야 합니다.");
        }

        user.updateStatus(normalizedStatus);
        return ResponseDto.success(toUserAdminResponseDto(user));
    }

    public ResponseDto<UserAdminResponseDto> updateAdminUserRole(Long id, UserAdminRoleUpdateRequestDto requestDto) {
        if (requestDto == null) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "Request body is required.");
        }

        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "User could not be found.");
        }

        String normalizedUserType = normalizeManagedUserType(resolveRequestedUserType(requestDto));
        if (normalizedUserType == null) {
            return ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "userType must be one of ROLE_USER, ROLE_MANAGER, ROLE_MASTER, ROLE_ADMIN."
            );
        }

        user.updateUserType(normalizedUserType);
        return ResponseDto.success(toUserAdminResponseDto(user));
    }

    private UserSearchDto toUserSearchDto(UserEntity user) {
        UserSearchDto dto = new UserSearchDto();
        dto.setId(user.getId());
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setTier(user.getTier());
        dto.setRace(user.getRace());
        dto.setPhoto(user.getPhoto());
        return dto;
    }

    private DraftUserSearchDto toDraftUserSearchDto(UserEntity user) {
        DraftUserSearchDto dto = new DraftUserSearchDto();
        dto.setId(user.getId());
        dto.setUserId(user.getUserId());
        dto.setTier(user.getTier());
        dto.setRace(user.getRace());
        return dto;
    }

    private UserAdminResponseDto toUserAdminResponseDto(UserEntity user) {
        UserAdminResponseDto dto = new UserAdminResponseDto();
        dto.setId(user.getId());
        dto.setUserId(user.getUserId());
        dto.setName(user.getName());
        dto.setRace(user.getRace());
        dto.setTier(user.getTier());
        dto.setStatus(user.getStatus());
        dto.setUserType(user.getUserType());
        return dto;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String normalizeAdminSearchStatus(String status) {
        String normalizedStatus = status == null || status.isBlank()
                ? ALL
                : status.trim().toUpperCase();
        return ADMIN_STATUSES.contains(normalizedStatus) ? normalizedStatus : null;
    }

    private String normalizeManagedUserStatus(String status) {
        String normalizedStatus = status == null ? null : status.trim().toUpperCase();
        return ACTIVE.equals(normalizedStatus) || INACTIVE.equals(normalizedStatus) ? normalizedStatus : null;
    }

    private String resolveRequestedUserType(UserAdminRoleUpdateRequestDto requestDto) {
        if (requestDto.getUserType() != null && !requestDto.getUserType().isBlank()) {
            return requestDto.getUserType();
        }
        return requestDto.getRole();
    }

    private String normalizeManagedUserType(String userType) {
        if (userType == null || userType.isBlank()) {
            return null;
        }

        String normalizedUserType = userType.trim().toUpperCase();
        if (!normalizedUserType.startsWith("ROLE_")) {
            normalizedUserType = "ROLE_" + normalizedUserType;
        }
        return MANAGED_USER_TYPES.contains(normalizedUserType) ? normalizedUserType : null;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value.trim();
    }
}
