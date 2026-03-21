package egs.transactions_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1")
@Slf4j
// Allow frontend dev servers (5173 and 5175). In production, lock this down to your real origin(s).
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5175"})
public class AuthController {

    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @GetMapping("/login")
    public ResponseEntity<Void> getLoginUrl(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                            @RequestParam(value = "state", required = false) String clientState,
                                            HttpServletRequest request) {
        if (redirectUri == null || redirectUri.isEmpty()) {
            redirectUri = "http://localhost:5173/callback";
        }

        // Generate a secure random state per login attempt and store it in the user's session
        String generatedState = UUID.randomUUID().toString();
        HttpSession session = request.getSession(true);
        session.setAttribute("oidc_state", generatedState);

        // If client supplied an arbitrary `state`, keep it as auxiliary info
        if (clientState != null && !clientState.isEmpty()) {
            session.setAttribute("oidc_client_state", clientState);
        }

        // Remember where the frontend expects to end up after login (post-login redirect)
        session.setAttribute("post_login_redirect", redirectUri);

        // For robust server-side flow use the server callback as Keycloak's redirect_uri
        String serverCallback = "http://localhost:8081/v1/callback";

        // Force login prompt and require fresh authentication (avoid silent SSO)
        String loginUrl = String.format(
        "%s/realms/%s/protocol/openid-connect/auth?client_id=%s&response_type=code&redirect_uri=%s&state=%s&scope=openid%%20profile%%20email&prompt=login&max_age=0",
        keycloakUrl,
        URLEncoder.encode(realm, StandardCharsets.UTF_8),
        URLEncoder.encode(clientId, StandardCharsets.UTF_8),
        URLEncoder.encode(serverCallback, StandardCharsets.UTF_8),
        URLEncoder.encode(generatedState, StandardCharsets.UTF_8)
    );
        log.info("Redirecting user to Keycloak for login. callback={}", serverCallback);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create(loginUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Server-side callback: Keycloak will redirect the browser here with code & state.
     * We validate state, exchange the code for a token, then redirect the browser back to the frontend
     * with the token placed in the URL fragment (so frontend can read it without an extra POST).
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallbackGet(@RequestParam(value = "code", required = false) String code,
                                                  @RequestParam(value = "state", required = false) String returnedState,
                                                  HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expectedState = session != null ? (String) session.getAttribute("oidc_state") : null;
        if (expectedState == null || returnedState == null || !expectedState.equals(returnedState)) {
            log.warn("OIDC state mismatch: expected={} returned={}", expectedState, returnedState);
            String frontendErr = "http://localhost:5175/?error=invalid_state";
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(java.net.URI.create(frontendErr));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        if (code == null || code.isEmpty()) {
            String frontendErr = "http://localhost:5175/?error=code_required";
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(java.net.URI.create(frontendErr));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", "http://localhost:8081/v1/callback");

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map<String, Object>> res = rest.postForEntity(tokenUrl, tokenRequest, (Class) Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            String accessToken = null;
            Object expires = null;
            if (body != null) {
                accessToken = (String) body.get("access_token");
                expires = body.get("expires_in");
            }

            log.info("Successfully exchanged code for token (GET callback)." );
            String postLogin = session != null ? (String) session.getAttribute("post_login_redirect") : null;
            if (postLogin == null || postLogin.isEmpty()) {
                postLogin = "http://localhost:5175/";
            }

            StringBuilder sb = new StringBuilder(postLogin);
            if (!postLogin.contains("#")) sb.append('#'); else sb.append('&');
            if (accessToken != null) {
                sb.append("access_token=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
            }
            if (expires != null) {
                sb.append("&expires_in=").append(URLEncoder.encode(String.valueOf(expires), StandardCharsets.UTF_8));
            }

            HttpHeaders out = new HttpHeaders();
            out.setLocation(java.net.URI.create(sb.toString()));
            return new ResponseEntity<>(out, HttpStatus.FOUND);
        } catch (Exception ex) {
            log.error("Failed to exchange code for token (GET callback): {}", ex.getMessage());
            String frontendErr = "http://localhost:5175/?error=failed_to_exchange_code";
            HttpHeaders headersOut = new HttpHeaders();
            headersOut.setLocation(java.net.URI.create(frontendErr));
            return new ResponseEntity<>(headersOut, HttpStatus.FOUND);
        }
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody Map<String, String> payload,
                                                              HttpServletRequest request) {
        String code = payload.get("code");
        String redirectUri = payload.getOrDefault("redirect_uri", "http://localhost:5173/callback");
        String returnedState = payload.get("state");

        // Validate state against session-stored value
        HttpSession session = request.getSession(false);
        String expectedState = session != null ? (String) session.getAttribute("oidc_state") : null;
        if (expectedState == null || returnedState == null || !expectedState.equals(returnedState)) {
            log.warn("OIDC POST callback state mismatch: expected={} returned={}", expectedState, returnedState);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_state", "detail", "state mismatch or missing"));
        }

        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code required"));
        }

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, realm);
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(form, headers);

        try {
            log.info("Exchanging code for token (POST callback)");
            ResponseEntity<Map<String, Object>> res = rest.postForEntity(tokenUrl, tokenRequest, (Class) Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = res.getBody();
            Map<String, Object> out = new HashMap<>();
            if (body != null) {
                out.put("access_token", body.get("access_token"));
                out.put("expires_in", body.get("expires_in"));
            }
            log.info("Token exchange (POST) successful");
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            log.error("Token exchange (POST) failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "failed_to_exchange_code", "detail", ex.getMessage()));
        }
    }
}
