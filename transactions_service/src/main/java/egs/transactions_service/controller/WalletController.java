package egs.transactions_service.controller;

import egs.transactions_service.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Wallet Controller - Para user comum usar.
 * O frontend chama isto após fazer login.
 */
@RestController
@RequestMapping("/v1/wallet")
@Slf4j
public class WalletController {
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Inicializa wallet para user autenticado.
     * Se user não tem wallet, cria uma nova automaticamente com privada.
     * Se já tem, apenas retorna o address.
     * 
     * GET /v1/wallet/init
     * Header: Authorization: Bearer <JWT>
     * 
     * Response: { "address": "0x..." }
     */
    @GetMapping("/init")
    public ResponseEntity<?> initializeWallet(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "missing_authorization"
                ));
            }
            
            // Parse JWT manualmente
            String token = authorization.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_token"
                ));
            }
            
            // Descodifica payload
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
            String keycloakSub = (String) payload.get("sub");
            
            if (keycloakSub == null || keycloakSub.isBlank()) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "no_sub_claim"
                ));
            }
            
            log.info("📱 Init wallet para user: {}", keycloakSub);
            
            // Se não tiver wallet, cria uma nova
            var address = walletService.getPublicAddressForUser(keycloakSub);
            if (address.isEmpty()) {
                walletService.createWalletForUser(keycloakSub);
                address = walletService.getPublicAddressForUser(keycloakSub);
                log.info("   ✅ Wallet criada: {}", address.get());
            } else {
                log.info("   ✅ Wallet já existia: {}", address.get());
            }
            
            return ResponseEntity.ok(Map.of(
                "address", address.get(),
                "status", "ready"
            ));
        } catch (Exception e) {
            log.error("❌ Erro: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Ver endereço da wallet do user atual.
     * 
     * GET /v1/wallet/address
     * Header: Authorization: Bearer <JWT>
     */
    @GetMapping("/address")
    public ResponseEntity<?> getAddress(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
                return ResponseEntity.status(401).build();
            }
            
            // Parse JWT manualmente
            String token = authorization.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return ResponseEntity.badRequest().build();
            }
            
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
            String keycloakSub = (String) payload.get("sub");
            
            Optional<String> address = walletService.getPublicAddressForUser(keycloakSub);
            
            if (address.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "wallet_not_found"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "address", address.get()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}
