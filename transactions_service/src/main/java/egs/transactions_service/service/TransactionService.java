package egs.transactions_service.service;

import egs.transactions_service.dto.*;
import java.util.Optional;

public interface TransactionService {
    BalanceDTO getBalance(String walletId);
    TransactionResponseDTO createTransaction(TransactionRequestDTO request);
    TransactionListResponseDTO listTransactions(String walletId, String status, int limit, int offset);
    Optional<TransactionResponseDTO> getTransactionById(String txId, String requesterWallet);
    RefundResponseDTO refundTransaction(RefundRequestDTO request);
}
