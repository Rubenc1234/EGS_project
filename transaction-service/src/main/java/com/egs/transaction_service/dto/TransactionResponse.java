package com.egs.transaction_service.dto;

import java.util.UUID;

public class TransactionResponse {
    private String id;
    private String sagaId;
    private String txHash;
    private String status;

    public TransactionResponse(String sagaId, String status) {
        this.id = UUID.randomUUID().toString(); // Gera um ID falso
        this.sagaId = sagaId;
        this.status = status;
        this.txHash = "0x" + UUID.randomUUID().toString().replace("-", ""); // Finge que é um hash da blockchain
    }

    // Getters
    public String getId() { return id; }
    public String getSagaId() { return sagaId; }
    public String getTxHash() { return txHash; }
    public String getStatus() { return status; }
}