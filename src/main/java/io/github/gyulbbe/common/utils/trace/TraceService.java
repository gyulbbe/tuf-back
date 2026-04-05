package io.github.gyulbbe.common.utils.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
@Slf4j
@RequiredArgsConstructor
public class TraceService {

    private final TraceRepository traceRepository;

    public void insertTrace(String type, String text) {
        TraceEntity entity = TraceEntity.builder()
                .type(type)
                .text(text)
                .build();
        traceRepository.save(entity);
    }
}
