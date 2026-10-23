package com.tastedna.session.api;

import com.tastedna.session.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// API 처리 중 발생한 주요 예외를 공통 JSON 오류 응답으로 변환한다.
// HTTP 상태 코드와 오류 식별자를 전달해 외부 클라이언트가 실패 원인을 구분할 수 있게 한다.
@RestControllerAdvice
public class SessionExceptionHandler {

    // 서비스에서 발생한 ResponseStatusException을 ErrorResponse로 변환한다.
    // 예외의 HTTP 상태 코드는 유지하고, 오류 식별자에 맞는 사용자 안내 문구를 제공한다.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception) {

        String code = exception.getReason();

        if (code == null || code.isBlank()) {
            code = "REQUEST_FAILED";
        }

        // 내부 오류 식별자를 사용자에게 표시할 안내 문구로 변환한다.
        // 등록되지 않은 식별자는 기본 메시지로 처리하되 원래 오류 코드는 유지한다.
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

            case "RAW_OBSERVATION_NOT_READY" ->
                    "이 세션에는 아직 분석을 지원하지 않는 질문이나 관측 규칙이 포함되어 있습니다.";
            default ->
                    "요청을 처리할 수 없습니다.";
        };

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ErrorResponse(code, message));
    }

    // 요청 본문의 JSON을 읽을 수 없거나 입력 형식을 해석하지 못한 경우를 처리한다.
    // 클라이언트에 HTTP 400과 INVALID_REQUEST_BODY 오류를 반환한다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_REQUEST_BODY",
                        "요청 본문의 JSON 형식이나 입력값이 올바르지 않습니다."
                ));
    }

    // 요청 객체의 필수값 및 유효성 검사 실패를 공통 오류 응답으로 변환한다.
    // HTTP 400과 VALIDATION_FAILED를 반환해 입력 데이터 문제를 알린다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "VALIDATION_FAILED",
                        "필수 입력값이 누락되었거나 올바르지 않습니다."
                ));
    }
}
