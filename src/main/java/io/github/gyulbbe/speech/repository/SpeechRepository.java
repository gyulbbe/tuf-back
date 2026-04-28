package io.github.gyulbbe.speech.repository;

import io.github.gyulbbe.speech.entity.SpeechEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeechRepository extends JpaRepository<SpeechEntity, Long> {
}
