package egs.transactions_service.service;

import egs.transactions_service.entity.TransactionRefund;
import egs.transactions_service.entity.Wallet;
import egs.transactions_service.repository.TransactionRefundRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for handling transaction refunds (fee returns)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {
    
    private final TransactionRefundRepository refundRepository;
    private final WalletRepository walletRepository;
    
    /**
     * Create a refund when a transaction fails and fees need to be returned
     */
    @Transactional
    public TransactionRefund createRefund(
            String transactionId,
            String userWallet,
            BigDecimal feeAmount,
            String asset,
            String reason) {
        
        log.info("=== Creating refund for transaction {} ===", transactionId);
        log.info("User wallet: {}, Fee to refund: {}, Asset: {}, Reason: {}", 
                 userWallet, feeAmount, asset, reason);
        
        // Create refund record
        TransactionRefund refund = TransactionRefund.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(transactionId)
                .toWallet(userWallet)
                .refundAmount(feeAmount)
                .asset(asset)
                .status(TransactionRefund.RefundStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .reason(reason)
                .build();
        
        refund = refundRepository.save(refund);
        log.info("✓ Refund record created with ID: {}", refund.getId());
        
        // Update wallet cache immediately (add fees back)
        log.info("=== Updating wallet cache to restore fee ===");
        Wallet wallet = walletRepository.findById(userWallet.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Wallet not found: " + userWallet));
        
        if ("EUR".equals(asset)) {
            wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(feeAmount));
            log.info("✓ Added {} EUR back to wallet cache", feeAmount);
        } else if ("ETH".equals(asset)) {
            wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(feeAmount));
            log.info("✓ Added {} ETH back to wallet cache", feeAmount);
        }
        
        walletRepository.save(wallet);
        log.info("✓ Wallet cache updated, refund PENDING");
        
        return refund;
    }
    
    /**
     * Mark a refund as completed
     */
    @Transactional
    public void markRefundCompleted(String refundId, String txHash) {
        log.info("=== Marking refund {} as COMPLETED ===", refundId);
        
        TransactionRefund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RuntimeException("Refund not found: " + refundId));
        
        refund.setStatus(TransactionRefund.RefundStatus.COMPLETED);
        refund.setRefundTxHash(txHash);
        refund.setCompletedAt(OffsetDateTime.now());
        
        refundRepository.save(refund);
        log.info("✓ Refund marked as COMPLETED with hash: {}", txHash);
    }
    
    /**
     * Mark a refund as failed
     */
    @Transactional
    public void markRefundFailed(String refundId, String failureReason) {
        log.info("=== Marking refund {} as FAILED ===", refundId);
        
        TransactionRefund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RuntimeException("Refund not found: " + refundId));
        
        refund.setStatus(TransactionRefund.RefundStatus.FAILED);
        refund.setReason(refund.getReason() + " | Refund failed: " + failureReason);
        
        refundRepository.save(refund);
        log.error("✗ Refund marked as FAILED: {}", failureReason);
    }
}
