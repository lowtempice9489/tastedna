package com.tastedna.session.persistence;

import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.SessionAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 세션의 질문별 답변을 저장하고 조회하는 JPA Repository다.
// SessionAnswer의 세션 ID와 질문 ID 복합 키를 사용한다.
public interface SessionAnswerRepository
        extends JpaRepository<SessionAnswer, SessionAnswerId> {

    // 지정한 세션에 저장된 답변을 질문 ID 오름차순으로 조회한다.
    // 실제 테스트 질문 순서는 SessionObservationService에서 별도로 정렬한다.
    List<SessionAnswer> findByIdSessionIdOrderByIdQuestionIdAsc(
            String sessionId
    );

}
