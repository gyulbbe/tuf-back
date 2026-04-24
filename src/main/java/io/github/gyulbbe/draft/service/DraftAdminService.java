package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
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
    private final DraftSessionRepository draftSessionRepository;
    private final DraftPermissionService draftPermissionService;
    private final UserRepository userRepository;
    private final DraftService draftService;

    public ResponseDto<DraftSessionDetailResponseDto> assignPicker(Long teamId, Long pickerUserId, AuthActor actor) {
        try {
            if (pickerUserId == null) {
                throw new IllegalArgumentException("Picker user id is required.");
            }

            DraftTeamEntity team = draftTeamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Draft team could not be found."));
            DraftSessionEntity session = draftSessionRepository.findById(team.getDraftSessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
            draftPermissionService.assertOwnerOrAdmin(session, actor);

            UserEntity picker = userRepository.findById(pickerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User could not be found."));

            if (!"ACTIVE".equals(picker.getStatus())) {
                throw new IllegalArgumentException("Only ACTIVE users can be assigned as pickers.");
            }
            requireUserLoginId(picker, "Picker user's userId is required.");

            if (!pickerUserId.equals(team.getPickerUserId())) {
                team.assignPicker(pickerUserId);
            }

            return ResponseDto.success(draftService.requireSessionDetail(team.getDraftSessionId()));
        } catch (Exception e) {
            log.error("Failed to assign draft picker. teamId={}, pickerUserId={}", teamId, pickerUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private String requireUserLoginId(UserEntity user, String message) {
        if (user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId().trim();
        }
        throw new IllegalArgumentException(message);
    }
}
