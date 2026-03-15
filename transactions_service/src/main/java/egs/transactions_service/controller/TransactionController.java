package egs.transactions_service.controller;

import egs.transactions_service.dto.*;
import egs.transactions_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/{wallet_id}/balance")
    public ResponseEntity<BalanceDTO> getBalance(@PathVariable("wallet_id") String walletId) {
        BalanceDTO balance = transactionService.getBalance(walletId);
        return ResponseEntity.ok(balance);
    }

    @PostMapping("/")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@RequestBody TransactionRequestDTO request) {
        TransactionResponseDTO response = transactionService.createTransaction(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/")
    public ResponseEntity<TransactionListResponseDTO> listTransactions(
            @RequestParam(name = "wallet_id", required = false) String walletId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        TransactionListResponseDTO response = transactionService.listTransactions(walletId, status, limit, offset);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponseDTO> refundTransaction(@RequestBody RefundRequestDTO request) {
        RefundResponseDTO response = transactionService.refundTransaction(request);
        return ResponseEntity.ok(response);
    }
}
