package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftPickerResponseDto;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DraftAdminService {

    private final DraftTeamRepository draftTeamRepository;
    private final DraftPermissionService draftPermissionService;
    private final UserRepository userRepository;

    public ResponseDto<DraftPickerResponseDto> assignPicker(Long teamId, Long pickerUserId, AuthActor actor) {
        try {
            draftPermissionService.assertAdmin(actor);
            if (pickerUserId == null) {
                throw new IllegalArgumentException("픽커 사용자 ID는 필수입니다.");
            }

            DraftTeamEntity team = draftTeamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("드래프트 팀을 찾을 수 없습니다."));

            UserEntity picker = userRepository.findById(pickerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

            if (!"ACTIVE".equals(picker.getStatus())) {
                throw new IllegalArgumentException("ACTIVE 상태 유저만 픽커로 지정할 수 있습니다.");
            }

            if (!pickerUserId.equals(team.getPickerUserId())) {
                team.assignPicker(pickerUserId);
            }

            return ResponseDto.success(toResponse(teamId, picker));
        } catch (Exception e) {
            log.error("드래프트 픽커 지정 실패. teamId={}, pickerUserId={}", teamId, pickerUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private DraftPickerResponseDto toResponse(Long teamId, UserEntity picker) {
        DraftPickerResponseDto responseDto = new DraftPickerResponseDto();
        responseDto.setDraftTeamId(teamId);
        responseDto.setPickerUserId(picker.getId());
        responseDto.setPickerName(picker.getName());
        return responseDto;
    }
}
