package com.kalyansky.pdf.web;

import com.kalyansky.pdf.convert.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/**
 * Turns failures into {@code {"error": "..."}} responses the UI can show. Lives in an advice
 * rather than the controller because upload-size errors are raised before the controller runs.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ConversionException.class)
    ResponseEntity<Map<String, String>> conversionFailed(ConversionException e) {
        if (!e.isUnsupportedFormat()) {
            log.warn("Conversion failed: {}", e.getMessage(), e.getCause());
        }
        HttpStatus status = switch (e.kind()) {
            case UNSUPPORTED_FORMAT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case UNREADABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return error(status, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Map<String, String>> missingFile() {
        return error(HttpStatus.BAD_REQUEST, "Choose a file to convert");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large (limit 20 MB)");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", message));
    }
}
