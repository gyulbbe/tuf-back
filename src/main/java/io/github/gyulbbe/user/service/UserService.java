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

    public ResponseDto<Void> insertUser(UserDto userDto) {
        UserEntity user = new UserEntity();
        user.setUserId(userDto.getUserId());
        user.setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()));
        user.setTier(userDto.getTier());
        user.setRace(userDto.getRace());
        user.setStatus("ACTIVE");
        user.setPoint(1000L);
        user.setUserType("USER");
        user.setPhoto("default.jpg");
        userRepository.save(user);
        return ResponseDto.success(null);
    }

    public ResponseDto<Void> insertUserList(List<UserDto> userList) {
        if (userList == null || userList.isEmpty()) {
            return ResponseDto.fail("사용자 리스트가 비어있습니다.");
        }

        List<UserEntity> userEntityList = new ArrayList<>();
        for(UserDto userDto : userList) {
            UserEntity user = new UserEntity();
            user.setUserId(userDto.getUserId());
            user.setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()));
            user.setTier(userDto.getTier());
            user.setRace(userDto.getRace());
            user.setStatus("ACTIVE");
            user.setUserType("USER");
            user.setPhoto("default.jpg");
            user.setPoint(1000L);
            userEntityList.add(user);
        }

        userRepository.saveAll(userEntityList);
        return ResponseDto.success(null);
    }

    public UserDetailDto getUserDetail(String userId) {
        UserDetailDto userDetailDto = new UserDetailDto();
        UserEntity user = userRepository.findByUserIdAndStatus(userId, "ACTIVE");
        userDetailDto.setUserId(user.getUserId());
        userDetailDto.setName(user.getName());
        userDetailDto.setRace(user.getRace());
        userDetailDto.setTier(user.getTier());
        userDetailDto.setBattleTag(user.getBattleTag());
        userDetailDto.setWin(0);
        userDetailDto.setLose(0);
        return userDetailDto;
    }
}
