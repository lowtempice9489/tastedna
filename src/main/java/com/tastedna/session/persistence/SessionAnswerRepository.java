package com.tastedna.session.persistence;

import com.tastedna.session.domain.SessionAnswer;
import com.tastedna.session.domain.SessionAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionAnswerRepository
        extends JpaRepository<SessionAnswer, SessionAnswerId> {
}
