package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickerResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
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
public class RpsDraftAdminService {

    private final RpsDraftSessionRepository rpsDraftSessionRepository;
    private final RpsDraftTeamRepository rpsDraftTeamRepository;
    private final RpsDraftPermissionService rpsDraftPermissionService;
    private final UserRepository userRepository;

    public ResponseDto<RpsDraftPickerResponseDto> assignPicker(
            Long sessionId,
            Long teamId,
            Long pickerUserId,
            RpsDraftActor actor
    ) {
        try {
            if (pickerUserId == null) {
                throw new IllegalArgumentException("Picker user id is required.");
            }

            RpsDraftSessionEntity session = rpsDraftSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("RPS draft session could not be found."));
            rpsDraftPermissionService.assertOwner(session, actor);

            if (!RpsDraftSessionEntity.STATUS_READY.equals(session.getStatus())) {
                throw new IllegalArgumentException("Pickers can only be assigned while the session is READY.");
            }

            RpsDraftTeamEntity team = rpsDraftTeamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("RPS draft team could not be found."));
            if (!team.getRpsDraftSessionId().equals(sessionId)) {
                throw new IllegalArgumentException("Team does not belong to the session.");
            }

            UserEntity picker = userRepository.findById(pickerUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Picker user could not be found."));
            if (!"ACTIVE".equals(picker.getStatus())) {
                throw new IllegalArgumentException("Only ACTIVE users can be assigned as pickers.");
            }

            rpsDraftTeamRepository.findByRpsDraftSessionIdAndPickerUserId(sessionId, pickerUserId)
                    .filter(existing -> !existing.getId().equals(teamId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Picker user is already assigned to another team in this session.");
                    });

            team.assignPicker(pickerUserId);

            RpsDraftPickerResponseDto response = new RpsDraftPickerResponseDto();
            response.setRpsDraftTeamId(team.getId());
            response.setPickerUserId(picker.getId());
            response.setPickerName(picker.getName());
            return ResponseDto.success(response);
        } catch (Exception e) {
            log.error("Failed to assign RPS draft picker.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }
}
