package egs.transactions_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import egs.transactions_service.blockchain.BlockchainProvider;
import egs.transactions_service.blockchain.MockBlockchainProvider;
import egs.transactions_service.dto.*;
import egs.transactions_service.entity.UserWallet;
import egs.transactions_service.entity.Wallet;
import egs.transactions_service.repository.UserWalletRepository;
import egs.transactions_service.repository.WalletRepository;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.service.TransactionService;
import egs.transactions_service.service.TransactionWorker;
import egs.transactions_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175"}, allowCredentials = "true")
public class TransactionController {

    private final TransactionService transactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserWalletRepository userWalletRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionWorker transactionWorker;
    private final BlockchainProvider blockchainProvider;
    private final WalletService walletService;

    /**
     * Create or retrieve the wallet for the currently authenticated user.
     * Automatically generates and persists a wallet address if the user doesn't have one yet.
     * Uses the "sub" claim from the JWT token to identify the user.
     */
    @PostMapping("/users/me/wallet")
    public ResponseEntity<?> createOrGetMyWallet(@RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("=== POST /v1/users/me/wallet ENTRY === authHeader={}", authorization != null ? "Bearer [PRESENT]" : "MISSING");
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            log.warn("=== POST /v1/users/me/wallet REJECTED === reason=missing_or_invalid_authorization_header");
            return ResponseEntity.status(401).body(Map.of("error", "missing_authorization", "detail", "Authorization: Bearer <token> header required"));
        }
        try {
            String token = authorization.substring(7).trim();
            log.debug("=== JWT Token received, length={} ===", token.length());
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("=== JWT malformed, parts.length={} ===", parts.length);
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_token", "detail", "Malformed JWT"));
            }
            String payloadB64 = parts[1];
            // Base64 URL decode
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(payloadB64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
            log.info("=== JWT payload decoded === claims={}", payload.keySet());

            // Extract "sub" claim (Keycloak user ID)
            String sub = (String) payload.get("sub");
            if (sub == null || sub.isBlank()) {
                log.error("=== No 'sub' claim in token ===");
                return ResponseEntity.status(401).body(Map.of("error", "invalid_token", "detail", "Token missing 'sub' claim"));
            }
            log.info("=== User sub={} ===", sub);

            // Check if user already has a wallet
            var existing = userWalletRepository.findByKeycloakSub(sub);
            if (existing.isPresent()) {
                log.info("=== User already has wallet: {} ===", existing.get().getWalletAddress());
                BalanceDTO balance = transactionService.getBalance(existing.get().getWalletAddress());
                return ResponseEntity.ok(balance);
            }

            // Create new wallet using WalletService (generates and encrypts private key)
            log.info("=== Creating new wallet with encrypted private key ===");
            UserWallet newWallet = walletService.createWalletForUser(sub);
            String newWalletAddress = newWallet.getWalletAddress();
            log.info("=== Wallet created: {} with encrypted private key ===", newWalletAddress);

            // Get balance for the new wallet (will create Wallet entry if needed)
            BalanceDTO balance = transactionService.getBalance(newWalletAddress);
            log.info("=== POST /v1/users/me/wallet SUCCESS === wallet={} balance={} ===", newWalletAddress, balance.getBalance());
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException iae) {
            log.error("=== JWT decode failed === error={}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_token", "detail", iae.getMessage()));
        } catch (Exception e) {
            log.error("=== POST /v1/users/me/wallet ERROR === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    /**
     * Generate a deterministic wallet address from user's sub claim.
     * In production, this should integrate with a proper key management service.
     * For now, we use a simple hash-based approach for testing.
     */
    private String generateWalletAddress(String sub) {
        // Simple deterministic generation: hash the sub claim to create an address
        // In production, use a proper wallet/key management service
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sub.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Convert first 20 bytes to hex (Ethereum address format)
            StringBuilder address = new StringBuilder("0x");
            for (int i = 0; i < 20; i++) {
                address.append(String.format("%02x", hash[i] & 0xff));
            }
            return address.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback: generate a random address
            String hex = String.format("%040x", new java.util.Random().nextLong());
            return "0x" + hex.substring(0, 40);
        }
    }

    @GetMapping("/wallets/{wallet_id}/balance")
    public ResponseEntity<BalanceDTO> getBalance(@PathVariable("wallet_id") String walletId) {
        log.info("=== GET /v1/wallets/{}/balance ENTRY === walletId={}", walletId, walletId);
        try {
            BalanceDTO balance = transactionService.getBalance(walletId);
            log.info("=== GET /v1/wallets/{}/balance SUCCESS === balance={}", walletId, balance.getBalance());
            return ResponseEntity.ok(balance);
        } catch (Exception e) {
            log.error("=== GET /v1/wallets/{}/balance ERROR === error={}", walletId, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/users/me/wallet")
    public ResponseEntity<?> getMyWallet(@RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("=== GET /v1/users/me/wallet ENTRY === authHeader={}", authorization != null ? "Bearer [PRESENT]" : "MISSING");
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            log.warn("=== GET /v1/users/me/wallet REJECTED === reason=missing_or_invalid_authorization_header");
            return ResponseEntity.status(401).body(Map.of("error", "missing_authorization", "detail", "Authorization: Bearer <token> header required"));
        }
        try {
            String token = authorization.substring(7).trim();
            log.debug("=== JWT Token received, length={} ===", token.length());
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("=== JWT malformed, parts.length={} ===", parts.length);
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_token", "detail", "Malformed JWT"));
            }
            String payloadB64 = parts[1];
            // Base64 URL decode
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(payloadB64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
            log.info("=== JWT payload decoded === claims={}", payload.keySet());

            // Try common claim names
            String walletId = null;
            if (payload.containsKey("wallet")) {
                walletId = String.valueOf(payload.get("wallet"));
                log.info("=== Found wallet claim: {} ===", walletId);
            } else if (payload.containsKey("wallet_id")) {
                walletId = String.valueOf(payload.get("wallet_id"));
                log.info("=== Found wallet_id claim: {} ===", walletId);
            } else if (payload.containsKey("address")) {
                walletId = String.valueOf(payload.get("address"));
                log.info("=== Found address claim: {} ===", walletId);
            }

            if (walletId == null || walletId.isBlank()) {
                log.warn("=== No wallet claim found in token, available claims: {} ===", payload.keySet());
                return ResponseEntity.status(404).body(Map.of("error", "wallet_not_found_in_token", "detail", "Token does not contain a wallet claim. Consider adding 'wallet' claim or call /v1/wallets/{id}/balance directly."));
            }

            log.info("=== Delegating to getBalance({}), will query blockchain ===", walletId);
            // Delegate to existing service
            BalanceDTO balance = transactionService.getBalance(walletId);
            log.info("=== GET /v1/users/me/wallet SUCCESS === wallet={} balance={} ===", walletId, balance.getBalance());
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException iae) {
            log.error("=== JWT decode failed === error={}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_token", "detail", iae.getMessage()));
        } catch (Exception e) {
            log.error("=== GET /v1/users/me/wallet ERROR === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> createTransaction(
            @RequestBody TransactionRequestDTO request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("=== POST /v1/transactions ENTRY === request={}", request);
        try {
            // Extract wallet from JWT if fromWallet not provided
            if (request.getFromWallet() == null || request.getFromWallet().isBlank()) {
                if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
                    log.warn("=== POST /v1/transactions REJECTED === reason=missing_authorization_and_fromWallet");
                    return ResponseEntity.status(401).body(Map.of("error", "missing_authorization", "detail", "Authorization header required when fromWallet not specified"));
                }
                
                try {
                    String token = authorization.substring(7).trim();
                    String[] parts = token.split("\\.");
                    if (parts.length < 2) {
                        return ResponseEntity.badRequest().body(Map.of("error", "invalid_token", "detail", "Malformed JWT"));
                    }
                    
                    String payloadB64 = parts[1];
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(payloadB64);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
                    
                    // Extract "sub" claim (Keycloak user ID)
                    String sub = (String) payload.get("sub");
                    if (sub == null || sub.isBlank()) {
                        return ResponseEntity.status(401).body(Map.of("error", "invalid_token", "detail", "Token missing 'sub' claim"));
                    }
                    
                    // Find user's wallet
                    var userWallet = userWalletRepository.findByKeycloakSub(sub);
                    if (userWallet.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of("error", "wallet_not_found", "detail", "User has no wallet. Call POST /v1/users/me/wallet first"));
                    }
                    
                    request.setFromWallet(userWallet.get().getWalletAddress());
                    log.info("=== Extracted wallet from JWT: {} ===", request.getFromWallet());
                } catch (Exception e) {
                    log.error("=== JWT processing failed === error={}", e.getMessage());
                    return ResponseEntity.status(401).body(Map.of("error", "invalid_token", "detail", e.getMessage()));
                }
            }
            
            TransactionResponseDTO response = transactionService.createTransaction(request);
            log.info("=== POST /v1/transactions SUCCESS === tx_id={} status={}", response.getTxId(), response.getStatus());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            log.warn("=== POST /v1/transactions VALIDATION ERROR === error={}", iae.getMessage(), iae);
            return ResponseEntity.badRequest().body(Map.of("error", "validation_error", "detail", iae.getMessage()));
        } catch (Exception e) {
            log.error("=== POST /v1/transactions ERROR === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionListResponseDTO> listTransactions(
            @RequestParam(name = "wallet_id", required = false) String walletId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        TransactionListResponseDTO response = transactionService.listTransactions(walletId, status, limit, offset);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{tx_id}")
    public ResponseEntity<?> getTransaction(
            @PathVariable("tx_id") String txId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("=== GET /v1/transactions/{} ENTRY ===", txId);
        try {
            // Extract wallet from JWT to verify ownership
            String requesterWallet = null;
            if (authorization != null && authorization.toLowerCase().startsWith("bearer ")) {
                try {
                    String token = authorization.substring(7).trim();
                    String[] parts = token.split("\\.");
                    if (parts.length >= 2) {
                        String payloadB64 = parts[1];
                        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payloadB64);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = objectMapper.readValue(decoded, Map.class);
                        String sub = (String) payload.get("sub");
                        if (sub != null) {
                            var userWallet = userWalletRepository.findByKeycloakSub(sub);
                            if (userWallet.isPresent()) {
                                requesterWallet = userWallet.get().getWalletAddress();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not extract wallet from JWT: {}", e.getMessage());
                }
            }
            
            // Fetch transaction
            var transaction = transactionService.getTransactionById(txId, requesterWallet);
            if (transaction.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "not_found", "detail", "Transaction not found"));
            }
            
            log.info("=== GET /v1/transactions/{} SUCCESS ===", txId);
            return ResponseEntity.ok(transaction.get());
        } catch (Exception e) {
            log.error("=== GET /v1/transactions/{} ERROR === error={}", txId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    @PostMapping("/transactions/refund")
    public ResponseEntity<RefundResponseDTO> refundTransaction(@RequestBody RefundRequestDTO request) {
        RefundResponseDTO response = transactionService.refundTransaction(request);
        return ResponseEntity.ok(response);
    }

    /**
     * DEV ONLY: Add funds to a wallet for testing purposes.
     * This endpoint is for development/testing only and should be removed in production.
     * Usage: POST /v1/dev/wallet/{wallet_id}/fund?amount=100&asset=EUR
     */
    @PostMapping("/dev/wallet/{wallet_id}/fund")
    public ResponseEntity<?> devAddFunds(
            @PathVariable("wallet_id") String walletId,
            @RequestParam(name = "amount", defaultValue = "100") String amountStr,
            @RequestParam(name = "asset", defaultValue = "EUR") String asset) {
        log.warn("=== DEV ENDPOINT: Adding funds === wallet={} amount={} asset={}", walletId, amountStr, asset);
        try {
            BigDecimal amount = new BigDecimal(amountStr);
            
            // Get or create wallet
            String normalizedWalletId = walletId.toLowerCase();
            Wallet wallet = walletRepository.findById(normalizedWalletId)
                    .orElseGet(() -> {
                        Wallet newWallet = Wallet.builder()
                                .address(normalizedWalletId)
                                .lastTokenBalance(BigDecimal.ZERO)
                                .build();
                        return walletRepository.save(newWallet);
                    });
            
            // Add funds to database
            BigDecimal currentBalance = wallet.getLastTokenBalance() != null ? wallet.getLastTokenBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = currentBalance.add(amount);
            wallet.setLastTokenBalance(newBalance);
            walletRepository.save(wallet);
            
            // ✅ FIX: Also add funds to MockBlockchainProvider in memory!
            if (blockchainProvider instanceof MockBlockchainProvider) {
                MockBlockchainProvider mock = (MockBlockchainProvider) blockchainProvider;
                mock.addFundsForTesting(normalizedWalletId, amount);
                log.warn("=== DEV: Funds added to MockBlockchainProvider === wallet={} amount={}", normalizedWalletId, amount);
            }
            
            log.warn("=== DEV: Funds added === wallet={} newBalance={}", walletId, newBalance);
            return ResponseEntity.ok(Map.of(
                    "message", "Funds added for development",
                    "wallet_id", walletId,
                    "amount_added", amount.toString(),
                    "new_balance", newBalance.toString(),
                    "asset", asset,
                    "blockchain_synced", blockchainProvider instanceof MockBlockchainProvider
            ));
        } catch (Exception e) {
            log.error("=== DEV: Error adding funds === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to add funds", "detail", e.getMessage()));
        }
    }

    /**
     * DEV ONLY: Retry a failed transaction
     * Usage: POST /v1/dev/transactions/{tx_id}/retry
     */
    @PostMapping("/dev/transactions/{tx_id}/retry")
    public ResponseEntity<?> retryFailedTransaction(@PathVariable("tx_id") String txId) {
        log.warn("=== DEV: Retrying failed transaction === tx_id={}", txId);
        try {
            var transaction = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Transaction not found: " + txId));
            
            if (transaction.getStatus() != egs.transactions_service.entity.Transaction.TransactionStatus.FAILED) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_status",
                        "detail", "Only FAILED transactions can be retried. Current status: " + transaction.getStatus()
                ));
            }
            
            log.warn("=== DEV: Resetting TX {} to PENDING for retry ===", txId);
            transaction.setStatus(egs.transactions_service.entity.Transaction.TransactionStatus.PENDING);
            transaction.setHash(null); // Clear hash for retry
            transaction.setUpdatedAt(java.time.OffsetDateTime.now());
            transactionRepository.save(transaction);
            
            // Manually trigger async processing
            log.warn("=== DEV: Triggering Worker for TX {} ===", txId);
            transactionWorker.processNewTransaction(
                    new egs.transactions_service.event.TransactionCreatedEvent(txId)
            );
            
            return ResponseEntity.ok(Map.of(
                    "message", "Transaction retry initiated",
                    "tx_id", txId,
                    "status", "PENDING",
                    "detail", "Transaction has been reset and async processing started"
            ));
        } catch (Exception e) {
            log.error("=== DEV: Error retrying transaction === tx_id={} error={}", txId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Retry failed", "detail", e.getMessage()));
        }
    }

    /**
     * Get the balance from the configured Blockchain Provider (Mock or Real).
     * Works with both MockBlockchainProvider and RealBlockchainProvider.
     * Usage: GET /v1/blockchain/{address}/balance
     */
    @CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175"}, allowCredentials = "true")
    @GetMapping("/blockchain/{address}/balance")
    public ResponseEntity<?> getWalletBalance(@PathVariable("address") String address) {
        log.info("=== GET /v1/blockchain/{}/balance ===", address);
        try {
            BigDecimal balance = blockchainProvider.getBalance(address);
            
            log.info("=== Wallet balance for {} = {} ===", address, balance);
            return ResponseEntity.ok(Map.of(
                "address", address,
                "balance", balance,
                "provider", blockchainProvider.getProviderName()
            ));
        } catch (Exception e) {
            log.error("=== Error getting wallet balance === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    /**
     * DEV ONLY: Get the REAL balance from MockBlockchain (not calculated from transactions).
     * Usage: GET /v1/dev/blockchain/balance/{address}
     */
    @GetMapping("/dev/blockchain/balance/{address}")
    public ResponseEntity<?> devGetBlockchainBalance(@PathVariable("address") String address) {
        log.info("=== DEV: Getting blockchain balance for {} ===", address);
        try {
            if (!(blockchainProvider instanceof MockBlockchainProvider)) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "not_mock_blockchain",
                    "detail", "This endpoint only works with MockBlockchainProvider. Current provider: " + blockchainProvider.getProviderName()
                ));
            }

            MockBlockchainProvider mock = (MockBlockchainProvider) blockchainProvider;
            BigDecimal balance = mock.getRealBalance(address);
            
            log.info("=== DEV: Blockchain balance for {} = {} ===", address, balance);
            return ResponseEntity.ok(Map.of(
                "address", address,
                "balance_eur", balance,
                "provider", "MockBlockchain",
                "note", "This is the REAL balance in MockBlockchain memory (not calculated from transaction history)"
            ));
        } catch (Exception e) {
            log.error("=== DEV: Error getting blockchain balance === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }

    /**
     * DEV ONLY: Get ALL balances from MockBlockchain.
     * Usage: GET /v1/dev/blockchain/balances
     */
    @GetMapping("/dev/blockchain/balances")
    public ResponseEntity<?> devGetAllBlockchainBalances() {
        log.info("=== DEV: Getting all blockchain balances ===");
        try {
            if (!(blockchainProvider instanceof MockBlockchainProvider)) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "not_mock_blockchain",
                    "detail", "This endpoint only works with MockBlockchainProvider. Current provider: " + blockchainProvider.getProviderName()
                ));
            }

            MockBlockchainProvider mock = (MockBlockchainProvider) blockchainProvider;
            Map<String, BigDecimal> allBalances = mock.getAllBalances();
            
            log.info("=== DEV: Found {} wallets in MockBlockchain ===", allBalances.size());
            return ResponseEntity.ok(Map.of(
                "wallets", allBalances,
                "total_wallets", allBalances.size(),
                "provider", "MockBlockchain",
                "note", "These are the REAL balances in MockBlockchain memory"
            ));
        } catch (Exception e) {
            log.error("=== DEV: Error getting all blockchain balances === error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error", "detail", e.getMessage()));
        }
    }
}
