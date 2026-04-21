package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamResponseDto;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftCandidateId;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftOrderId;
import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftPickId;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftQueryRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DraftService {

    private static final Set<String> SESSION_STATUSES = Set.of("READY", "LIVE", "PAUSED", "FINISHED", "CANCELLED");
    private static final Set<String> DRAFT_MODES = Set.of(
            DraftSessionEntity.MODE_FIXED_ORDER,
            DraftSessionEntity.MODE_MANUAL_CAPTAIN
    );
    private static final Set<String> RACES = Set.of("ZERG", "TERRAN", "PROTOSS", "RANDOM");
    private static final Set<String> CANDIDATE_STATUSES = Set.of("WAITING", "PICKED", "SKIPPED", "EXCLUDED");

    private final DraftSessionRepository draftSessionRepository;
    private final DraftTeamRepository draftTeamRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftPickRepository draftPickRepository;
    private final DraftQueryRepository draftQueryRepository;
    private final UserRepository userRepository;
    private final DraftLiveSessionTracker draftLiveSessionTracker;

    public ResponseDto<DraftSessionSummaryResponseDto> createSession(DraftSessionRequestDto requestDto) {
        try {
            validateSessionRequest(requestDto, false);

            DraftSessionEntity entity = DraftSessionEntity.builder()
                    .title(requestDto.getTitle())
                    .status(defaultIfBlank(requestDto.getStatus(), "READY"))
                    .teamCount(requestDto.getTeamCount())
                    .pickTimeSeconds(requestDto.getPickTimeSeconds())
                    .draftMode(defaultIfBlank(requestDto.getDraftMode(), DraftSessionEntity.MODE_FIXED_ORDER))
                    .currentPickNo(requestDto.getCurrentPickNo() != null ? requestDto.getCurrentPickNo() : 1)
                    .currentDraftTeamId(null)
                    .deadlineAt(requestDto.getDeadlineAt())
                    .startedAt(requestDto.getStartedAt())
                    .endedAt(requestDto.getEndedAt())
                    .build();

            DraftSessionEntity saved = draftSessionRepository.save(entity);
            if ("LIVE".equals(saved.getStatus())) {
                draftLiveSessionTracker.markLiveSessionPresentAfterCommit();
            }
            return ResponseDto.success(requireSessionSummary(saved.getId()));
        } catch (Exception e) {
            log.error("드래프트 세션 생성 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftSessionDetailResponseDto> getSession(Long sessionId) {
        try {
            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("드래프트 세션 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftSessionSummaryResponseDto>> listSessions() {
        try {
            return ResponseDto.success(draftQueryRepository.findSessionSummaries());
        } catch (Exception e) {
            log.error("드래프트 세션 목록 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionSummaryResponseDto> updateSession(Long sessionId, DraftSessionRequestDto requestDto) {
        try {
            DraftSessionEntity entity = getSessionEntity(sessionId);
            validateSessionRequest(requestDto, true);

            Long currentDraftTeamId = requestDto.getCurrentDraftTeamId();
            if (currentDraftTeamId != null && !draftTeamRepository.existsByIdAndDraftSessionId(currentDraftTeamId, sessionId)) {
                throw new IllegalArgumentException("현재 드래프트 팀은 같은 세션 소속이어야 합니다.");
            }

            entity.update(
                    defaultIfBlank(requestDto.getTitle(), entity.getTitle()),
                    defaultIfBlank(requestDto.getStatus(), entity.getStatus()),
                    requestDto.getTeamCount() != null ? requestDto.getTeamCount() : entity.getTeamCount(),
                    requestDto.getPickTimeSeconds() != null ? requestDto.getPickTimeSeconds() : entity.getPickTimeSeconds(),
                    defaultIfBlank(requestDto.getDraftMode(), entity.getDraftMode()),
                    requestDto.getCurrentPickNo() != null ? requestDto.getCurrentPickNo() : entity.getCurrentPickNo(),
                    requestDto.getCurrentDraftTeamId() != null ? requestDto.getCurrentDraftTeamId() : entity.getCurrentDraftTeamId(),
                    requestDto.getDeadlineAt() != null ? requestDto.getDeadlineAt() : entity.getDeadlineAt(),
                    requestDto.getStartedAt() != null ? requestDto.getStartedAt() : entity.getStartedAt(),
                    requestDto.getEndedAt() != null ? requestDto.getEndedAt() : entity.getEndedAt()
            );

            if ("LIVE".equals(entity.getStatus())) {
                draftLiveSessionTracker.markLiveSessionPresentAfterCommit();
            } else {
                draftLiveSessionTracker.refreshAfterCommit();
            }

            return ResponseDto.success(requireSessionSummary(sessionId));
        } catch (Exception e) {
            log.error("드래프트 세션 수정 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteSession(Long sessionId) {
        try {
            DraftSessionEntity session = getSessionEntityForUpdate(sessionId);
            DraftSessionDeleteStats deleteStats = collectDeleteStats(session);
            log.info(
                    "Deleting draft session. sessionId={}, status={}, currentDraftTeamId={}, picks={}, orders={}, candidates={}, teams={}",
                    deleteStats.sessionId(),
                    deleteStats.status(),
                    deleteStats.currentDraftTeamId(),
                    deleteStats.pickCount(),
                    deleteStats.orderCount(),
                    deleteStats.candidateCount(),
                    deleteStats.teamCount()
            );

            session.clearCurrentDraftTeam();
            draftSessionRepository.flush();

            int deletedPicks = draftPickRepository.deleteByDraftSessionId(sessionId);
            int deletedOrders = draftOrderRepository.deleteByDraftSessionId(sessionId);
            int deletedCandidates = draftCandidateRepository.deleteByDraftSessionId(sessionId);
            int deletedTeams = draftTeamRepository.deleteByDraftSessionId(sessionId);
            draftSessionRepository.delete(session);
            draftLiveSessionTracker.refreshAfterCommit();

            log.info(
                    "Deleted draft session. sessionId={}, deletedPicks={}, deletedOrders={}, deletedCandidates={}, deletedTeams={}",
                    sessionId,
                    deletedPicks,
                    deletedOrders,
                    deletedCandidates,
                    deletedTeams
            );

            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("드래프트 세션 삭제 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftTeamResponseDto> createTeam(DraftTeamRequestDto requestDto) {
        try {
            validateTeamRequest(requestDto);
            getSessionEntity(requestDto.getDraftSessionId());

            DraftTeamEntity entity = DraftTeamEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .teamName(requestDto.getTeamName())
                    .displayOrder(requestDto.getDisplayOrder())
                    .pickerUserId(null)
                    .build();

            DraftTeamEntity saved = draftTeamRepository.save(entity);
            return ResponseDto.success(requireTeam(saved.getId()));
        } catch (Exception e) {
            log.error("드래프트 팀 생성 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftTeamResponseDto> getTeam(Long teamId) {
        try {
            return ResponseDto.success(requireTeam(teamId));
        } catch (Exception e) {
            log.error("드래프트 팀 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftTeamResponseDto>> listTeams(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(loadTeams(sessionId));
        } catch (Exception e) {
            log.error("드래프트 팀 목록 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftTeamResponseDto> updateTeam(Long teamId, DraftTeamRequestDto requestDto) {
        try {
            DraftTeamEntity entity = getTeamEntity(teamId);
            if (requestDto.getDisplayOrder() != null && requestDto.getDisplayOrder() <= 0) {
                throw new IllegalArgumentException("팀 표시 순서는 1 이상이어야 합니다.");
            }

            entity.update(
                    defaultIfBlank(requestDto.getTeamName(), entity.getTeamName()),
                    requestDto.getDisplayOrder() != null ? requestDto.getDisplayOrder() : entity.getDisplayOrder()
            );

            return ResponseDto.success(requireTeam(teamId));
        } catch (Exception e) {
            log.error("드래프트 팀 수정 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteTeam(Long teamId) {
        try {
            DraftTeamEntity team = getTeamEntity(teamId);
            DraftSessionEntity session = getSessionEntity(team.getDraftSessionId());
            if (Objects.equals(session.getCurrentDraftTeamId(), teamId)) {
                throw new IllegalArgumentException("현재 차례 팀은 삭제할 수 없습니다.");
            }

            draftTeamRepository.delete(team);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("드래프트 팀 삭제 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftCandidateResponseDto> createCandidate(DraftCandidateRequestDto requestDto) {
        try {
            validateCandidateRequest(requestDto, false);
            getSessionEntity(requestDto.getDraftSessionId());
            getUserEntity(requestDto.getCandidateUserId());
            validatePickedTeamBelongsToSession(requestDto.getDraftSessionId(), requestDto.getPickedDraftTeamId());

            DraftCandidateEntity entity = DraftCandidateEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .candidateUserId(requestDto.getCandidateUserId())
                    .candidateName(requestDto.getCandidateName())
                    .race(requestDto.getRace())
                    .status(defaultIfBlank(requestDto.getStatus(), "WAITING"))
                    .pickedDraftTeamId(requestDto.getPickedDraftTeamId())
                    .pickedAt(requestDto.getPickedAt())
                    .build();

            draftCandidateRepository.save(entity);
            return ResponseDto.success(requireCandidate(requestDto.getDraftSessionId(), requestDto.getCandidateUserId()));
        } catch (Exception e) {
            log.error("드래프트 후보 생성 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftCandidateResponseDto> getCandidate(Long sessionId, Long candidateUserId) {
        try {
            return ResponseDto.success(requireCandidate(sessionId, candidateUserId));
        } catch (Exception e) {
            log.error("드래프트 후보 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftCandidateResponseDto>> listCandidates(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(draftQueryRepository.findCandidatesBySessionId(sessionId));
        } catch (Exception e) {
            log.error("드래프트 후보 목록 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftCandidateResponseDto> updateCandidate(Long sessionId, Long candidateUserId, DraftCandidateRequestDto requestDto) {
        try {
            DraftCandidateEntity entity = getCandidateEntity(sessionId, candidateUserId);
            validateCandidateRequest(requestDto, true);

            boolean pickedExists = draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId);
            if (pickedExists && (requestDto.getStatus() != null || requestDto.getPickedDraftTeamId() != null || requestDto.getPickedAt() != null)) {
                throw new IllegalArgumentException("이미 픽된 후보의 상태는 직접 수정할 수 없습니다.");
            }

            String status = requestDto.getStatus() != null ? requestDto.getStatus() : entity.getStatus();
            if (!pickedExists) {
                validateCandidateStatus(status);
                validatePickedTeamBelongsToSession(sessionId, requestDto.getPickedDraftTeamId());
            }

            entity.update(
                    defaultIfBlank(requestDto.getCandidateName(), entity.getCandidateName()),
                    requestDto.getRace() != null ? requestDto.getRace() : entity.getRace(),
                    status,
                    !pickedExists ? requestDto.getPickedDraftTeamId() : entity.getPickedDraftTeamId(),
                    !pickedExists ? requestDto.getPickedAt() : entity.getPickedAt()
            );

            return ResponseDto.success(requireCandidate(sessionId, candidateUserId));
        } catch (Exception e) {
            log.error("드래프트 후보 수정 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteCandidate(Long sessionId, Long candidateUserId) {
        try {
            DraftCandidateEntity entity = getCandidateEntity(sessionId, candidateUserId);
            if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
                throw new IllegalArgumentException("이미 픽된 후보는 삭제할 수 없습니다.");
            }
            draftCandidateRepository.delete(entity);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("드래프트 후보 삭제 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftOrderResponseDto> createOrder(DraftOrderRequestDto requestDto) {
        try {
            validateOrderRequest(requestDto);
            getSessionEntity(requestDto.getDraftSessionId());
            validateTeamBelongsToSession(requestDto.getDraftSessionId(), requestDto.getDraftTeamId());

            DraftOrderEntity entity = DraftOrderEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .pickNo(requestDto.getPickNo())
                    .draftTeamId(requestDto.getDraftTeamId())
                    .build();

            draftOrderRepository.save(entity);
            return ResponseDto.success(requireOrder(requestDto.getDraftSessionId(), requestDto.getPickNo()));
        } catch (Exception e) {
            log.error("드래프트 순서 생성 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftOrderResponseDto> getOrder(Long sessionId, Long pickNo) {
        try {
            return ResponseDto.success(requireOrder(sessionId, pickNo));
        } catch (Exception e) {
            log.error("드래프트 순서 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftOrderResponseDto>> listOrders(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(draftQueryRepository.findOrdersBySessionId(sessionId));
        } catch (Exception e) {
            log.error("드래프트 순서 목록 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftOrderResponseDto> updateOrder(Long sessionId, Long pickNo, DraftOrderRequestDto requestDto) {
        try {
            DraftOrderEntity entity = getOrderEntity(sessionId, pickNo);
            if (draftPickRepository.existsByDraftSessionIdAndPickNo(sessionId, pickNo)) {
                throw new IllegalArgumentException("이미 진행된 순서는 수정할 수 없습니다.");
            }

            Long draftTeamId = requestDto.getDraftTeamId() != null ? requestDto.getDraftTeamId() : entity.getDraftTeamId();

            validateTeamBelongsToSession(sessionId, draftTeamId);
            entity.update(draftTeamId);

            return ResponseDto.success(requireOrder(sessionId, pickNo));
        } catch (Exception e) {
            log.error("드래프트 순서 수정 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteOrder(Long sessionId, Long pickNo) {
        try {
            DraftOrderEntity entity = getOrderEntity(sessionId, pickNo);
            if (draftPickRepository.existsByDraftSessionIdAndPickNo(sessionId, pickNo)) {
                throw new IllegalArgumentException("이미 진행된 순서는 삭제할 수 없습니다.");
            }
            draftOrderRepository.delete(entity);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("드래프트 순서 삭제 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftPickResponseDto> createPick(DraftPickRequestDto requestDto) {
        try {
            validatePickRequest(requestDto);
            DraftSessionEntity session = getSessionEntity(requestDto.getDraftSessionId());
            validateTeamBelongsToSession(requestDto.getDraftSessionId(), requestDto.getDraftTeamId());
            DraftCandidateEntity candidate = getCandidateEntity(requestDto.getDraftSessionId(), requestDto.getCandidateUserId());
            getUserEntity(requestDto.getPickedByUserId());
            ensureCandidatePickable(candidate, requestDto.getDraftSessionId(), requestDto.getCandidateUserId());

            LocalDateTime pickedAt = requestDto.getPickedAt() != null ? requestDto.getPickedAt() : LocalDateTime.now();

            DraftPickEntity entity = DraftPickEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .pickNo(requestDto.getPickNo())
                    .draftTeamId(requestDto.getDraftTeamId())
                    .candidateUserId(requestDto.getCandidateUserId())
                    .pickedByUserId(requestDto.getPickedByUserId())
                    .pickedAt(pickedAt)
                    .build();

            draftPickRepository.save(entity);
            candidate.markPicked(requestDto.getDraftTeamId(), pickedAt);
            advanceSessionAfterPick(session, requestDto.getPickNo(), pickedAt);

            return ResponseDto.success(requirePick(requestDto.getDraftSessionId(), requestDto.getPickNo()));
        } catch (Exception e) {
            log.error("드래프트 픽 생성 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftPickResponseDto> getPick(Long sessionId, Long pickNo) {
        try {
            return ResponseDto.success(requirePick(sessionId, pickNo));
        } catch (Exception e) {
            log.error("드래프트 픽 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftPickResponseDto>> listPicks(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(draftQueryRepository.findPicksBySessionId(sessionId));
        } catch (Exception e) {
            log.error("드래프트 픽 목록 조회 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftPickResponseDto> updatePick(Long sessionId, Long pickNo, DraftPickRequestDto requestDto) {
        try {
            DraftPickEntity entity = getPickEntity(sessionId, pickNo);
            requestDto.setDraftSessionId(sessionId);
            requestDto.setPickNo(pickNo);
            validatePickRequest(requestDto);
            validateTeamBelongsToSession(sessionId, requestDto.getDraftTeamId());
            DraftCandidateEntity newCandidate = getCandidateEntity(sessionId, requestDto.getCandidateUserId());
            getUserEntity(requestDto.getPickedByUserId());

            Long previousCandidateUserId = entity.getCandidateUserId();
            if (!Objects.equals(previousCandidateUserId, requestDto.getCandidateUserId())) {
                ensureCandidatePickable(newCandidate, sessionId, requestDto.getCandidateUserId());
                DraftCandidateEntity previousCandidate = getCandidateEntity(sessionId, previousCandidateUserId);
                previousCandidate.resetToWaiting();
            }

            LocalDateTime pickedAt = requestDto.getPickedAt() != null ? requestDto.getPickedAt() : entity.getPickedAt();
            newCandidate.markPicked(requestDto.getDraftTeamId(), pickedAt);
            entity.update(
                    requestDto.getDraftTeamId(),
                    requestDto.getCandidateUserId(),
                    requestDto.getPickedByUserId(),
                    pickedAt
            );

            return ResponseDto.success(requirePick(sessionId, pickNo));
        } catch (Exception e) {
            log.error("드래프트 픽 수정 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deletePick(Long sessionId, Long pickNo) {
        try {
            DraftPickEntity entity = getPickEntity(sessionId, pickNo);
            DraftCandidateEntity candidate = getCandidateEntity(sessionId, entity.getCandidateUserId());
            draftPickRepository.delete(entity);
            candidate.resetToWaiting();
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("드래프트 픽 삭제 실패", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private DraftSessionDetailResponseDto buildSessionDetail(Long sessionId) {
        DraftSessionSummaryResponseDto summary = requireSessionSummary(sessionId);
        DraftSessionDetailResponseDto detail = new DraftSessionDetailResponseDto();
        detail.setId(summary.getId());
        detail.setTitle(summary.getTitle());
        detail.setStatus(summary.getStatus());
        detail.setTeamCount(summary.getTeamCount());
        detail.setPickTimeSeconds(summary.getPickTimeSeconds());
        detail.setDraftMode(summary.getDraftMode());
        detail.setCurrentPickNo(summary.getCurrentPickNo());
        detail.setCurrentDraftTeamId(summary.getCurrentDraftTeamId());
        detail.setDeadlineAt(summary.getDeadlineAt());
        detail.setStartedAt(summary.getStartedAt());
        detail.setEndedAt(summary.getEndedAt());
        detail.setTeams(loadTeams(sessionId));
        detail.setCandidates(draftQueryRepository.findCandidatesBySessionId(sessionId));
        detail.setOrders(draftQueryRepository.findOrdersBySessionId(sessionId));
        detail.setPicks(draftQueryRepository.findPicksBySessionId(sessionId));
        return detail;
    }

    private List<DraftTeamResponseDto> loadTeams(Long sessionId) {
        return draftQueryRepository.findTeamsBySessionId(sessionId);
    }

    private DraftSessionSummaryResponseDto requireSessionSummary(Long sessionId) {
        return draftQueryRepository.findSessionSummary(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 세션을 찾을 수 없습니다."));
    }

    private DraftSessionDeleteStats collectDeleteStats(DraftSessionEntity session) {
        Long sessionId = session.getId();
        return new DraftSessionDeleteStats(
                sessionId,
                session.getStatus(),
                session.getCurrentDraftTeamId(),
                draftTeamRepository.countByDraftSessionId(sessionId),
                draftCandidateRepository.countByDraftSessionId(sessionId),
                draftOrderRepository.countByDraftSessionId(sessionId),
                draftPickRepository.countByDraftSessionId(sessionId)
        );
    }

    private DraftTeamResponseDto requireTeam(Long teamId) {
        return draftQueryRepository.findTeam(teamId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 팀을 찾을 수 없습니다."));
    }

    private DraftCandidateResponseDto requireCandidate(Long sessionId, Long candidateUserId) {
        return draftQueryRepository.findCandidate(sessionId, candidateUserId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 후보를 찾을 수 없습니다."));
    }

    private DraftOrderResponseDto requireOrder(Long sessionId, Long pickNo) {
        return draftQueryRepository.findOrder(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 순서를 찾을 수 없습니다."));
    }

    private DraftPickResponseDto requirePick(Long sessionId, Long pickNo) {
        return draftQueryRepository.findPick(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 픽을 찾을 수 없습니다."));
    }

    private DraftSessionEntity getSessionEntity(Long sessionId) {
        return draftSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 세션을 찾을 수 없습니다."));
    }

    private DraftSessionEntity getSessionEntityForUpdate(Long sessionId) {
        return draftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 세션을 찾을 수 없습니다."));
    }

    private DraftTeamEntity getTeamEntity(Long teamId) {
        return draftTeamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 팀을 찾을 수 없습니다."));
    }

    private DraftCandidateEntity getCandidateEntity(Long sessionId, Long candidateUserId) {
        return draftCandidateRepository.findById(new DraftCandidateId(sessionId, candidateUserId))
                .orElseThrow(() -> new IllegalArgumentException("드래프트 후보를 찾을 수 없습니다."));
    }

    private DraftOrderEntity getOrderEntity(Long sessionId, Long pickNo) {
        return draftOrderRepository.findById(new DraftOrderId(sessionId, pickNo))
                .orElseThrow(() -> new IllegalArgumentException("드래프트 순서를 찾을 수 없습니다."));
    }

    private DraftPickEntity getPickEntity(Long sessionId, Long pickNo) {
        return draftPickRepository.findById(new DraftPickId(sessionId, pickNo))
                .orElseThrow(() -> new IllegalArgumentException("드래프트 픽을 찾을 수 없습니다."));
    }

    private UserEntity getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
    }

    private void validateSessionRequest(DraftSessionRequestDto requestDto, boolean allowPartial) {
        if (!allowPartial || requestDto.getTitle() != null) {
            validateText(requestDto.getTitle(), "세션 제목");
        }
        if (!allowPartial || requestDto.getTeamCount() != null) {
            validatePositive(requestDto.getTeamCount(), "팀 수");
            if (requestDto.getTeamCount() != null && requestDto.getTeamCount() <= 1) {
                throw new IllegalArgumentException("팀 수는 2 이상이어야 합니다.");
            }
        }
        if (!allowPartial || requestDto.getPickTimeSeconds() != null) {
            validatePositive(requestDto.getPickTimeSeconds(), "픽 제한 시간");
        }
        if (requestDto.getCurrentPickNo() != null) {
            validatePositive(requestDto.getCurrentPickNo(), "현재 픽 순번");
        }
        if (requestDto.getStatus() != null) {
            validateSessionStatus(requestDto.getStatus());
        }
        if (requestDto.getDraftMode() != null) {
            validateDraftMode(requestDto.getDraftMode());
        }
    }

    private void validateTeamRequest(DraftTeamRequestDto requestDto) {
        if (requestDto.getDraftSessionId() == null) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }
        validateText(requestDto.getTeamName(), "팀 이름");
        validatePositive(requestDto.getDisplayOrder(), "팀 표시 순서");
    }

    private void validateCandidateRequest(DraftCandidateRequestDto requestDto, boolean allowPartial) {
        if (!allowPartial) {
            if (requestDto.getDraftSessionId() == null || requestDto.getCandidateUserId() == null) {
                throw new IllegalArgumentException("세션 ID와 후보 유저 ID는 필수입니다.");
            }
            validateText(requestDto.getCandidateName(), "후보 이름");
            validateRace(requestDto.getRace());
        } else {
            if (requestDto.getCandidateName() != null) {
                validateText(requestDto.getCandidateName(), "후보 이름");
            }
            if (requestDto.getRace() != null) {
                validateRace(requestDto.getRace());
            }
        }
        if (requestDto.getStatus() != null) {
            validateCandidateStatus(requestDto.getStatus());
        }
    }

    private void validateOrderRequest(DraftOrderRequestDto requestDto) {
        if (requestDto.getDraftSessionId() == null || requestDto.getPickNo() == null || requestDto.getDraftTeamId() == null) {
            throw new IllegalArgumentException("세션 ID, 픽 번호, 팀 ID는 필수입니다.");
        }
        validatePositive(requestDto.getPickNo(), "픽 번호");
    }

    private void validatePickRequest(DraftPickRequestDto requestDto) {
        if (requestDto.getDraftSessionId() == null || requestDto.getPickNo() == null || requestDto.getDraftTeamId() == null
                || requestDto.getCandidateUserId() == null || requestDto.getPickedByUserId() == null) {
            throw new IllegalArgumentException("세션 ID, 픽 번호, 팀 ID, 후보 유저 ID, 픽한 유저 ID는 필수입니다.");
        }
        validatePositive(requestDto.getPickNo(), "픽 번호");
        getOrderEntity(requestDto.getDraftSessionId(), requestDto.getPickNo());
    }

    private void ensureCandidatePickable(DraftCandidateEntity candidate, Long sessionId, Long candidateUserId) {
        if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
            throw new IllegalArgumentException("이미 픽된 후보입니다.");
        }
        if (!"WAITING".equals(candidate.getStatus())) {
            throw new IllegalArgumentException("대기 상태 후보만 픽할 수 있습니다.");
        }
    }

    private void advanceSessionAfterPick(DraftSessionEntity session, Long currentPickNo, LocalDateTime pickedAt) {
        if (session.usesManualCaptainMode()) {
            long waitingCandidates = draftCandidateRepository.countByDraftSessionIdAndStatus(session.getId(), "WAITING");
            if (waitingCandidates <= 0) {
                session.finish(pickedAt);
                return;
            }
            session.waitForNextManualTurn(currentPickNo.intValue() + 1);
            return;
        }

        long nextPickNo = currentPickNo + 1;
        Optional<DraftOrderEntity> nextOrder = draftOrderRepository.findById(new DraftOrderId(session.getId(), nextPickNo));
        if (nextOrder.isPresent()) {
            session.advanceTurn((int) nextPickNo, nextOrder.get().getDraftTeamId(), pickedAt.plusSeconds(session.getPickTimeSeconds()));
            return;
        }
        session.finish(pickedAt);
    }

    private void validatePickedTeamBelongsToSession(Long sessionId, Long draftTeamId) {
        if (draftTeamId != null) {
            validateTeamBelongsToSession(sessionId, draftTeamId);
        }
    }

    private void validateTeamBelongsToSession(Long sessionId, Long draftTeamId) {
        if (!draftTeamRepository.existsByIdAndDraftSessionId(draftTeamId, sessionId)) {
            throw new IllegalArgumentException("세션에 속한 팀이 아닙니다.");
        }
    }

    private void validateSessionStatus(String status) {
        validateAllowed(status, SESSION_STATUSES, "세션 상태");
    }

    private void validateDraftMode(String draftMode) {
        validateAllowed(draftMode, DRAFT_MODES, "드래프트 모드");
    }

    private void validateRace(String race) {
        validateAllowed(race, RACES, "종족");
    }

    private void validateCandidateStatus(String status) {
        validateAllowed(status, CANDIDATE_STATUSES, "후보 상태");
    }

    private void validateAllowed(String value, Set<String> allowed, String fieldName) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(fieldName + " 값이 올바르지 않습니다.");
        }
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }

    private void validatePositive(Number number, String fieldName) {
        if (number == null || number.longValue() <= 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 1 이상이어야 합니다.");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record DraftSessionDeleteStats(
            Long sessionId,
            String status,
            Long currentDraftTeamId,
            long teamCount,
            long candidateCount,
            long orderCount,
            long pickCount
    ) {
    }
}
