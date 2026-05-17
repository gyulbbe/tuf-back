package io.github.gyulbbe.league.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.league.dto.LeagueDto;
import io.github.gyulbbe.league.dto.LeagueParticipationDto;
import io.github.gyulbbe.league.dto.ProleagueTeamDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueParticipationRepository leagueParticipationRepository;
    private final ProleagueTeamRepository proleagueTeamRepository;

    public ResponseDto<Void> insertLeague(LeagueDto leagueDto) {
        try {
            LeagueEntity entity = LeagueEntity.builder()
                    .leagueName(leagueDto.getLeagueName())
                    .startDate(leagueDto.getStartDate())
                    .endDate(leagueDto.getEndDate())
                    .build();

            leagueRepository.save(entity);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("리그 등록 실패", e);
            return ResponseDto.fail("리그 등록에 실패했습니다.");
        }
    }

    public ResponseDto<Void> insertLeagueParticipation(LeagueParticipationDto leagueParticipationDto) {
        try {
            LeagueParticipationEntity entity = LeagueParticipationEntity.builder()
                    .leagueId(leagueParticipationDto.getLeagueId())
                    .userId(leagueParticipationDto.getUserId())
                    .race(leagueParticipationDto.getRace())
                    .status(leagueParticipationDto.getStatus() != null ? leagueParticipationDto.getStatus() : "ACTIVE")
                    .build();

            leagueParticipationRepository.save(entity);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("리그 참가 등록 실패", e);
            return ResponseDto.fail("리그 참가 등록에 실패했습니다.");
        }
    }

    public ResponseDto<Void> insertProleagueTeam(ProleagueTeamDto proleagueTeamDto) {
        try {
            ProleagueTeamEntity entity = ProleagueTeamEntity.builder()
                    .teamName(proleagueTeamDto.getTeamName())
                    .leagueId(proleagueTeamDto.getLeagueId())
                    .leaderId(proleagueTeamDto.getLeaderId())
                    .viceLeaderId(proleagueTeamDto.getViceLeaderId())
                    .build();

            proleagueTeamRepository.save(entity);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("프로리그 팀 등록 실패", e);
            return ResponseDto.fail("프로리그 팀 등록에 실패했습니다.");
        }
    }

    // League 조회
    public ResponseDto<LeagueDto> findLeagueById(Long id) {
        try {
            LeagueEntity entity = leagueRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("리그를 찾을 수 없습니다.");
            }

            LeagueDto dto = new LeagueDto();
            dto.setId(entity.getId());
            dto.setLeagueName(entity.getLeagueName());
            dto.setStartDate(entity.getStartDate());
            dto.setEndDate(entity.getEndDate());

            return ResponseDto.success(dto);
        } catch (Exception e) {
            log.error("리그 조회 실패", e);
            return ResponseDto.fail("리그 조회에 실패했습니다.");
        }
    }

    // League 수정
    public ResponseDto<Void> updateLeague(Long id, LeagueDto leagueDto) {
        try {
            LeagueEntity entity = leagueRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("리그를 찾을 수 없습니다.");
            }

            entity.updateBasic(
                    leagueDto.getLeagueName(),
                    entity.getSeasonName(),
                    entity.getDescription(),
                    entity.getStatus(),
                    leagueDto.getStartDate(),
                    leagueDto.getEndDate()
            );
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("리그 수정 실패", e);
            return ResponseDto.fail("리그 수정에 실패했습니다.");
        }
    }

    // LeagueParticipation 조회
    public ResponseDto<LeagueParticipationDto> findLeagueParticipationById(Long id) {
        try {
            LeagueParticipationEntity entity = leagueParticipationRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("리그 참가 정보를 찾을 수 없습니다.");
            }

            LeagueParticipationDto dto = new LeagueParticipationDto();
            dto.setId(entity.getId());
            dto.setLeagueId(entity.getLeagueId());
            dto.setUserId(entity.getUserId());
            dto.setRace(entity.getRace());
            dto.setStatus(entity.getStatus());

            return ResponseDto.success(dto);
        } catch (Exception e) {
            log.error("리그 참가 조회 실패", e);
            return ResponseDto.fail("리그 참가 조회에 실패했습니다.");
        }
    }

    // LeagueParticipation 수정
    public ResponseDto<Void> updateLeagueParticipation(Long id, LeagueParticipationDto leagueParticipationDto) {
        try {
            LeagueParticipationEntity entity = leagueParticipationRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("리그 참가 정보를 찾을 수 없습니다.");
            }

            LeagueParticipationEntity updated = LeagueParticipationEntity.builder()
                    .id(entity.getId())
                    .leagueId(leagueParticipationDto.getLeagueId())
                    .userId(leagueParticipationDto.getUserId())
                    .race(leagueParticipationDto.getRace())
                    .status(leagueParticipationDto.getStatus())
                    .build();

            leagueParticipationRepository.save(updated);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("리그 참가 수정 실패", e);
            return ResponseDto.fail("리그 참가 수정에 실패했습니다.");
        }
    }

    // ProleagueTeam 조회
    public ResponseDto<ProleagueTeamDto> findProleagueTeamById(Long id) {
        try {
            ProleagueTeamEntity entity = proleagueTeamRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("프로리그 팀을 찾을 수 없습니다.");
            }

            ProleagueTeamDto dto = new ProleagueTeamDto();
            dto.setId(entity.getId());
            dto.setTeamName(entity.getTeamName());
            dto.setLeagueId(entity.getLeagueId());
            dto.setLeaderId(entity.getLeaderId());
            dto.setViceLeaderId(entity.getViceLeaderId());

            return ResponseDto.success(dto);
        } catch (Exception e) {
            log.error("프로리그 팀 조회 실패", e);
            return ResponseDto.fail("프로리그 팀 조회에 실패했습니다.");
        }
    }

    // ProleagueTeam 수정
    public ResponseDto<Void> updateProleagueTeam(Long id, ProleagueTeamDto proleagueTeamDto) {
        try {
            ProleagueTeamEntity entity = proleagueTeamRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("프로리그 팀을 찾을 수 없습니다.");
            }

            entity.update(
                    proleagueTeamDto.getTeamName(),
                    proleagueTeamDto.getLeaderId(),
                    proleagueTeamDto.getViceLeaderId(),
                    entity.getDisplayOrder(),
                    entity.getDraftTeamId()
            );
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("프로리그 팀 수정 실패", e);
            return ResponseDto.fail("프로리그 팀 수정에 실패했습니다.");
        }
    }

    // League 목록 조회
    public ResponseDto<List<LeagueDto>> list() {
        try {
            List<LeagueEntity> entities = leagueRepository.findAll();

            List<LeagueDto> dtos = entities.stream().map(entity -> {
                LeagueDto dto = new LeagueDto();
                dto.setId(entity.getId());
                dto.setLeagueName(entity.getLeagueName());
                dto.setStartDate(entity.getStartDate());
                dto.setEndDate(entity.getEndDate());
                return dto;
            }).toList();

            return ResponseDto.success(dtos);
        } catch (Exception e) {
            log.error("리그 목록 조회 실패", e);
            return ResponseDto.fail("리그 목록 조회에 실패했습니다.");
        }
    }

    // LeagueParticipation 목록 조회
    public ResponseDto<List<LeagueParticipationDto>> listParticipation() {
        try {
            List<LeagueParticipationEntity> entities = leagueParticipationRepository.findAll();

            List<LeagueParticipationDto> dtos = entities.stream().map(entity -> {
                LeagueParticipationDto dto = new LeagueParticipationDto();
                dto.setId(entity.getId());
                dto.setLeagueId(entity.getLeagueId());
                dto.setUserId(entity.getUserId());
                dto.setRace(entity.getRace());
                dto.setStatus(entity.getStatus());
                return dto;
            }).toList();

            return ResponseDto.success(dtos);
        } catch (Exception e) {
            log.error("리그 참가 목록 조회 실패", e);
            return ResponseDto.fail("리그 참가 목록 조회에 실패했습니다.");
        }
    }

    // ProleagueTeam 목록 조회
    public ResponseDto<List<ProleagueTeamDto>> listTeam() {
        try {
            List<ProleagueTeamEntity> entities = proleagueTeamRepository.findAll();

            List<ProleagueTeamDto> dtos = entities.stream().map(entity -> {
                ProleagueTeamDto dto = new ProleagueTeamDto();
                dto.setId(entity.getId());
                dto.setTeamName(entity.getTeamName());
                dto.setLeagueId(entity.getLeagueId());
                dto.setLeaderId(entity.getLeaderId());
                dto.setViceLeaderId(entity.getViceLeaderId());
                return dto;
            }).toList();

            return ResponseDto.success(dtos);
        } catch (Exception e) {
            log.error("프로리그 팀 목록 조회 실패", e);
            return ResponseDto.fail("프로리그 팀 목록 조회에 실패했습니다.");
        }
    }
}
