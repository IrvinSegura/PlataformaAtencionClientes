package com.irvinsegura.supportchat.shared.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 Problem Details bodies carrying the stable {@code code} and the correlation id.
 */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail create(HttpStatusCode status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return withCode(problem, code);
    }

    public static ProblemDetail withCode(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
