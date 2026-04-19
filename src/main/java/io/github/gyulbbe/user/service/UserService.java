package io.github.gyulbbe.user.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.UserDetailDto;
import io.github.gyulbbe.user.dto.UserDto;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private static final String ACTIVE = "ACTIVE";

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
                .userType("ROLE_USER")
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
                    .userType("ROLE_USER")
                    .photo("default.jpg")
                    .coin(1000L)
                    .build();
            userEntityList.add(user);
        }

        userRepository.saveAll(userEntityList);
        return ResponseDto.success(null);
    }

    public ResponseDto<Void> updatePassword(Long id, String newPassword) {
        UserEntity user = userRepository.findById(id)
                .orElse(null);
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
}
