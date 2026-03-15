package egs.transactions_service.service;

import egs.transactions_service.dto.*;

public interface TransactionService {
    BalanceDTO getBalance(String walletId);
    TransactionResponseDTO createTransaction(TransactionRequestDTO request);
    TransactionListResponseDTO listTransactions(String walletId, String status, int limit, int offset);
    RefundResponseDTO refundTransaction(RefundRequestDTO request);
}
