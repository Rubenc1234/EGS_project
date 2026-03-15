package egs.transactions_service.service;

import egs.transactions_service.dto.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    @Override
    public BalanceDTO getBalance(String walletId) {
        return BalanceDTO.builder()
                .walletId(walletId)
                .symbol("ETH")
                .balance("1.50000000")
                .balanceInFiat(new BigDecimal("3500.50"))
                .currency("BRL")
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public TransactionResponseDTO createTransaction(TransactionRequestDTO request) {
        return TransactionResponseDTO.builder()
                .txId(UUID.randomUUID().toString())
                .hash("0x" + UUID.randomUUID().toString().replace("-", ""))
                .status("PENDING")
                .estimatedFee("0.0002")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public TransactionListResponseDTO listTransactions(String walletId, String status, int limit, int offset) {
        TransactionItemDTO item = TransactionItemDTO.builder()
                .txId("internal-uuid-001")
                .hash("0xabc" + UUID.randomUUID().toString().substring(0, 8))
                .from(walletId != null ? walletId : "0x123")
                .to("0x456")
                .amount("0.5")
                .status(status != null ? status : "CONFIRMED")
                .confirmedAt(OffsetDateTime.now())
                .build();

        return TransactionListResponseDTO.builder()
                .items(List.of(item))
                .total(150)
                .build();
    }

    @Override
    public RefundResponseDTO refundTransaction(RefundRequestDTO request) {
        return RefundResponseDTO.builder()
                .refundTxId("internal-uuid-" + UUID.randomUUID().toString().substring(0, 3))
                .originalTxId(request.getOriginalTxId() != null ? request.getOriginalTxId() : "internal-uuid-001")
                .status("INITIATED")
                .message("Refund transaction iniciated.")
                .build();
    }
}
