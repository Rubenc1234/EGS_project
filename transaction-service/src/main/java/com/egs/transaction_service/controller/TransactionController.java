package com.egs.transaction_service.controller;

import com.egs.transaction_service.dto.CreateTransactionRequest;
import com.egs.transaction_service.dto.TransactionResponse;
import com.egs.transaction_service.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    // POST /v1/transactions
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@RequestBody CreateTransactionRequest request) {
        TransactionResponse response = service.processFakeTransaction(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // GET /v1/transactions
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getHistory() {
        List<TransactionResponse> history = service.getAllTransactions();
        return ResponseEntity.ok(history);
    }
}