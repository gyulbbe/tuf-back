package io.github.gyulbbe.user.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.RegisterUserDto;
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

    public ResponseDto<Void> insertUser(RegisterUserDto registerUserDto) {
        System.out.println(registerUserDto);
        UserEntity user = new UserEntity();
        user.setUserId(registerUserDto.getUserId());
        user.setPassword(bCryptPasswordEncoder.encode(registerUserDto.getPassword()));
        user.setTier(registerUserDto.getTier());
        user.setRace(registerUserDto.getRace());
        user.setStatus("ACTIVE");
        user.setPoint(1000L);
        user.setUserType("USER");
        user.setPhoto("default.jpg");
        userRepository.save(user);
        return ResponseDto.success(null);
    }

    public ResponseDto<Void> insertUserList(List<RegisterUserDto> userList) {
        if (userList == null || userList.isEmpty()) {
            return ResponseDto.fail("사용자 리스트가 비어있습니다.");
        }

        List<UserEntity> userEntityList = new ArrayList<>();
        for(RegisterUserDto registerUserDto : userList) {
            UserEntity user = new UserEntity();
            user.setUserId(registerUserDto.getUserId());
            user.setPassword(bCryptPasswordEncoder.encode(registerUserDto.getPassword()));
            user.setTier(registerUserDto.getTier());
            user.setRace(registerUserDto.getRace());
            user.setStatus("ACTIVE");
            user.setUserType("USER");
            user.setPhoto("default.jpg");
            user.setPoint(1000L);
            userEntityList.add(user);
        }

        userRepository.saveAll(userEntityList);
        return ResponseDto.success(null);
    }
}
