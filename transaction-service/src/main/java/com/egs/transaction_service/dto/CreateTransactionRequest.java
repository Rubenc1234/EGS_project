package com.egs.transaction_service.dto;

public class CreateTransactionRequest {
    private String sagaId;
    private String userId;
    private Double amount;
    private String walletAddress;
    private String type;

    // Getters e Setters
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getWalletAddress() { return walletAddress; }
    public void setWalletAddress(String walletAddress) { this.walletAddress = walletAddress; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
