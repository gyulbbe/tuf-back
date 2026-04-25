package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DraftOrderPatternService {

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

        PatternResolver patternResolver = resolvePattern(sessionId, orders);
        Set<Long> existingPickNos = new HashSet<>();
        orders.forEach(order -> existingPickNos.add(order.getPickNo()));

        DraftOrderEntity targetOrder = null;
        for (long nextPickNo = 1L; nextPickNo <= pickNo; nextPickNo++) {
            if (existingPickNos.contains(nextPickNo)) {
                continue;
            }

            DraftOrderEntity generatedOrder = savePatternOrder(sessionId, nextPickNo, patternResolver);
            existingPickNos.add(nextPickNo);
            if (nextPickNo == pickNo) {
                targetOrder = generatedOrder;
            }
        }

        return targetOrder;
    }

    private PatternResolver resolvePattern(Long sessionId, List<DraftOrderEntity> orders) {
        List<DraftTeamEntity> orderedTeams = draftTeamRepository.findAllByDraftSessionIdOrderByDisplayOrderAsc(sessionId);
        if (orderedTeams.isEmpty()) {
            throw new IllegalArgumentException("Draft order could not be found.");
        }

        int basicPrefixLength = matchedPrefixLength(orders, orderedTeams, OrderMode.BASIC);
        int snakePrefixLength = matchedPrefixLength(orders, orderedTeams, OrderMode.SNAKE);

        if (snakePrefixLength > basicPrefixLength && snakePrefixLength > orderedTeams.size()) {
            return pickNo -> teamIdForSnakePick(pickNo, orderedTeams);
        }
        if (basicPrefixLength >= orders.size()) {
            return pickNo -> teamIdForBasicPick(pickNo, orderedTeams);
        }
        if (snakePrefixLength >= orders.size()) {
            return pickNo -> teamIdForSnakePick(pickNo, orderedTeams);
        }
        return pickNo -> teamIdForFallbackPick(pickNo, orders);
    }

    private int matchedPrefixLength(
            List<DraftOrderEntity> orders,
            List<DraftTeamEntity> orderedTeams,
            OrderMode orderMode
    ) {
        int matchedCount = 0;
        long expectedPickNo = 1L;
        for (DraftOrderEntity order : orders) {
            if (order.getPickNo() == null || !order.getPickNo().equals(expectedPickNo)) {
                break;
            }

            Long expectedTeamId = switch (orderMode) {
                case BASIC -> teamIdForBasicPick(order.getPickNo(), orderedTeams);
                case SNAKE -> teamIdForSnakePick(order.getPickNo(), orderedTeams);
            };
            if (!expectedTeamId.equals(order.getDraftTeamId())) {
                break;
            }

            matchedCount++;
            expectedPickNo++;
        }
        return matchedCount;
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

    private Long teamIdForFallbackPick(long pickNo, List<DraftOrderEntity> orders) {
        int orderIndex = (int) ((pickNo - 1L) % orders.size());
        return orders.get(orderIndex).getDraftTeamId();
    }

    private DraftOrderEntity savePatternOrder(Long sessionId, long pickNo, PatternResolver patternResolver) {
        Long draftTeamId = patternResolver.resolveTeamId(pickNo);
        return draftOrderRepository.save(DraftOrderEntity.builder()
                .draftSessionId(sessionId)
                .pickNo(pickNo)
                .draftTeamId(draftTeamId)
                .build());
    }

    private enum OrderMode {
        BASIC,
        SNAKE
    }

    @FunctionalInterface
    private interface PatternResolver {
        Long resolveTeamId(long pickNo);
    }
}
