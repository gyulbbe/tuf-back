package io.github.gyulbbe.board.controller;

import io.github.gyulbbe.board.dto.BoardCommentCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardCommentUpdateRequestDto;
import io.github.gyulbbe.board.dto.BoardCommentsSnapshotResponseDto;
import io.github.gyulbbe.board.dto.BoardCreateRequestDto;
import io.github.gyulbbe.board.dto.BoardDetailResponseDto;
import io.github.gyulbbe.board.dto.BoardListResponseDto;
import io.github.gyulbbe.board.dto.BoardUpdateRequestDto;
import io.github.gyulbbe.board.service.BoardService;
import io.github.gyulbbe.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequiredArgsConstructor
@RequestMapping("/boards")
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public ResponseEntity<ResponseDto<BoardListResponseDto>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String keyword,
            Authentication authentication
    ) {
        return respond(boardService.listBoards(page, size, searchType, keyword, authentication));
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<ResponseDto<BoardDetailResponseDto>> getBoard(
            @PathVariable Long boardId,
            Authentication authentication
    ) {
        return respond(boardService.getBoard(boardId, authentication));
    }

    @PostMapping
    public ResponseEntity<ResponseDto<BoardDetailResponseDto>> createBoard(
            @RequestBody BoardCreateRequestDto requestDto,
            Authentication authentication
    ) {
        return respond(boardService.createBoard(requestDto, authentication));
    }

    @PutMapping("/{boardId}")
    public ResponseEntity<ResponseDto<BoardDetailResponseDto>> updateBoard(
            @PathVariable Long boardId,
            @RequestBody BoardUpdateRequestDto requestDto,
            Authentication authentication
    ) {
        return respond(boardService.updateBoard(boardId, requestDto, authentication));
    }

    @DeleteMapping("/{boardId}")
    public ResponseEntity<ResponseDto<Void>> deleteBoard(
            @PathVariable Long boardId,
            Authentication authentication
    ) {
        return respond(boardService.deleteBoard(boardId, authentication));
    }

    @GetMapping("/{boardId}/comments")
    public ResponseEntity<ResponseDto<BoardCommentsSnapshotResponseDto>> listComments(
            @PathVariable Long boardId,
            Authentication authentication
    ) {
        return respond(boardService.listComments(boardId, authentication));
    }

    @PostMapping("/{boardId}/comments")
    public ResponseEntity<ResponseDto<BoardCommentsSnapshotResponseDto>> createComment(
            @PathVariable Long boardId,
            @RequestBody BoardCommentCreateRequestDto requestDto,
            Authentication authentication
    ) {
        return respond(boardService.createComment(boardId, requestDto, authentication));
    }

    @PutMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<ResponseDto<BoardCommentsSnapshotResponseDto>> updateComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId,
            @RequestBody BoardCommentUpdateRequestDto requestDto,
            Authentication authentication
    ) {
        return respond(boardService.updateComment(boardId, commentId, requestDto, authentication));
    }

    @DeleteMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<ResponseDto<BoardCommentsSnapshotResponseDto>> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId,
            Authentication authentication
    ) {
        return respond(boardService.deleteComment(boardId, commentId, authentication));
    }

    private <T> ResponseEntity<ResponseDto<T>> respond(ResponseDto<T> responseDto) {
        return ResponseEntity.status(responseDto.getStatus()).body(responseDto);
    }
}
