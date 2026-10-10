package com.example.app.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Dados da requisicao atual (IP, user-agent, correlation id) para auditoria e controle de tentativas. */
@Component
public class RequestInfo {

    /** IP do cliente. Com FORWARD_HEADERS_STRATEGY=native o Tomcat ja resolve o X-Forwarded-For de proxies confiaveis. */
    public String ip() {
        HttpServletRequest r = current();
        return r == null ? null : r.getRemoteAddr();
    }

    public String userAgent() {
        HttpServletRequest r = current();
        return r == null ? null : r.getHeader("User-Agent");
    }

    public String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    private HttpServletRequest current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }
}
