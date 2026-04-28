package io.github.gyulbbe.board.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.board.dto.BoardSearchType;
import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.entity.QBoardEntity;
import io.github.gyulbbe.user.entity.QUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BoardRepositoryImpl implements BoardRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QBoardEntity board = QBoardEntity.boardEntity;
    private final QUserEntity user = QUserEntity.userEntity;

    @Override
    public Page<BoardEntity> search(BoardSearchType searchType, String keyword, Pageable pageable) {
        List<BoardEntity> content = queryFactory
                .selectFrom(board)
                .leftJoin(user).on(user.id.eq(board.userId))
                .where(searchPredicate(searchType, keyword))
                .orderBy(board.regDate.desc(), board.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(board.count())
                .from(board)
                .leftJoin(user).on(user.id.eq(board.userId))
                .where(searchPredicate(searchType, keyword));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression searchPredicate(BoardSearchType searchType, String keyword) {
        if (searchType == null || !StringUtils.hasText(keyword)) {
            return null;
        }

        String trimmedKeyword = keyword.trim();

        return switch (searchType) {
            case USER_ID -> user.userId.containsIgnoreCase(trimmedKeyword)
                    .or(board.authorName.containsIgnoreCase(trimmedKeyword));
            case TITLE -> board.title.containsIgnoreCase(trimmedKeyword);
            case TEXT -> board.text.containsIgnoreCase(trimmedKeyword);
        };
    }
}
