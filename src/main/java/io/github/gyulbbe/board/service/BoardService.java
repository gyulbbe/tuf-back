package io.github.gyulbbe.board.service;

import io.github.gyulbbe.board.dto.BoardCommentCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardCommentResponseDto;
import io.github.gyulbbe.board.dto.BoardCommentUpdateRequestDto;
import io.github.gyulbbe.board.dto.BoardCommentsSnapshotResponseDto;
import io.github.gyulbbe.board.dto.BoardCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardDetailResponseDto;
import io.github.gyulbbe.board.dto.BoardListResponseDto;
import io.github.gyulbbe.board.dto.BoardPaginationResponseDto;
import io.github.gyulbbe.board.dto.BoardSearchType;
import io.github.gyulbbe.board.dto.BoardSummaryResponseDto;
import io.github.gyulbbe.board.dto.BoardUpdateRequestDto;
import io.github.gyulbbe.board.entity.BoardCommentEntity;
import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.repository.BoardCommentRepository;
import io.github.gyulbbe.board.repository.BoardRepository;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoardService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final int PAGE_GROUP_SIZE = 10;
    private static final int AUTHOR_NAME_MAX_LENGTH = 50;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int COMMENT_MAX_LENGTH = 4000;
    private static final Set<String> PRIVILEGED_ROLES = Set.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN");

    private final BoardRepository boardRepository;
    private final BoardCommentRepository boardCommentRepository;
    private final UserRepository userRepository;

    public ResponseDto<BoardListResponseDto> listBoards(
            Integer page,
            Integer size,
            String searchType,
            String keyword,
            Authentication authentication
    ) {
        try {
            BoardSearchType resolvedSearchType = resolveSearchType(searchType, keyword);
            Pageable pageable = toPageable(page, size);
            Page<BoardEntity> boardPage = boardRepository.search(resolvedSearchType, normalizeKeyword(keyword), pageable);
            BoardActor actor = resolveActor(authentication);
            Map<Long, String> authorUserIdMap = resolveAuthorUserIdMap(extractAuthorUserPks(boardPage.getContent()));

            List<BoardSummaryResponseDto> boards = boardPage.getContent().stream()
                    .map(board -> toBoardSummary(board, actor, authorUserIdMap))
                    .toList();

            return ResponseDto.success(BoardListResponseDto.builder()
                    .boards(boards)
                    .pagination(toPagination(boardPage))
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("게시글 목록 조회 실패", e);
            return ResponseDto.fail("게시글 목록 조회에 실패했습니다.");
        }
    }

    public ResponseDto<BoardDetailResponseDto> getBoard(Long boardId, Authentication authentication) {
        try {
            BoardEntity board = boardRepository.findById(boardId).orElse(null);
            if (board == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toBoardDetail(board, comments, actor));
        } catch (Exception e) {
            log.error("게시글 상세 조회 실패", e);
            return ResponseDto.fail("게시글 상세 조회에 실패했습니다.");
        }
    }

    public ResponseDto<BoardCommentsSnapshotResponseDto> listComments(Long boardId, Authentication authentication) {
        try {
            if (!boardRepository.existsById(boardId)) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toCommentsSnapshot(boardId, comments, actor));
        } catch (Exception e) {
            log.error("댓글 목록 조회 실패", e);
            return ResponseDto.fail("댓글 목록 조회에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<BoardDetailResponseDto> createBoard(BoardCreateRequestDto requestDto, Authentication authentication) {
        try {
            String title = validateTitle(requestDto.getTitle());
            String text = validateBoardText(requestDto.getText());
            BoardActor actor = resolveActor(authentication);
            AuthorInfo authorInfo = resolveAuthorInfo(actor, requestDto.getAuthorName());

            BoardEntity board = boardRepository.save(BoardEntity.builder()
                    .userId(authorInfo.userId())
                    .authorName(authorInfo.authorName())
                    .title(title)
                    .text(text)
                    .build());

            return ResponseDto.success(toBoardDetail(board, Collections.emptyList(), actor));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("게시글 등록 실패", e);
            return ResponseDto.fail("게시글 등록에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<BoardDetailResponseDto> updateBoard(
            Long boardId,
            BoardUpdateRequestDto requestDto,
            Authentication authentication
    ) {
        try {
            BoardEntity board = boardRepository.findById(boardId).orElse(null);
            if (board == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            if (!canModify(actor, board.getUserId())) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "게시글을 수정할 권한이 없습니다.");
            }

            board.update(
                    validateTitle(requestDto.getTitle()),
                    validateBoardText(requestDto.getText())
            );

            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toBoardDetail(board, comments, actor));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("게시글 수정 실패", e);
            return ResponseDto.fail("게시글 수정에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<Void> deleteBoard(Long boardId, Authentication authentication) {
        try {
            BoardEntity board = boardRepository.findById(boardId).orElse(null);
            if (board == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            if (!canModify(actor, board.getUserId())) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "게시글을 삭제할 권한이 없습니다.");
            }

            boardCommentRepository.deleteAllByBoardId(boardId);
            boardRepository.delete(board);
            return ResponseDto.success(null);
        } catch (Exception e) {
            log.error("게시글 삭제 실패", e);
            return ResponseDto.fail("게시글 삭제에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<BoardCommentsSnapshotResponseDto> createComment(
            Long boardId,
            BoardCommentCreateRequestDto requestDto,
            Authentication authentication
    ) {
        try {
            if (!boardRepository.existsById(boardId)) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "게시글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            AuthorInfo authorInfo = resolveAuthorInfo(actor, requestDto.getAuthorName());
            String content = validateCommentContent(requestDto.getContent());

            Long parentId = requestDto.getParentId();
            int depth = 0;
            if (parentId != null) {
                BoardCommentEntity parent = boardCommentRepository.findByIdAndBoardId(parentId, boardId).orElse(null);
                if (parent == null) {
                    return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "부모 댓글을 찾을 수 없습니다.");
                }
                depth = parent.getDepth() + 1;
            }

            boardCommentRepository.save(BoardCommentEntity.builder()
                    .boardId(boardId)
                    .userId(authorInfo.userId())
                    .authorName(authorInfo.authorName())
                    .parentId(parentId)
                    .depth(depth)
                    .content(content)
                    .build());

            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toCommentsSnapshot(boardId, comments, actor));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("댓글 등록 실패", e);
            return ResponseDto.fail("댓글 등록에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<BoardCommentsSnapshotResponseDto> updateComment(
            Long boardId,
            Long commentId,
            BoardCommentUpdateRequestDto requestDto,
            Authentication authentication
    ) {
        try {
            BoardCommentEntity comment = boardCommentRepository.findByIdAndBoardId(commentId, boardId).orElse(null);
            if (comment == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "댓글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            if (!canModify(actor, comment.getUserId())) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "댓글을 수정할 권한이 없습니다.");
            }

            comment.updateContent(validateCommentContent(requestDto.getContent()));
            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toCommentsSnapshot(boardId, comments, actor));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("댓글 수정 실패", e);
            return ResponseDto.fail("댓글 수정에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<BoardCommentsSnapshotResponseDto> deleteComment(Long boardId, Long commentId, Authentication authentication) {
        try {
            BoardCommentEntity comment = boardCommentRepository.findByIdAndBoardId(commentId, boardId).orElse(null);
            if (comment == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "댓글을 찾을 수 없습니다.");
            }

            BoardActor actor = resolveActor(authentication);
            if (!canModify(actor, comment.getUserId())) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "댓글을 삭제할 권한이 없습니다.");
            }

            List<BoardCommentEntity> comments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            Map<Long, List<BoardCommentEntity>> childrenMap = buildChildrenMap(comments);
            List<Long> deleteIds = new ArrayList<>();
            collectCommentIds(commentId, childrenMap, deleteIds);
            boardCommentRepository.deleteAllByIdInBatch(deleteIds);

            List<BoardCommentEntity> remainingComments = boardCommentRepository.findAllByBoardIdOrderByRegDateAscIdAsc(boardId);
            return ResponseDto.success(toCommentsSnapshot(boardId, remainingComments, actor));
        } catch (Exception e) {
            log.error("댓글 삭제 실패", e);
            return ResponseDto.fail("댓글 삭제에 실패했습니다.");
        }
    }

    private BoardSearchType resolveSearchType(String searchType, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        if (!StringUtils.hasText(searchType)) {
            return BoardSearchType.TITLE;
        }

        try {
            return BoardSearchType.from(searchType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("searchType 은 USER_ID, TITLE, TEXT 중 하나여야 합니다.");
        }
    }

    private Pageable toPageable(Integer page, Integer size) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        if (resolvedPage < 1) {
            throw new IllegalArgumentException("page 는 1 이상이어야 합니다.");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new IllegalArgumentException("size 는 1 이상 " + MAX_SIZE + " 이하이어야 합니다.");
        }

        return PageRequest.of(resolvedPage - 1, resolvedSize);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String validateTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }

        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmedTitle;
    }

    private String validateBoardText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("본문은 필수입니다.");
        }
        return text.trim();
    }

    private String validateCommentContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("댓글 내용은 필수입니다.");
        }

        String trimmedContent = content.trim();
        if (trimmedContent.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException("댓글 내용은 " + COMMENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmedContent;
    }

    private AuthorInfo resolveAuthorInfo(BoardActor actor, String guestAuthorName) {
        if (actor.authenticated()) {
            UserEntity user = userRepository.findById(actor.userId()).orElse(null);
            if (user == null) {
                throw new IllegalArgumentException("작성자 정보를 찾을 수 없습니다.");
            }

            if (!StringUtils.hasText(user.getUserId())) {
                throw new IllegalArgumentException("작성자 로그인 아이디가 있어야 합니다.");
            }
            String resolvedAuthorName = user.getUserId().trim();

            return new AuthorInfo(user.getId(), truncateAuthorName(resolvedAuthorName));
        }

        if (!StringUtils.hasText(guestAuthorName)) {
            throw new IllegalArgumentException("비회원 작성자는 authorName 이 필요합니다.");
        }

        return new AuthorInfo(null, truncateAuthorName(guestAuthorName.trim()));
    }

    private String truncateAuthorName(String authorName) {
        if (!StringUtils.hasText(authorName)) {
            throw new IllegalArgumentException("작성자 이름은 비어 있을 수 없습니다.");
        }

        String trimmedAuthorName = authorName.trim();
        if (trimmedAuthorName.length() > AUTHOR_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("작성자 이름은 " + AUTHOR_NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmedAuthorName;
    }

    private BoardActor resolveActor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return BoardActor.guest();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails customUserDetails)) {
            return BoardActor.guest();
        }

        String role = customUserDetails.getAuthorities().stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .orElse("ROLE_USER");

        return new BoardActor(customUserDetails.getUserPk(), role);
    }

    private boolean canModify(BoardActor actor, Long ownerUserId) {
        if (!actor.authenticated()) {
            return false;
        }
        if (actor.privileged()) {
            return true;
        }
        return ownerUserId != null && ownerUserId.equals(actor.userId());
    }

    private BoardSummaryResponseDto toBoardSummary(BoardEntity board, BoardActor actor, Map<Long, String> authorUserIdMap) {
        String authorUserId = resolveAuthorUserId(board.getUserId(), authorUserIdMap);
        return BoardSummaryResponseDto.builder()
                .id(board.getId())
                .authorUserId(authorUserId)
                .authorName(resolveResponseAuthorName(board.getAuthorName(), authorUserId))
                .title(board.getTitle())
                .summaryText(createSummaryText(board.getText()))
                .regDate(board.getRegDate())
                .updateDate(board.getUpdateDate())
                .editable(canModify(actor, board.getUserId()))
                .deletable(canModify(actor, board.getUserId()))
                .build();
    }

    private String createSummaryText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private BoardDetailResponseDto toBoardDetail(BoardEntity board, List<BoardCommentEntity> comments, BoardActor actor) {
        Map<Long, String> authorUserIdMap = resolveAuthorUserIdMap(extractAuthorUserPks(board, comments));
        BoardCommentsSnapshotResponseDto commentsSnapshot = toCommentsSnapshot(board.getId(), comments, actor, authorUserIdMap);
        String authorUserId = resolveAuthorUserId(board.getUserId(), authorUserIdMap);

        return BoardDetailResponseDto.builder()
                .id(board.getId())
                .authorUserId(authorUserId)
                .authorName(resolveResponseAuthorName(board.getAuthorName(), authorUserId))
                .title(board.getTitle())
                .text(board.getText())
                .regDate(board.getRegDate())
                .updateDate(board.getUpdateDate())
                .commentCount(commentsSnapshot.getCommentCount())
                .editable(canModify(actor, board.getUserId()))
                .deletable(canModify(actor, board.getUserId()))
                .comments(commentsSnapshot.getComments())
                .build();
    }

    private BoardCommentsSnapshotResponseDto toCommentsSnapshot(
            Long boardId,
            List<BoardCommentEntity> comments,
            BoardActor actor
    ) {
        return toCommentsSnapshot(
                boardId,
                comments,
                actor,
                resolveAuthorUserIdMap(extractAuthorUserPks(comments))
        );
    }

    private BoardCommentsSnapshotResponseDto toCommentsSnapshot(
            Long boardId,
            List<BoardCommentEntity> comments,
            BoardActor actor,
            Map<Long, String> authorUserIdMap
    ) {
        return BoardCommentsSnapshotResponseDto.builder()
                .boardId(boardId)
                .commentCount(comments.size())
                .comments(buildCommentTree(comments, actor, authorUserIdMap))
                .build();
    }

    private List<BoardCommentResponseDto> buildCommentTree(
            List<BoardCommentEntity> comments,
            BoardActor actor,
            Map<Long, String> authorUserIdMap
    ) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<BoardCommentEntity>> childrenMap = buildChildrenMap(comments);
        return buildChildren(null, childrenMap, actor, authorUserIdMap);
    }

    private Map<Long, List<BoardCommentEntity>> buildChildrenMap(List<BoardCommentEntity> comments) {
        Map<Long, List<BoardCommentEntity>> childrenMap = new LinkedHashMap<>();
        for (BoardCommentEntity comment : comments) {
            childrenMap.computeIfAbsent(comment.getParentId(), key -> new ArrayList<>()).add(comment);
        }
        return childrenMap;
    }

    private List<BoardCommentResponseDto> buildChildren(
            Long parentId,
            Map<Long, List<BoardCommentEntity>> childrenMap,
            BoardActor actor,
            Map<Long, String> authorUserIdMap
    ) {
        List<BoardCommentEntity> children = childrenMap.getOrDefault(parentId, Collections.emptyList());
        List<BoardCommentResponseDto> response = new ArrayList<>();
        for (BoardCommentEntity child : children) {
            response.add(toCommentResponse(
                    child,
                    actor,
                    buildChildren(child.getId(), childrenMap, actor, authorUserIdMap),
                    authorUserIdMap
            ));
        }
        return response;
    }

    private BoardCommentResponseDto toCommentResponse(
            BoardCommentEntity comment,
            BoardActor actor,
            List<BoardCommentResponseDto> children,
            Map<Long, String> authorUserIdMap
    ) {
        String authorUserId = resolveAuthorUserId(comment.getUserId(), authorUserIdMap);
        return BoardCommentResponseDto.builder()
                .id(comment.getId())
                .parentId(comment.getParentId())
                .depth(comment.getDepth())
                .authorUserId(authorUserId)
                .authorName(resolveResponseAuthorName(comment.getAuthorName(), authorUserId))
                .content(comment.getContent())
                .regDate(comment.getRegDate())
                .updateDate(comment.getUpdateDate())
                .editable(canModify(actor, comment.getUserId()))
                .deletable(canModify(actor, comment.getUserId()))
                .children(children)
                .build();
    }

    private List<Long> extractAuthorUserPks(List<?> sources) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        for (Object source : sources) {
            if (source instanceof BoardEntity board && board.getUserId() != null) {
                userIds.add(board.getUserId());
            }
            if (source instanceof BoardCommentEntity comment && comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }
        }
        return new ArrayList<>(userIds);
    }

    private List<Long> extractAuthorUserPks(BoardEntity board, List<BoardCommentEntity> comments) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (board.getUserId() != null) {
            userIds.add(board.getUserId());
        }
        for (BoardCommentEntity comment : comments) {
            if (comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }
        }
        return new ArrayList<>(userIds);
    }

    private Map<Long, String> resolveAuthorUserIdMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> authorUserIdMap = new LinkedHashMap<>();
        for (UserEntity user : userRepository.findAllById(userIds)) {
            if (StringUtils.hasText(user.getUserId())) {
                authorUserIdMap.put(user.getId(), user.getUserId());
            }
        }
        return authorUserIdMap;
    }

    private String resolveAuthorUserId(Long userPk, Map<Long, String> authorUserIdMap) {
        if (userPk == null) {
            return null;
        }
        return authorUserIdMap.get(userPk);
    }

    private String resolveResponseAuthorName(String storedAuthorName, String authorUserId) {
        if (StringUtils.hasText(authorUserId)) {
            return authorUserId;
        }
        return storedAuthorName;
    }

    private void collectCommentIds(
            Long commentId,
            Map<Long, List<BoardCommentEntity>> childrenMap,
            List<Long> deleteIds
    ) {
        for (BoardCommentEntity child : childrenMap.getOrDefault(commentId, Collections.emptyList())) {
            collectCommentIds(child.getId(), childrenMap, deleteIds);
        }
        deleteIds.add(commentId);
    }

    private BoardPaginationResponseDto toPagination(Page<BoardEntity> boardPage) {
        int totalPages = boardPage.getTotalPages();
        int currentPage = boardPage.getNumber() + 1;

        if (totalPages == 0) {
            return BoardPaginationResponseDto.builder()
                    .page(currentPage)
                    .size(boardPage.getSize())
                    .totalElements(boardPage.getTotalElements())
                    .totalPages(0)
                    .hasPreviousPage(false)
                    .hasNextPage(false)
                    .groupStartPage(0)
                    .groupEndPage(0)
                    .hasPreviousGroup(false)
                    .hasNextGroup(false)
                    .build();
        }

        int currentGroupIndex = (currentPage - 1) / PAGE_GROUP_SIZE;
        int groupStartPage = currentGroupIndex * PAGE_GROUP_SIZE + 1;
        int groupEndPage = Math.min(groupStartPage + PAGE_GROUP_SIZE - 1, totalPages);

        Integer previousPage = boardPage.hasPrevious() ? currentPage - 1 : null;
        Integer nextPage = boardPage.hasNext() ? currentPage + 1 : null;
        Integer previousGroupPage = groupStartPage > 1 ? groupStartPage - 1 : null;
        Integer nextGroupPage = groupEndPage < totalPages ? groupEndPage + 1 : null;

        return BoardPaginationResponseDto.builder()
                .page(currentPage)
                .size(boardPage.getSize())
                .totalElements(boardPage.getTotalElements())
                .totalPages(totalPages)
                .hasPreviousPage(boardPage.hasPrevious())
                .hasNextPage(boardPage.hasNext())
                .previousPage(previousPage)
                .nextPage(nextPage)
                .groupStartPage(groupStartPage)
                .groupEndPage(groupEndPage)
                .hasPreviousGroup(previousGroupPage != null)
                .hasNextGroup(nextGroupPage != null)
                .previousGroupPage(previousGroupPage)
                .nextGroupPage(nextGroupPage)
                .firstPage(1)
                .lastPage(totalPages)
                .build();
    }

    private record AuthorInfo(Long userId, String authorName) {
    }

    private record BoardActor(Long userId, String role) {

        private static BoardActor guest() {
            return new BoardActor(null, null);
        }

        private boolean authenticated() {
            return userId != null;
        }

        private boolean privileged() {
            return role != null && PRIVILEGED_ROLES.contains(role);
        }
    }
}
