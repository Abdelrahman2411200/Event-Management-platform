package com.eventplatform.auth.oauth;

import com.eventplatform.auth.api.AuthApiException;
import com.eventplatform.auth.api.RequestMetadata;
import com.eventplatform.auth.api.TokenResponse;
import com.eventplatform.auth.user.AuthenticationService;
import com.eventplatform.web.ApiErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;
    private final ApiErrorResponseWriter errorWriter;

    public OAuth2LoginSuccessHandler(
            AuthenticationService authenticationService,
            ObjectMapper objectMapper,
            ApiErrorResponseWriter errorWriter) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
        this.errorWriter = errorWriter;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = oauthToken.getPrincipal();
        String email = principal.getAttribute("email");
        Object verifiedClaim = principal.getAttribute("email_verified");
        if (verifiedClaim == null) {
            verifiedClaim = principal.getAttribute("verified_email");
        }
        boolean emailVerified = Boolean.TRUE.equals(verifiedClaim)
                || "true".equalsIgnoreCase(String.valueOf(verifiedClaim));
        try {
            TokenResponse tokens = authenticationService.oauthLogin(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    principal.getName(),
                    email,
                    emailVerified,
                    RequestMetadata.from(request));
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            objectMapper.writeValue(response.getOutputStream(), tokens);
        } catch (AuthApiException exception) {
            errorWriter.write(
                    request,
                    response,
                    exception.getStatus().value(),
                    exception.getStatus().getReasonPhrase(),
                    exception.getCode(),
                    exception.getMessage());
        } catch (RuntimeException exception) {
            RequestMetadata metadata = RequestMetadata.from(request);
            LOGGER.error(
                    "Unexpected OAuth completion failure correlationId={} type={}",
                    metadata.correlationId(),
                    exception.getClass().getSimpleName());
            errorWriter.write(
                    request,
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal Server Error",
                    "OAUTH_LOGIN_ERROR",
                    "OAuth login could not be completed");
        }
    }
}
