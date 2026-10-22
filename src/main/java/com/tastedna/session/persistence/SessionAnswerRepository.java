package com.tastedna.session.persistence;

import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.SessionAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionAnswerRepository
        extends JpaRepository<SessionAnswer, SessionAnswerId> {

    List<SessionAnswer> findByIdSessionId(String sessionId);

}
