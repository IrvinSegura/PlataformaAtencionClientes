package com.irvinsegura.supportchat.shared.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the errors that never reach a controller, such as a request the security firewall rejects, with the same
 * Problem Details body as every other error, in place of Spring Boot's own error page.
 */
// Not part of the API the panel is built on
@Hidden
@RestController
class ProblemErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        HttpStatus status = statusOf(request);
        ProblemDetail problem = status.is5xxServerError()
                ? Problems.create(status, "Internal server error", "An unexpected error occurred", "INTERNAL_ERROR")
                : Problems.create(status, status.getReasonPhrase(), "The request could not be processed",
                        status.name());
        // The request's own filters are done by now and cleared the log context, but its header is still set
        if (problem.getProperties() == null || problem.getProperties().get("correlationId") == null) {
            problem.setProperty("correlationId", response.getHeader(CorrelationIdFilter.HEADER));
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        // Opened directly, with no error behind it
        return status == null || status.value() < 400 ? HttpStatus.NOT_FOUND : status;
    }
}
