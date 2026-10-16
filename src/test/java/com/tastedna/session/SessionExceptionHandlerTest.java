
package com.tastedna.session;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

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
                HttpStatus.UNPROCESSABLE_ENTITY,
                "ACTIVE_DEFINITION_NOT_FOUND"
        );

        var response = handler.handleResponseStatus(exception);

        assertEquals(
                HttpStatus.UNPROCESSABLE_ENTITY,
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
