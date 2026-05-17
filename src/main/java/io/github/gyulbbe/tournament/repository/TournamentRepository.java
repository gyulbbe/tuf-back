package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends JpaRepository<TournamentEntity, Long> {
    List<TournamentEntity> findAllByStatusOrderByRegDateDesc(String status);

    List<TournamentEntity> findAllByOwnerUserIdOrderByRegDateDesc(Long ownerUserId);

    List<TournamentEntity> findAllByStatusInOrderByUpdateDateDescRegDateDesc(List<String> statuses);

    Optional<TournamentEntity> findByIdAndStatusIn(Long id, List<String> statuses);

    @Query("""
            select t
            from TournamentEntity t
            where t.status = :status
            order by coalesce(t.updateDate, t.regDate) desc,
                     t.regDate desc,
                     t.id desc
            """)
    List<TournamentEntity> findHomeMainLiveTournaments(
            @Param("status") String status,
            Pageable pageable
    );

    @Query(
            value = """
                    select t
                    from TournamentEntity t
                    where t.status in :statuses
                      and (:keyword is null or lower(t.title) like lower(concat(concat('%', :keyword), '%')))
                    order by case when t.status = :liveStatus then 0 else 1 end,
                             coalesce(t.updateDate, t.regDate) desc,
                             t.regDate desc,
                             t.id desc
                    """,
            countQuery = """
                    select count(t)
                    from TournamentEntity t
                    where t.status in :statuses
                      and (:keyword is null or lower(t.title) like lower(concat(concat('%', :keyword), '%')))
                    """
    )
    Page<TournamentEntity> findPublicPage(
            @Param("statuses") List<String> statuses,
            @Param("liveStatus") String liveStatus,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TournamentEntity t where t.id = :tournamentId")
    Optional<TournamentEntity> findByIdForUpdate(@Param("tournamentId") Long tournamentId);
}
