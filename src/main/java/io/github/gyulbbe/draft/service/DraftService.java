package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderBulkReplaceRequestDto;
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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DraftService {

    private static final Set<String> SESSION_STATUSES = Set.of("READY", "LIVE", "PAUSED", "FINISHED", "CANCELLED");
    private static final Set<String> SUPPORTED_ORDER_MODES = Set.of("BASIC", "SNAKE");
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
    private final DraftPermissionService draftPermissionService;
    private final DraftOrderPatternService draftOrderPatternService;

    public ResponseDto<DraftSessionDetailResponseDto> createSession(DraftSessionRequestDto requestDto, AuthActor actor) {
        try {
            draftPermissionService.assertAuthenticated(actor);
            validateSessionRequest(requestDto, false);

            DraftSessionEntity entity = DraftSessionEntity.builder()
                    .title(requestDto.getTitle())
                    .ownerUserId(actor.userPk())
                    .status(defaultIfBlank(requestDto.getStatus(), "READY"))
                    .orderMode(resolveOrderMode(requestDto.getOrderMode(), "BASIC"))
                    .teamCount(requestDto.getTeamCount())
                    .pickTimeSeconds(requestDto.getPickTimeSeconds())
                    .currentPickNo(requestDto.getCurrentPickNo() != null ? requestDto.getCurrentPickNo() : 1)
                    .currentDraftTeamId(null)
                    .deadlineAt(requestDto.getDeadlineAt())
                    .startedAt(requestDto.getStartedAt())
                    .endedAt(requestDto.getEndedAt())
                    .build();

            DraftSessionEntity saved = draftSessionRepository.save(entity);
            createDefaultTeams(saved.getId(), saved.getTeamCount());
            if ("LIVE".equals(saved.getStatus())) {
                draftLiveSessionTracker.markLiveSessionPresentAfterCommit();
            }
            return ResponseDto.success(buildSessionDetail(saved.getId()));
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Failed to create draft session.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftSessionDetailResponseDto> getSession(Long sessionId) {
        try {
            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to get draft session.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public DraftSessionDetailResponseDto requireSessionDetail(Long sessionId) {
        return buildSessionDetail(sessionId);
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftSessionSummaryResponseDto>> listSessions() {
        try {
            return ResponseDto.success(draftQueryRepository.findSessionSummaries());
        } catch (Exception e) {
            log.error("Failed to list draft sessions.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> updateSession(Long sessionId, DraftSessionRequestDto requestDto, AuthActor actor) {
        try {
            DraftSessionEntity entity = getSessionEntityForUpdate(sessionId);
            draftPermissionService.assertOwnerOrAdmin(entity, actor);
            validateSessionRequest(requestDto, true);

            Long currentDraftTeamId = requestDto.getCurrentDraftTeamId();
            if (currentDraftTeamId != null && !draftTeamRepository.existsByIdAndDraftSessionId(currentDraftTeamId, sessionId)) {
                throw new IllegalArgumentException("Current draft team does not belong to this session.");
            }

            entity.update(
                    defaultIfBlank(requestDto.getTitle(), entity.getTitle()),
                    defaultIfBlank(requestDto.getStatus(), entity.getStatus()),
                    resolveOrderMode(requestDto.getOrderMode(), entity.getOrderMode()),
                    requestDto.getTeamCount() != null ? requestDto.getTeamCount() : entity.getTeamCount(),
                    requestDto.getPickTimeSeconds() != null ? requestDto.getPickTimeSeconds() : entity.getPickTimeSeconds(),
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

            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to update draft session. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteSession(Long sessionId, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntityForUpdate(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
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
            log.error("Failed to delete draft session. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftTeamResponseDto> createTeam(DraftTeamRequestDto requestDto, AuthActor actor) {
        try {
            validateTeamRequest(requestDto);
            DraftSessionEntity session = getSessionEntity(requestDto.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);

            Optional<DraftTeamEntity> defaultTeam = findReplaceableDefaultTeam(
                    requestDto.getDraftSessionId(),
                    requestDto.getDisplayOrder()
            );
            if (defaultTeam.isPresent()) {
                DraftTeamEntity entity = defaultTeam.get();
                entity.update(requestDto.getTeamName(), requestDto.getDisplayOrder());
                return ResponseDto.success(requireTeam(entity.getId()));
            }

            DraftTeamEntity entity = DraftTeamEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .teamName(requestDto.getTeamName())
                    .displayOrder(requestDto.getDisplayOrder())
                    .pickerUserId(null)
                    .build();

            DraftTeamEntity saved = draftTeamRepository.save(entity);
            return ResponseDto.success(requireTeam(saved.getId()));
        } catch (Exception e) {
            log.error("Failed to create draft team.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftTeamResponseDto> getTeam(Long teamId) {
        try {
            return ResponseDto.success(requireTeam(teamId));
        } catch (Exception e) {
            log.error("Failed to get draft team. teamId={}", teamId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftTeamResponseDto>> listTeams(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(loadTeams(sessionId));
        } catch (Exception e) {
            log.error("Failed to list draft teams. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> updateTeam(Long teamId, DraftTeamRequestDto requestDto, AuthActor actor) {
        try {
            DraftTeamEntity entity = getTeamEntity(teamId);
            DraftSessionEntity session = getSessionEntity(entity.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);

            if (requestDto.getDisplayOrder() != null && requestDto.getDisplayOrder() <= 0) {
                throw new IllegalArgumentException("Display order must be greater than 0.");
            }

            entity.update(
                    defaultIfBlank(requestDto.getTeamName(), entity.getTeamName()),
                    requestDto.getDisplayOrder() != null ? requestDto.getDisplayOrder() : entity.getDisplayOrder()
            );

            return ResponseDto.success(buildSessionDetail(entity.getDraftSessionId()));
        } catch (Exception e) {
            log.error("Failed to update draft team. teamId={}", teamId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteTeam(Long teamId, AuthActor actor) {
        try {
            DraftTeamEntity team = getTeamEntity(teamId);
            DraftSessionEntity session = getSessionEntity(team.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);

            if (Objects.equals(session.getCurrentDraftTeamId(), teamId)) {
                throw new IllegalArgumentException("The current draft team cannot be deleted.");
            }

            draftTeamRepository.delete(team);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("Failed to delete draft team. teamId={}", teamId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> createCandidate(DraftCandidateRequestDto requestDto, AuthActor actor) {
        try {
            validateCandidateRequest(requestDto, false);
            DraftSessionEntity session = getSessionEntity(requestDto.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);
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
            return ResponseDto.success(buildSessionDetail(requestDto.getDraftSessionId()));
        } catch (Exception e) {
            log.error("Failed to create draft candidate.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftCandidateResponseDto> getCandidate(Long sessionId, Long candidateUserId) {
        try {
            return ResponseDto.success(requireCandidate(sessionId, candidateUserId));
        } catch (Exception e) {
            log.error("Failed to get draft candidate. sessionId={}, candidateUserId={}", sessionId, candidateUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftCandidateResponseDto>> listCandidates(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(draftQueryRepository.findCandidatesBySessionId(sessionId));
        } catch (Exception e) {
            log.error("Failed to list draft candidates. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> updateCandidate(
            Long sessionId,
            Long candidateUserId,
            DraftCandidateRequestDto requestDto,
            AuthActor actor
    ) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            DraftCandidateEntity entity = getCandidateEntity(sessionId, candidateUserId);
            validateCandidateRequest(requestDto, true);

            boolean pickedExists = draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId);
            if (pickedExists && (requestDto.getStatus() != null || requestDto.getPickedDraftTeamId() != null || requestDto.getPickedAt() != null)) {
                throw new IllegalArgumentException("Picked candidates cannot be updated directly.");
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

            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to update draft candidate. sessionId={}, candidateUserId={}", sessionId, candidateUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> deleteCandidate(Long sessionId, Long candidateUserId, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            DraftCandidateEntity entity = getCandidateEntity(sessionId, candidateUserId);
            if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
                throw new IllegalArgumentException("Picked candidates cannot be deleted.");
            }
            draftCandidateRepository.delete(entity);
            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to delete draft candidate. sessionId={}, candidateUserId={}", sessionId, candidateUserId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> createOrder(DraftOrderRequestDto requestDto, AuthActor actor) {
        try {
            validateOrderRequest(requestDto);
            DraftSessionEntity session = getSessionEntity(requestDto.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            validateTeamBelongsToSession(requestDto.getDraftSessionId(), requestDto.getDraftTeamId());

            DraftOrderEntity entity = DraftOrderEntity.builder()
                    .draftSessionId(requestDto.getDraftSessionId())
                    .pickNo(requestDto.getPickNo())
                    .draftTeamId(requestDto.getDraftTeamId())
                    .build();

            draftOrderRepository.save(entity);
            return ResponseDto.success(buildSessionDetail(requestDto.getDraftSessionId()));
        } catch (Exception e) {
            log.error("Failed to create draft order.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftOrderResponseDto> getOrder(Long sessionId, Long pickNo) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            DraftOrderResponseDto order = requireOrder(sessionId, pickNo);
            populateOrderRoundNo(order, session.getTeamCount());
            return ResponseDto.success(order);
        } catch (Exception e) {
            log.error("Failed to get draft order. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftOrderResponseDto>> listOrders(Long sessionId) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            return ResponseDto.success(loadOrders(sessionId, session.getTeamCount()));
        } catch (Exception e) {
            log.error("Failed to list draft orders. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> updateOrder(Long sessionId, Long pickNo, DraftOrderRequestDto requestDto, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            DraftOrderEntity entity = getOrderEntity(sessionId, pickNo);
            if (draftPickRepository.existsByDraftSessionIdAndPickNo(sessionId, pickNo)) {
                throw new IllegalArgumentException("Completed draft orders cannot be updated.");
            }

            Long draftTeamId = requestDto.getDraftTeamId() != null ? requestDto.getDraftTeamId() : entity.getDraftTeamId();

            validateTeamBelongsToSession(sessionId, draftTeamId);
            entity.update(draftTeamId);

            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to update draft order. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> deleteOrder(Long sessionId, Long pickNo, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            DraftOrderEntity entity = getOrderEntity(sessionId, pickNo);
            if (draftPickRepository.existsByDraftSessionIdAndPickNo(sessionId, pickNo)) {
                throw new IllegalArgumentException("Completed draft orders cannot be deleted.");
            }
            draftOrderRepository.delete(entity);
            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to delete draft order. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftSessionDetailResponseDto> replaceOrders(
            Long sessionId,
            DraftOrderBulkReplaceRequestDto requestDto,
            AuthActor actor
    ) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);

            if (requestDto == null || requestDto.getOrders() == null) {
                return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "orders is required.");
            }
            if (draftPickRepository.countByDraftSessionId(sessionId) > 0) {
                return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, "Completed draft orders cannot be replaced.");
            }

            validateBulkOrderReplacement(sessionId, requestDto.getOrders());

            draftOrderRepository.deleteByDraftSessionId(sessionId);
            draftOrderRepository.saveAll(
                    requestDto.getOrders().stream()
                            .map(order -> DraftOrderEntity.builder()
                                    .draftSessionId(sessionId)
                                    .pickNo(order.getPickNo())
                                    .draftTeamId(order.getDraftTeamId())
                                    .build())
                            .toList()
            );

            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Failed to replace draft orders. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftPickResponseDto> createPick(DraftPickRequestDto requestDto, AuthActor actor) {
        try {
            validatePickRequest(requestDto);
            DraftSessionEntity session = getSessionEntity(requestDto.getDraftSessionId());
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            validateTeamBelongsToSession(requestDto.getDraftSessionId(), requestDto.getDraftTeamId());
            DraftCandidateEntity candidate = getCandidateEntity(requestDto.getDraftSessionId(), requestDto.getCandidateUserId());
            getUserEntity(requestDto.getPickedByUserId());
            ensureCandidatePickable(candidate, requestDto.getDraftSessionId(), requestDto.getCandidateUserId());
            draftOrderPatternService.getOrCreateOrder(requestDto.getDraftSessionId(), requestDto.getPickNo());

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
            log.error("Failed to create draft pick.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<DraftPickResponseDto> getPick(Long sessionId, Long pickNo) {
        try {
            return ResponseDto.success(requirePick(sessionId, pickNo));
        } catch (Exception e) {
            log.error("Failed to get draft pick. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<DraftPickResponseDto>> listPicks(Long sessionId) {
        try {
            getSessionEntity(sessionId);
            return ResponseDto.success(draftQueryRepository.findPicksBySessionId(sessionId));
        } catch (Exception e) {
            log.error("Failed to list draft picks. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<DraftPickResponseDto> updatePick(Long sessionId, Long pickNo, DraftPickRequestDto requestDto, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
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
            }
            draftOrderPatternService.getOrCreateOrder(sessionId, pickNo);
            if (!Objects.equals(previousCandidateUserId, requestDto.getCandidateUserId())) {
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
            log.error("Failed to update draft pick. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deletePick(Long sessionId, Long pickNo, AuthActor actor) {
        try {
            DraftSessionEntity session = getSessionEntity(sessionId);
            draftPermissionService.assertOwnerOrAdmin(session, actor);
            DraftPickEntity entity = getPickEntity(sessionId, pickNo);
            DraftCandidateEntity candidate = getCandidateEntity(sessionId, entity.getCandidateUserId());
            draftPickRepository.delete(entity);
            candidate.resetToWaiting();
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("Failed to delete draft pick. sessionId={}, pickNo={}", sessionId, pickNo, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private DraftSessionDetailResponseDto buildSessionDetail(Long sessionId) {
        DraftSessionSummaryResponseDto summary = requireSessionSummary(sessionId);
        DraftSessionDetailResponseDto detail = new DraftSessionDetailResponseDto();
        detail.setId(summary.getId());
        detail.setTitle(summary.getTitle());
        detail.setOwnerUserId(summary.getOwnerUserId());
        detail.setOwnerUserLoginId(summary.getOwnerUserLoginId());
        detail.setOwnerName(summary.getOwnerName());
        detail.setStatus(summary.getStatus());
        detail.setOrderMode(summary.getOrderMode());
        detail.setTeamCount(summary.getTeamCount());
        detail.setPickTimeSeconds(summary.getPickTimeSeconds());
        detail.setCurrentPickNo(summary.getCurrentPickNo());
        detail.setCurrentDraftTeamId(summary.getCurrentDraftTeamId());
        detail.setDeadlineAt(summary.getDeadlineAt());
        detail.setStartedAt(summary.getStartedAt());
        detail.setEndedAt(summary.getEndedAt());
        detail.setTeams(loadTeams(sessionId));
        detail.setCandidates(draftQueryRepository.findCandidatesBySessionId(sessionId));
        detail.setOrders(loadOrders(sessionId, summary.getTeamCount()));
        detail.setPicks(draftQueryRepository.findPicksBySessionId(sessionId));
        return detail;
    }

    private List<DraftTeamResponseDto> loadTeams(Long sessionId) {
        return draftQueryRepository.findTeamsBySessionId(sessionId);
    }

    private List<DraftOrderResponseDto> loadOrders(Long sessionId, Integer teamCount) {
        List<DraftOrderResponseDto> orders = draftQueryRepository.findOrdersBySessionId(sessionId);
        orders.forEach(order -> populateOrderRoundNo(order, teamCount));
        return orders;
    }

    private void populateOrderRoundNo(DraftOrderResponseDto order, Integer teamCount) {
        if (order == null || order.getPickNo() == null || teamCount == null || teamCount <= 0) {
            return;
        }
        order.setRoundNo(((order.getPickNo() - 1) / teamCount) + 1);
    }

    private Optional<DraftTeamEntity> findReplaceableDefaultTeam(Long sessionId, Integer displayOrder) {
        return draftTeamRepository.findByDraftSessionIdAndDisplayOrder(sessionId, displayOrder)
                .filter(team -> team.getPickerUserId() == null)
                .filter(team -> Objects.equals(team.getTeamName(), buildDefaultTeamName(displayOrder)));
    }

    private void createDefaultTeams(Long sessionId, Integer teamCount) {
        if (teamCount == null || teamCount <= 0) {
            return;
        }

        List<DraftTeamEntity> defaultTeams = new ArrayList<>(teamCount);
        for (int displayOrder = 1; displayOrder <= teamCount; displayOrder++) {
            defaultTeams.add(DraftTeamEntity.builder()
                    .draftSessionId(sessionId)
                    .teamName(buildDefaultTeamName(displayOrder))
                    .displayOrder(displayOrder)
                    .pickerUserId(null)
                    .build());
        }
        draftTeamRepository.saveAll(defaultTeams);
    }

    private String buildDefaultTeamName(int displayOrder) {
        return displayOrder + "팀";
    }

    private DraftSessionSummaryResponseDto requireSessionSummary(Long sessionId) {
        return draftQueryRepository.findSessionSummary(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
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
                .orElseThrow(() -> new IllegalArgumentException("Draft team could not be found."));
    }

    private DraftCandidateResponseDto requireCandidate(Long sessionId, Long candidateUserId) {
        return draftQueryRepository.findCandidate(sessionId, candidateUserId)
                .orElseThrow(() -> new IllegalArgumentException("Draft candidate could not be found."));
    }

    private DraftOrderResponseDto requireOrder(Long sessionId, Long pickNo) {
        return draftQueryRepository.findOrder(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("Draft order could not be found."));
    }

    private DraftPickResponseDto requirePick(Long sessionId, Long pickNo) {
        return draftQueryRepository.findPick(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("Draft pick could not be found."));
    }

    private DraftSessionEntity getSessionEntity(Long sessionId) {
        return draftSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
    }

    private DraftSessionEntity getSessionEntityForUpdate(Long sessionId) {
        return draftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
    }

    private DraftTeamEntity getTeamEntity(Long teamId) {
        return draftTeamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Draft team could not be found."));
    }

    private DraftCandidateEntity getCandidateEntity(Long sessionId, Long candidateUserId) {
        return draftCandidateRepository.findById(new DraftCandidateId(sessionId, candidateUserId))
                .orElseThrow(() -> new IllegalArgumentException("Draft candidate could not be found."));
    }

    private DraftOrderEntity getOrderEntity(Long sessionId, Long pickNo) {
        return draftOrderRepository.findById(new DraftOrderId(sessionId, pickNo))
                .orElseThrow(() -> new IllegalArgumentException("Draft order could not be found."));
    }

    private DraftPickEntity getPickEntity(Long sessionId, Long pickNo) {
        return draftPickRepository.findById(new DraftPickId(sessionId, pickNo))
                .orElseThrow(() -> new IllegalArgumentException("Draft pick could not be found."));
    }

    private UserEntity getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User could not be found."));
    }

    private void validateSessionRequest(DraftSessionRequestDto requestDto, boolean allowPartial) {
        if (!allowPartial || requestDto.getTitle() != null) {
            validateText(requestDto.getTitle(), "Session title");
        }
        if (!allowPartial || requestDto.getTeamCount() != null) {
            validatePositive(requestDto.getTeamCount(), "Team count");
            if (requestDto.getTeamCount() != null && requestDto.getTeamCount() <= 1) {
                throw new IllegalArgumentException("Team count must be at least 2.");
            }
        }
        if (!allowPartial || requestDto.getPickTimeSeconds() != null) {
            validatePositive(requestDto.getPickTimeSeconds(), "Pick time seconds");
        }
        if (requestDto.getCurrentPickNo() != null) {
            validatePositive(requestDto.getCurrentPickNo(), "Current pick number");
        }
        if (requestDto.getStatus() != null) {
            validateSessionStatus(requestDto.getStatus());
        }
        if (requestDto.getOrderMode() != null) {
            validateOrderMode(requestDto.getOrderMode());
        }
    }

    private void validateTeamRequest(DraftTeamRequestDto requestDto) {
        if (requestDto.getDraftSessionId() == null) {
            throw new IllegalArgumentException("Session id is required.");
        }
        validateText(requestDto.getTeamName(), "Team name");
        validatePositive(requestDto.getDisplayOrder(), "Display order");
    }

    private void validateCandidateRequest(DraftCandidateRequestDto requestDto, boolean allowPartial) {
        if (!allowPartial) {
            if (requestDto.getDraftSessionId() == null || requestDto.getCandidateUserId() == null) {
                throw new IllegalArgumentException("Session id and candidate user id are required.");
            }
            validateText(requestDto.getCandidateName(), "Candidate name");
            validateRace(requestDto.getRace());
        } else {
            if (requestDto.getCandidateName() != null) {
                validateText(requestDto.getCandidateName(), "Candidate name");
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
            throw new IllegalArgumentException("Session id, pick number, and draft team id are required.");
        }
        validatePositive(requestDto.getPickNo(), "Pick number");
    }

    private void validateBulkOrderReplacement(Long sessionId, List<DraftOrderRequestDto> orders) {
        Set<Long> pickNos = new HashSet<>();
        Set<Long> teamIds = draftTeamRepository.findAllByDraftSessionId(sessionId).stream()
                .map(DraftTeamEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        for (DraftOrderRequestDto order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("Order item is required.");
            }
            if (order.getRoundNo() == null) {
                throw new IllegalArgumentException("Round number is required.");
            }
            if (order.getPickNo() == null) {
                throw new IllegalArgumentException("Pick number is required.");
            }
            if (order.getDraftTeamId() == null) {
                throw new IllegalArgumentException("Draft team id is required.");
            }
            validatePositive(order.getRoundNo(), "Round number");
            validatePositive(order.getPickNo(), "Pick number");
            if (!pickNos.add(order.getPickNo())) {
                throw new IllegalArgumentException("Duplicate pick number is not allowed.");
            }
            if (!teamIds.contains(order.getDraftTeamId())) {
                throw new IllegalArgumentException("Draft team does not belong to this session.");
            }
        }
    }

    private void validatePickRequest(DraftPickRequestDto requestDto) {
        if (requestDto.getDraftSessionId() == null
                || requestDto.getPickNo() == null
                || requestDto.getDraftTeamId() == null
                || requestDto.getCandidateUserId() == null
                || requestDto.getPickedByUserId() == null) {
            throw new IllegalArgumentException("Session id, pick number, draft team id, candidate user id, and picked by user id are required.");
        }
        validatePositive(requestDto.getPickNo(), "Pick number");
    }

    private void ensureCandidatePickable(DraftCandidateEntity candidate, Long sessionId, Long candidateUserId) {
        if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
            throw new IllegalArgumentException("Candidate has already been picked.");
        }
        if (!"WAITING".equals(candidate.getStatus())) {
            throw new IllegalArgumentException("Only WAITING candidates can be picked.");
        }
    }

    private void advanceSessionAfterPick(DraftSessionEntity session, Long currentPickNo, LocalDateTime pickedAt) {
        if (draftCandidateRepository.countByDraftSessionIdAndStatus(session.getId(), "WAITING") <= 0) {
            session.finish(pickedAt);
            return;
        }

        long nextPickNo = currentPickNo + 1;
        DraftOrderEntity nextOrder = draftOrderPatternService.getOrCreateOrder(session.getId(), nextPickNo);
        session.advanceTurn((int) nextPickNo, nextOrder.getDraftTeamId(), pickedAt.plusSeconds(session.getPickTimeSeconds()));
    }

    private void validatePickedTeamBelongsToSession(Long sessionId, Long draftTeamId) {
        if (draftTeamId != null) {
            validateTeamBelongsToSession(sessionId, draftTeamId);
        }
    }

    private void validateTeamBelongsToSession(Long sessionId, Long draftTeamId) {
        if (!draftTeamRepository.existsByIdAndDraftSessionId(draftTeamId, sessionId)) {
            throw new IllegalArgumentException("Draft team does not belong to this session.");
        }
    }

    private void validateSessionStatus(String status) {
        validateAllowed(status, SESSION_STATUSES, "Session status");
    }

    private void validateOrderMode(String orderMode) {
        validateAllowed(normalizeOrderMode(orderMode), SUPPORTED_ORDER_MODES, "Order mode");
    }

    private void validateRace(String race) {
        validateAllowed(race, RACES, "Race");
    }

    private void validateCandidateStatus(String status) {
        validateAllowed(status, CANDIDATE_STATUSES, "Candidate status");
    }

    private void validateAllowed(String value, Set<String> allowed, String fieldName) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(fieldName + " is invalid.");
        }
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    private void validatePositive(Number number, String fieldName) {
        if (number == null || number.longValue() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0.");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String resolveOrderMode(String requestedOrderMode, String defaultOrderMode) {
        String normalized = requestedOrderMode == null
                ? normalizeOrderMode(defaultIfBlank(defaultOrderMode, "BASIC"))
                : normalizeOrderMode(requestedOrderMode);
        validateAllowed(normalized, SUPPORTED_ORDER_MODES, "Order mode");
        return normalized;
    }

    private String normalizeOrderMode(String orderMode) {
        if (orderMode == null || orderMode.isBlank()) {
            throw new IllegalArgumentException("Order mode is invalid.");
        }
        return orderMode.trim().toUpperCase(Locale.ROOT);
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
