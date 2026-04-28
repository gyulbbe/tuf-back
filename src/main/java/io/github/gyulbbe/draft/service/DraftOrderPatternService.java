package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DraftOrderPatternService {

    private static final String BASIC_ORDER_MODE = "BASIC";
    private static final String SNAKE_ORDER_MODE = "SNAKE";
    private static final Set<String> SUPPORTED_ORDER_MODES = Set.of(BASIC_ORDER_MODE, SNAKE_ORDER_MODE);

    private final DraftSessionRepository draftSessionRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftTeamRepository draftTeamRepository;

    public DraftOrderEntity requireExistingOrder(Long sessionId, long pickNo) {
        return draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("Draft order could not be found."));
    }

    public DraftOrderEntity getOrCreateOrder(Long sessionId, long pickNo) {
        return draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, pickNo)
                .orElseGet(() -> createOrderFromPattern(sessionId, pickNo));
    }

    private DraftOrderEntity createOrderFromPattern(Long sessionId, long pickNo) {
        List<DraftOrderEntity> orders = draftOrderRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Draft order could not be found.");
        }

        DraftSessionEntity session = draftSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
        String orderMode = normalizeOrderMode(session.getOrderMode());
        List<DraftTeamEntity> orderedTeams = draftTeamRepository.findAllByDraftSessionIdOrderByDisplayOrderAsc(sessionId);
        if (orderedTeams.isEmpty()) {
            throw new IllegalArgumentException("Draft order could not be found.");
        }

        Set<Long> existingPickNos = new HashSet<>();
        orders.forEach(order -> existingPickNos.add(order.getPickNo()));

        DraftOrderEntity targetOrder = null;
        for (long nextPickNo = 1L; nextPickNo <= pickNo; nextPickNo++) {
            if (existingPickNos.contains(nextPickNo)) {
                continue;
            }

            DraftOrderEntity generatedOrder = savePatternOrder(sessionId, nextPickNo, orderMode, orderedTeams);
            existingPickNos.add(nextPickNo);
            if (nextPickNo == pickNo) {
                targetOrder = generatedOrder;
            }
        }

        return targetOrder;
    }

    private Long teamIdForBasicPick(long pickNo, List<DraftTeamEntity> orderedTeams) {
        int teamIndex = (int) ((pickNo - 1L) % orderedTeams.size());
        return orderedTeams.get(teamIndex).getId();
    }

    private Long teamIdForSnakePick(long pickNo, List<DraftTeamEntity> orderedTeams) {
        int teamCount = orderedTeams.size();
        if (teamCount == 1) {
            return orderedTeams.get(0).getId();
        }

        int cycleLength = teamCount * 2;
        int cycleIndex = (int) ((pickNo - 1L) % cycleLength);
        int teamIndex = cycleIndex < teamCount ? cycleIndex : cycleLength - 1 - cycleIndex;
        return orderedTeams.get(teamIndex).getId();
    }

    private Long teamIdForPick(long pickNo, String orderMode, List<DraftTeamEntity> orderedTeams) {
        return switch (orderMode) {
            case BASIC_ORDER_MODE -> teamIdForBasicPick(pickNo, orderedTeams);
            case SNAKE_ORDER_MODE -> teamIdForSnakePick(pickNo, orderedTeams);
            default -> throw new IllegalArgumentException("Draft order mode must be BASIC or SNAKE.");
        };
    }

    private DraftOrderEntity savePatternOrder(
            Long sessionId,
            long pickNo,
            String orderMode,
            List<DraftTeamEntity> orderedTeams
    ) {
        Long draftTeamId = teamIdForPick(pickNo, orderMode, orderedTeams);
        return draftOrderRepository.save(DraftOrderEntity.builder()
                .draftSessionId(sessionId)
                .pickNo(pickNo)
                .draftTeamId(draftTeamId)
                .build());
    }

    private String normalizeOrderMode(String orderMode) {
        String normalized = orderMode == null || orderMode.isBlank()
                ? BASIC_ORDER_MODE
                : orderMode.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ORDER_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Draft order mode must be BASIC or SNAKE.");
        }
        return normalized;
    }
}
