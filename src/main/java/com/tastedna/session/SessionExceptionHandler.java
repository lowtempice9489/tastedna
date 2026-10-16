
package com.tastedna.session;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class SessionExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<SessionDtos.ErrorResponse> handleResponseStatus(
            ResponseStatusException exception) {

        String code = exception.getReason();

        if (code == null || code.isBlank()) {
            code = "REQUEST_FAILED";
        }

        String message = switch (code) {
            case "ACTIVE_SESSION_EXISTS" ->
                    "이미 진행 중인 테스트가 있습니다.";

            case "ACTIVE_DEFINITION_NOT_FOUND" ->
                    "현재 이용 가능한 테스트가 없습니다.";

            case "QUESTION_SET_INCOMPLETE" ->
                    "테스트 질문 구성이 완료되지 않았습니다.";

            case "SESSION_NOT_FOUND" ->
                    "요청한 테스트 세션을 찾을 수 없습니다.";

            case "SESSION_DEFINITION_NOT_FOUND" ->
                    "테스트 정의 정보를 찾을 수 없습니다.";

            default ->
                    "요청을 처리할 수 없습니다.";
        };

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new SessionDtos.ErrorResponse(code, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SessionDtos.ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new SessionDtos.ErrorResponse(
                        "INVALID_REQUEST_BODY",
                        "요청 본문의 JSON 형식이나 입력값이 올바르지 않습니다."
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SessionDtos.ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new SessionDtos.ErrorResponse(
                        "VALIDATION_FAILED",
                        "필수 입력값이 누락되었거나 올바르지 않습니다."
                ));
    }
}
