package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftTeamOperatorResponseDto;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorEntity;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorId;
import io.github.gyulbbe.draft.repository.DraftTeamOperatorRepository;
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
    private final DraftTeamOperatorRepository draftTeamOperatorRepository;
    private final DraftPermissionService draftPermissionService;
    private final UserRepository userRepository;

    public ResponseDto<DraftTeamOperatorResponseDto> assignPicker(Long teamId, Long operatorUserId, AuthActor actor) {
        try {
            draftPermissionService.assertAdmin(actor);

            DraftTeamEntity team = draftTeamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("드래프트 팀을 찾을 수 없습니다."));

            DraftTeamOperatorEntity target = draftTeamOperatorRepository
                    .findById(new DraftTeamOperatorId(teamId, operatorUserId))
                    .orElseThrow(() -> new IllegalArgumentException("팀 운영자를 찾을 수 없습니다."));

            if (!"Y".equals(target.getIsActive())) {
                throw new IllegalArgumentException("비활성 운영자는 픽 권한자로 지정할 수 없습니다.");
            }
            if (!"CAPTAIN".equals(target.getRole()) && !"VICE_CAPTAIN".equals(target.getRole())) {
                throw new IllegalArgumentException("팀장 또는 부팀장만 픽 권한자로 지정할 수 있습니다.");
            }

            DraftTeamOperatorEntity existingPicker = draftPermissionService.getCurrentPicker(teamId);
            if (existingPicker != null && existingPicker.getOperatorUserId().equals(operatorUserId)) {
                return ResponseDto.success(toResponse(target));
            }

            if (existingPicker != null) {
                existingPicker.clearPicker();
            }

            target.assignPicker();
            return ResponseDto.success(toResponse(target));
        } catch (Exception e) {
            log.error("드래프트 픽 권한자 지정 실패. teamId={}, operatorUserId={}", teamId, operatorUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private DraftTeamOperatorResponseDto toResponse(DraftTeamOperatorEntity entity) {
        UserEntity user = userRepository.findById(entity.getOperatorUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        DraftTeamOperatorResponseDto responseDto = new DraftTeamOperatorResponseDto();
        responseDto.setDraftTeamId(entity.getDraftTeamId());
        responseDto.setOperatorUserId(entity.getOperatorUserId());
        responseDto.setOperatorName(user.getName());
        responseDto.setRole(entity.getRole());
        responseDto.setIsActive(entity.getIsActive());
        responseDto.setCanPick(entity.getCanPick());
        return responseDto;
    }
}
