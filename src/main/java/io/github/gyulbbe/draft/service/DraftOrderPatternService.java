package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DraftOrderPatternService {

    private final DraftOrderRepository draftOrderRepository;

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

        int patternLength = findRepeatingPatternLength(orders);
        long maxPickNo = orders.get(orders.size() - 1).getPickNo();

        DraftOrderEntity targetOrder = null;
        if (pickNo > maxPickNo) {
            for (long nextPickNo = maxPickNo + 1; nextPickNo <= pickNo; nextPickNo++) {
                DraftOrderEntity generatedOrder = savePatternOrder(sessionId, nextPickNo, patternLength, orders);
                if (nextPickNo == pickNo) {
                    targetOrder = generatedOrder;
                }
            }
            return targetOrder;
        }

        return savePatternOrder(sessionId, pickNo, patternLength, orders);
    }

    private DraftOrderEntity savePatternOrder(
            Long sessionId,
            long pickNo,
            int patternLength,
            List<DraftOrderEntity> orders
    ) {
        Long draftTeamId = orders.get((int) ((pickNo - 1) % patternLength)).getDraftTeamId();
        return draftOrderRepository.save(DraftOrderEntity.builder()
                .draftSessionId(sessionId)
                .pickNo(pickNo)
                .draftTeamId(draftTeamId)
                .build());
    }

    private int findRepeatingPatternLength(List<DraftOrderEntity> orders) {
        for (int patternLength = 1; patternLength <= orders.size(); patternLength++) {
            if (matchesPattern(orders, patternLength)) {
                return patternLength;
            }
        }
        return orders.size();
    }

    private boolean matchesPattern(List<DraftOrderEntity> orders, int patternLength) {
        for (int index = patternLength; index < orders.size(); index++) {
            if (!Objects.equals(
                    orders.get(index).getDraftTeamId(),
                    orders.get(index % patternLength).getDraftTeamId()
            )) {
                return false;
            }
        }
        return true;
    }
}
