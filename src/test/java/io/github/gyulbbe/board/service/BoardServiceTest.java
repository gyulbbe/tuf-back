package io.github.gyulbbe.board.service;

import io.github.gyulbbe.board.dto.BoardCommentCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardCommentUpdateRequestDto;
import io.github.gyulbbe.board.dto.BoardCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardUpdateRequestDto;
import io.github.gyulbbe.board.entity.BoardCommentEntity;
import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.repository.BoardCommentRepository;
import io.github.gyulbbe.board.repository.BoardRepository;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private BoardCommentRepository boardCommentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BoardService boardService;

    @Test
    void listBoards_authenticated_user_includes_authorUserId() {
        UserEntity user = user(10L, "writer01", "Real Name");
        BoardEntity board = board(1L, 10L, "Old Name", "title", "body", now().minusDays(1), now().minusHours(1));

        given(boardRepository.search(any(), anyString(), any())).willReturn(
                new PageImpl<>(List.of(board), PageRequest.of(0, 10), 1)
        );
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(user));

        var response = boardService.listBoards(1, 10, null, "keyword", authentication(10L, "viewer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getBoards()).hasSize(1);
        assertThat(response.getData().getBoards().get(0).getAuthorUserId()).isEqualTo("writer01");
    }

    @Test
    void getBoard_restores_authorUserId_from_existing_userPk_for_board_and_comments() {
        UserEntity user = user(10L, "writer01", "Real Name");
        BoardEntity board = board(1L, 10L, "Old Real Name", "title", "text", now().minusDays(1), now().minusHours(1));
        BoardCommentEntity comment = comment(100L, 1L, 10L, "Old Real Name", null, 0, "comment", now().minusMinutes(30));

        given(boardRepository.findById(1L)).willReturn(Optional.of(board));
        given(boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(1L)).willReturn(List.of(comment));
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(user));

        var response = boardService.getBoard(1L, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getAuthorUserId()).isEqualTo("writer01");
        assertThat(response.getData().getComments()).hasSize(1);
        assertThat(response.getData().getComments().get(0).getAuthorUserId()).isEqualTo("writer01");
    }

    @Test
    void getBoard_returnsNotFoundErrorCode_whenBoardMissing() {
        given(boardRepository.findById(999L)).willReturn(Optional.empty());

        var response = boardService.getBoard(999L, null);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void createBoard_guest_returns_null_authorUserId() {
        BoardCreateRequestDto requestDto = new BoardCreateRequestDto();
        requestDto.setAuthorName("guest01");
        requestDto.setTitle("first post");
        requestDto.setText("body");

        given(boardRepository.save(any(BoardEntity.class))).willAnswer(invocation -> {
            BoardEntity source = invocation.getArgument(0);
            return board(1L, source.getUserId(), source.getAuthorName(), source.getTitle(), source.getText(), now(), now());
        });

        var response = boardService.createBoard(requestDto, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getAuthorUserId()).isNull();
        assertThat(response.getData().isEditable()).isFalse();
    }

    @Test
    void createBoard_returnsValidationErrorCode_whenTitleMissing() {
        BoardCreateRequestDto requestDto = new BoardCreateRequestDto();
        requestDto.setAuthorName("guest01");
        requestDto.setText("body");

        var response = boardService.createBoard(requestDto, null);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("제목은 필수입니다.");
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void listComments_guest_snapshot_preserves_tree_order_and_null_authorUserId() {
        BoardCommentEntity rootA = comment(100L, 1L, null, "guestA", null, 0, "rootA", now().minusMinutes(4));
        BoardCommentEntity childA1 = comment(101L, 1L, null, "guestA1", 100L, 1, "childA1", now().minusMinutes(3));
        BoardCommentEntity childA2 = comment(102L, 1L, null, "guestA2", 100L, 1, "childA2", now().minusMinutes(2));
        BoardCommentEntity rootB = comment(200L, 1L, null, "guestB", null, 0, "rootB", now().minusMinutes(1));

        given(boardRepository.existsById(1L)).willReturn(true);
        given(boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(1L))
                .willReturn(List.of(rootA, childA1, childA2, rootB));

        var response = boardService.listComments(1L, null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getBoardId()).isEqualTo(1L);
        assertThat(response.getData().getCommentCount()).isEqualTo(4);
        assertThat(response.getData().getComments()).extracting("id").containsExactly(100L, 200L);
        assertThat(response.getData().getComments().get(0).getAuthorUserId()).isNull();
        assertThat(response.getData().getComments().get(0).getChildren()).extracting("id").containsExactly(101L, 102L);
    }

    @Test
    void createComment_authenticated_user_returns_latest_comment_snapshot() {
        BoardCommentCreateRequestDto requestDto = new BoardCommentCreateRequestDto();
        requestDto.setContent("member comment");

        UserEntity user = user(10L, "writer01", "Real Name");
        BoardCommentEntity existingGuest = comment(100L, 1L, null, "guest01", null, 0, "guest comment", now().minusMinutes(5));
        BoardCommentEntity createdMember = comment(101L, 1L, 10L, "writer01", null, 0, "member comment", now().minusMinutes(1));

        given(boardRepository.existsById(1L)).willReturn(true);
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(boardCommentRepository.save(any(BoardCommentEntity.class))).willReturn(createdMember);
        given(boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(1L))
                .willReturn(List.of(existingGuest, createdMember));
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(user));

        var response = boardService.createComment(1L, requestDto, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getBoardId()).isEqualTo(1L);
        assertThat(response.getData().getCommentCount()).isEqualTo(2);
        assertThat(response.getData().getComments()).hasSize(2);
        assertThat(response.getData().getComments().get(1).getAuthorUserId()).isEqualTo("writer01");
    }

    @Test
    void updateComment_returns_latest_comment_snapshot() {
        UserEntity user = user(10L, "writer01", "Real Name");
        BoardCommentEntity comment = comment(100L, 1L, 10L, "writer01", null, 0, "before", now().minusMinutes(10));
        BoardCommentEntity reply = comment(101L, 1L, null, "guest01", 100L, 1, "reply", now().minusMinutes(5));
        BoardCommentUpdateRequestDto requestDto = new BoardCommentUpdateRequestDto();
        requestDto.setContent("after");

        given(boardCommentRepository.findByIdAndBoardId(100L, 1L)).willReturn(Optional.of(comment));
        given(boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(1L)).willReturn(List.of(comment, reply));
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(user));

        var response = boardService.updateComment(1L, 100L, requestDto, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getCommentCount()).isEqualTo(2);
        assertThat(response.getData().getComments()).hasSize(1);
        assertThat(response.getData().getComments().get(0).getContent()).isEqualTo("after");
        assertThat(response.getData().getComments().get(0).getAuthorUserId()).isEqualTo("writer01");
        assertThat(response.getData().getComments().get(0).getChildren()).extracting("id").containsExactly(101L);
    }

    @Test
    void deleteComment_returns_latest_comment_snapshot() {
        BoardCommentEntity comment = comment(100L, 1L, 10L, "writer01", null, 0, "parent", now().minusMinutes(10));
        BoardCommentEntity reply = comment(101L, 1L, null, "guest01", 100L, 1, "reply", now().minusMinutes(9));
        BoardCommentEntity survivor = comment(200L, 1L, null, "guest02", null, 0, "survivor", now().minusMinutes(1));

        given(boardCommentRepository.findByIdAndBoardId(100L, 1L)).willReturn(Optional.of(comment));
        given(boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(1L))
                .willReturn(List.of(comment, reply, survivor), List.of(survivor));

        var response = boardService.deleteComment(1L, 100L, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getBoardId()).isEqualTo(1L);
        assertThat(response.getData().getCommentCount()).isEqualTo(1);
        assertThat(response.getData().getComments()).extracting("id").containsExactly(200L);
        assertThat(response.getData().getComments().get(0).getAuthorUserId()).isNull();
    }

    @Test
    void updateBoard_allows_owner() {
        BoardEntity board = board(1L, 10L, "writer01", "before", "before text", now().minusDays(1), now().minusDays(1));
        BoardUpdateRequestDto requestDto = new BoardUpdateRequestDto();
        requestDto.setTitle("after");
        requestDto.setText("after text");

        given(boardRepository.findById(1L)).willReturn(Optional.of(board));

        var response = boardService.updateBoard(1L, requestDto, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(board.getTitle()).isEqualTo("after");
        assertThat(board.getText()).isEqualTo("after text");
        assertThat(response.getData().isEditable()).isTrue();
    }

    @Test
    void updateBoard_rejects_guest_for_guest_owned_post() {
        BoardEntity board = board(1L, null, "guest01", "before", "before text", now().minusDays(1), now().minusDays(1));
        BoardUpdateRequestDto requestDto = new BoardUpdateRequestDto();
        requestDto.setTitle("after");
        requestDto.setText("after text");

        given(boardRepository.findById(1L)).willReturn(Optional.of(board));

        var response = boardService.updateBoard(1L, requestDto, null);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void createBoard_authenticated_user_uses_login_userId_not_name() {
        BoardCreateRequestDto requestDto = new BoardCreateRequestDto();
        requestDto.setTitle("member post");
        requestDto.setText("body");

        UserEntity user = user(10L, "writer01", "Real Name");

        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(userRepository.findAllById(List.of(10L))).willReturn(List.of(user));
        given(boardRepository.save(any(BoardEntity.class))).willAnswer(invocation -> {
            BoardEntity source = invocation.getArgument(0);
            return board(1L, source.getUserId(), source.getAuthorName(), source.getTitle(), source.getText(), now(), now());
        });

        var response = boardService.createBoard(requestDto, authentication(10L, "writer01", "ROLE_USER"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getAuthorUserId()).isEqualTo("writer01");
    }

    private UserEntity user(Long id, String userId, String name) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .userType("ROLE_USER")
                .status("ACTIVE")
                .build();
    }

    private BoardEntity board(
            Long id,
            Long userId,
            String authorName,
            String title,
            String text,
            LocalDateTime regDate,
            LocalDateTime updateDate
    ) {
        return BoardEntity.builder()
                .id(id)
                .userId(userId)
                .authorName(authorName)
                .title(title)
                .text(text)
                .regDate(regDate)
                .updateDate(updateDate)
                .build();
    }

    private BoardCommentEntity comment(
            Long id,
            Long boardId,
            Long userId,
            String authorName,
            Long parentId,
            int depth,
            String content,
            LocalDateTime time
    ) {
        return BoardCommentEntity.builder()
                .id(id)
                .boardId(boardId)
                .userId(userId)
                .authorName(authorName)
                .parentId(parentId)
                .depth(depth)
                .content(content)
                .regDate(time)
                .updateDate(time)
                .build();
    }

    private Authentication authentication(Long userPk, String userId, String role) {
        UserEntity user = UserEntity.builder()
                .id(userPk)
                .userId(userId)
                .userType(role)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 4, 22, 10, 0);
    }
}
