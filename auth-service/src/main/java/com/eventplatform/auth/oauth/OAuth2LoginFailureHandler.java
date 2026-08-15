package com.eventplatform.auth.oauth;

import com.eventplatform.auth.api.RequestMetadata;
import com.eventplatform.auth.audit.SecurityAuditService;
import com.eventplatform.auth.audit.SecurityEventType;
import com.eventplatform.web.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final SecurityAuditService auditService;
    private final ApiErrorResponseWriter errorWriter;

    public OAuth2LoginFailureHandler(SecurityAuditService auditService, ApiErrorResponseWriter errorWriter) {
        this.auditService = auditService;
        this.errorWriter = errorWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        RequestMetadata metadata = RequestMetadata.from(request);
        auditService.failure(
                SecurityEventType.OAUTH_LOGIN_FAILURE,
                null,
                null,
                null,
                metadata,
                "PROVIDER_AUTHENTICATION_FAILED");
        errorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "OAUTH_LOGIN_FAILED",
                "OAuth login failed");
    }
}
