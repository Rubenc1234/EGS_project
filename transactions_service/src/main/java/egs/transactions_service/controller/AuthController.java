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

@RestController
@RequestMapping("/v1")
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
                                            @RequestParam(value = "state", required = false) String state) {
        if (redirectUri == null || redirectUri.isEmpty()) {
            redirectUri = "http://localhost:5173/callback";
        }
        if (state == null || state.isEmpty()) {
            state = "state123";
        }

    // Force login prompt and require fresh authentication (avoid silent SSO)
    String loginUrl = String.format(
        "%s/realms/%s/protocol/openid-connect/auth?client_id=%s&response_type=code&redirect_uri=%s&state=%s&scope=openid%%20profile%%20email&prompt=login&max_age=0",
        keycloakUrl,
        URLEncoder.encode(realm, StandardCharsets.UTF_8),
        URLEncoder.encode(clientId, StandardCharsets.UTF_8),
        URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
        URLEncoder.encode(state, StandardCharsets.UTF_8)
    );
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create(loginUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        String redirectUri = payload.getOrDefault("redirect_uri", "http://localhost:5173/callback");

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

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> res = rest.postForEntity(tokenUrl, request, Map.class);
            Map body = res.getBody();
            Map<String, Object> out = new HashMap<>();
            if (body != null) {
                out.put("access_token", body.get("access_token"));
                out.put("expires_in", body.get("expires_in"));
            }
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "failed_to_exchange_code", "detail", ex.getMessage()));
        }
    }
}
