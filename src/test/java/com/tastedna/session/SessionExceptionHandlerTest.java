package com.tastedna.session;

import com.tastedna.session.api.SessionExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

// 서비스 오류가 API 응답에서 올바른 HTTP 상태와 오류 코드로 유지되는지 검증한다.
// 클라이언트가 오류 코드를 기준으로 분기할 수 있도록 응답 계약을 보호한다.
class SessionExceptionHandlerTest {

    private final SessionExceptionHandler handler =
            new SessionExceptionHandler();

    @Test
    void activeSessionReturns409() {

        var exception = new ResponseStatusException(
                HttpStatus.CONFLICT,
                "ACTIVE_SESSION_EXISTS"
        );

        var response = handler.handleResponseStatus(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "ACTIVE_SESSION_EXISTS",
                response.getBody().code()
        );
    }

    @Test
    void missingDefinitionReturns422() {

        var exception = new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "ACTIVE_DEFINITION_NOT_FOUND"
        );

        var response = handler.handleResponseStatus(exception);

        assertEquals(
                HttpStatus.UNPROCESSABLE_CONTENT,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "ACTIVE_DEFINITION_NOT_FOUND",
                response.getBody().code()
        );
    }

    @Test
    void missingSessionReturns404() {

        var exception = new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "SESSION_NOT_FOUND"
        );

        var response = handler.handleResponseStatus(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "SESSION_NOT_FOUND",
                response.getBody().code()
        );
    }
}
