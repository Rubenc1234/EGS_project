package egs.transactions_service.service;

import egs.transactions_service.blockchain.BlockchainProvider;
import egs.transactions_service.entity.Transaction;
import egs.transactions_service.entity.Wallet;
import egs.transactions_service.event.TransactionCreatedEvent;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.TransactionFeeRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Worker — Processamento Assincronamente
 * 
 * Agora usa BlockchainProvider (Strategy Pattern).
 * Supports: MockBlockchain (dev), RealBlockchain (prod)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionWorker {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionFeeRepository transactionFeeRepository;
    private final BlockchainProvider blockchainProvider;
    private final KeyManagementService keyManagementService;
    private final NotificationService notificationService;
    
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, Object> transactionLocks = new ConcurrentHashMap<>();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processNewTransaction(TransactionCreatedEvent event) {
        String txId = event.getTransactionId();
        log.info("🔄 Worker acordou! A processar transação: {} com BlockchainProvider: {}", 
                txId, blockchainProvider.getProviderName());

        try {
            // 1. Busca detalhes da transação
            Transaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Transação não encontrada: " + txId));

            if (tx.getStatus() != Transaction.TransactionStatus.PENDING) {
                log.warn("   Transação {} não está PENDING. Status: {}", txId, tx.getStatus());
                return;
            }

            // 2. Envia para blockchain (Mock ou Real)
            log.info("   📤 Enviando {} {} de {} para {}", 
                    tx.getAmount(), tx.getAsset(), tx.getFromWallet(), tx.getToWallet());
            
            String privateKey = keyManagementService.getPrivateKeyForWallet(tx.getFromWallet());
            String txHash = blockchainProvider.sendTransaction(
                tx.getFromWallet(),
                tx.getToWallet(),
                tx.getAmount(),
                privateKey
            );
            
            log.info("   ✅ Transação enviada! Hash: {}", txHash);
            updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.BROADCASTED);
            
            // 3. Poll confirmação (com retry)
            pollTransactionConfirmation(txId, txHash);

        } catch (Exception e) {
            log.error("   ❌ Erro ao processar transação {}: {}", txId, e.getMessage(), e);
            finalizeFailedTransaction(txId, "Transaction failed before broadcast: " + e.getMessage());
        }
    }

    /**
     * Poll para confirmação de transação com logging detalhado.
     * Tenta a cada 5 segundos durante 5 minutos (60 tentativas).
     */
    private void pollTransactionConfirmation(String txId, String txHash) {
        final int MAX_ATTEMPTS = 60; // 60 * 5s = 5 minutos
        final int POLL_INTERVAL_SECONDS = 5;
        
        log.info("🔄 === INICIANDO POLL DE CONFIRMAÇÃO ===");
        log.info("   Transaction ID: {}", txId);
        log.info("   TX Hash: {}", txHash);
        log.info("   Max Tentativas: {}", MAX_ATTEMPTS);
        log.info("   Intervalo: {} segundos", POLL_INTERVAL_SECONDS);
        log.info("⏱️  Timeout total: {} minutos", (MAX_ATTEMPTS * POLL_INTERVAL_SECONDS) / 60);
        
        final int[] attemptCounter = {0};
        
        java.util.concurrent.ScheduledFuture<?> pollTask = executor.scheduleAtFixedRate(() -> {
            attemptCounter[0]++;
            
            log.info("🔍 Poll Tentativa {}/{} para TX: {}...", attemptCounter[0], MAX_ATTEMPTS, txHash.substring(0, 10) + "...");
            
            try {
                // Query blockchain para receipt
                Optional<BlockchainProvider.TransactionReceipt> receipt = blockchainProvider.getTransactionReceipt(txHash);
                
                if (receipt.isPresent()) {
                    log.warn("   🎯 Receipt ENCONTRADO!");
                    log.warn("   Status: {}", receipt.get().successful ? "SUCCESS ✅" : "FAILED ❌");
                    
                    // Transação CONFIRMADA
                    log.info("   ✅ Transação CONFIRMADA: {}", txHash);
                    finalizeConfirmedTransaction(txId, txHash);
                    log.info("✅ === POLL COMPLETADO COM SUCESSO ===\n");
                    throw new RuntimeException("Polling completed successfully");
                    
                } else {
                    log.debug("   ⏳ Sem receipt ainda. Tentativa {}/{}. Aguardando próxima verificação...", attemptCounter[0], MAX_ATTEMPTS);
                }
                
            } catch (RuntimeException e) {
                if ("Polling completed successfully".equals(e.getMessage())) {
                    throw e;  // Re-throw para parar o polling
                } else {
                    log.error("   ❌ Erro ao verificar receipt: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("   ❌ Erro inesperado no polling: {}", e.getMessage(), e);
            }
            
        }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Cancelar após MAX_ATTEMPTS (usar outro task para isso)
        executor.schedule(() -> {
            if (!pollTask.isDone()) {
                log.warn("⚠️  === POLL TIMEOUT ATINGIDO ===");
                log.warn("   Tentativas completadas: {}/{}", attemptCounter[0], MAX_ATTEMPTS);
                log.warn("   TX ID: {}", txId);
                log.warn("   TX Hash: {}", txHash);
                log.warn("   Motivo: Transação não confirmada em {} minutos", (MAX_ATTEMPTS * POLL_INTERVAL_SECONDS) / 60);
                
                pollTask.cancel(false);
                finalizeFailedTransaction(txId, "Broadcast polling timeout after " + ((MAX_ATTEMPTS * POLL_INTERVAL_SECONDS) / 60) + " minutes");
                
                log.warn("❌ === FIM DO POLL COM TIMEOUT ===\n");
            }
        }, MAX_ATTEMPTS * POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void finalizeConfirmedTransaction(String txId, String txHash) {
        synchronized (getTransactionLock(txId)) {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) {
                log.warn("   ⚠️ finalizeConfirmedTransaction: transaction {} not found", txId);
                return;
            }

            if (tx.getStatus() == Transaction.TransactionStatus.CONFIRMED) {
                log.info("   ℹ️ Transaction {} already CONFIRMED, skipping", txId);
                return;
            }
            if (tx.getStatus() == Transaction.TransactionStatus.FAILED) {
                log.warn("   ℹ️ Transaction {} already FAILED, skipping confirmation", txId);
                return;
            }

            updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.CONFIRMED);

            // Creditar receiver sempre
            creditReceiver(tx);

            // Fee só para transações normais
            if (tx.getType() == Transaction.TransactionType.TRANSFER) {
                transferFeeIfNeeded(tx);
            } else {
                log.info("   ℹ️ Refund transaction {}: fee is disabled", txId);
            }
        }
    }

    public void finalizeFailedTransaction(String txId, String reason) {
        synchronized (getTransactionLock(txId)) {
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx == null) {
                log.warn("   ⚠️ finalizeFailedTransaction: transaction {} not found", txId);
                return;
            }

            if (tx.getStatus() == Transaction.TransactionStatus.CONFIRMED) {
                log.warn("   ℹ️ Transaction {} already CONFIRMED, skipping failure handling", txId);
                return;
            }
            if (tx.getStatus() == Transaction.TransactionStatus.FAILED) {
                log.info("   ℹ️ Transaction {} already FAILED, skipping", txId);
                return;
            }

            updateTransactionStatus(txId, null, Transaction.TransactionStatus.FAILED);

            // If this was a refund, allow the original refund request to be retried
            if (tx.getType() == Transaction.TransactionType.REFUND && tx.getLinkedTxId() != null) {
                transactionRepository.findById(tx.getLinkedTxId()).ifPresent(originalTx -> {
                    originalTx.setRefunded(false);
                    transactionRepository.save(originalTx);
                    log.info("   ↩️ Original transaction {} refund flag reset", originalTx.getId());
                });
            }

            log.warn("   ❌ Transaction {} marked as FAILED: {}", txId, reason);
        }
    }

    private void creditReceiver(Transaction tx) {
        transactionRepository.findById(tx.getId()).ifPresent(currentTx -> {
            log.info("   💳 Processando crédito ao receiver: {}", currentTx.getToWallet());
            walletRepository.findById(currentTx.getToWallet().toLowerCase()).ifPresentOrElse(wallet -> {
                if ("EUR".equals(currentTx.getAsset())) {
                    wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(currentTx.getAmount()));
                    log.info("   ✅ Creditados {} {} (EUR) ao receiver", currentTx.getAmount(), currentTx.getAsset());
                } else if ("ETH".equals(currentTx.getAsset())) {
                    wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(currentTx.getAmount()));
                    log.info("   ✅ Creditados {} {} (ETH) ao receiver", currentTx.getAmount(), currentTx.getAsset());
                }
                walletRepository.save(wallet);
            }, () -> {
                Wallet newWallet = new Wallet();
                newWallet.setAddress(currentTx.getToWallet().toLowerCase());
                newWallet.setLastNativeBalance(BigDecimal.ZERO);
                newWallet.setLastTokenBalance(BigDecimal.ZERO);
                if ("EUR".equals(currentTx.getAsset())) {
                    newWallet.setLastTokenBalance(currentTx.getAmount());
                } else if ("ETH".equals(currentTx.getAsset())) {
                    newWallet.setLastNativeBalance(currentTx.getAmount());
                }
                walletRepository.save(newWallet);
                log.info("   ✅ Receiver wallet created and credited: {}", currentTx.getToWallet());
            });
        });
    }

    private void transferFeeIfNeeded(Transaction tx) {
        transactionFeeRepository.findFirstByTransactionId(tx.getId()).ifPresent(fee -> {
            if (fee.getFeeAmount() == null || fee.getFeeAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("   ⚠️ Fee inexistente ou zero para tx {}. Nada a transferir.", tx.getId());
                return;
            }

            if (fee.getRecipientAddress() == null || fee.getRecipientAddress().isBlank()) {
                log.warn("   ⚠️ Fee recipient em falta para tx {}. Nada a transferir.", tx.getId());
                return;
            }

            if (fee.isTransferredToRecipient()) {
                log.info("   ℹ️ Fee already transferred for tx {}", tx.getId());
                return;
            }

            try {
                String privateKey = keyManagementService.getPrivateKeyForWallet(tx.getFromWallet());
                log.info("   💸 Enviando fee {} {} para {}", fee.getFeeAmount(), fee.getAsset(), fee.getRecipientAddress());
                String feeTxHash = blockchainProvider.sendTransaction(
                        tx.getFromWallet(),
                        fee.getRecipientAddress(),
                        fee.getFeeAmount(),
                        privateKey
                );
                fee.setTransferredToRecipient(true);
                fee.setTransferTxHash(feeTxHash);
                transactionFeeRepository.save(fee);
                log.info("   ✅ Fee enviada! Hash: {}", feeTxHash);

                String normalizedFeeRecipient = fee.getRecipientAddress().toLowerCase();
                Wallet feeWallet = walletRepository.findById(normalizedFeeRecipient)
                        .orElseGet(() -> {
                            Wallet newWallet = new Wallet();
                            newWallet.setAddress(normalizedFeeRecipient);
                            newWallet.setLastNativeBalance(BigDecimal.ZERO);
                            newWallet.setLastTokenBalance(BigDecimal.ZERO);
                            return walletRepository.save(newWallet);
                        });

                if ("EUR".equals(tx.getAsset())) {
                    feeWallet.setLastTokenBalance(feeWallet.getLastTokenBalance().add(fee.getFeeAmount()));
                    log.info("   ✅ Creditados {} EUR ao fee recipient cache", fee.getFeeAmount());
                } else if ("ETH".equals(tx.getAsset())) {
                    feeWallet.setLastNativeBalance(feeWallet.getLastNativeBalance().add(fee.getFeeAmount()));
                    log.info("   ✅ Creditados {} ETH ao fee recipient cache", fee.getFeeAmount());
                }
                walletRepository.save(feeWallet);
            } catch (Exception feeError) {
                log.error("   ❌ Erro ao transferir fee para {}: {}", fee.getRecipientAddress(), feeError.getMessage(), feeError);
            }
        });
    }

    private Object getTransactionLock(String txId) {
        return transactionLocks.computeIfAbsent(txId, ignored -> new Object());
    }

    @Transactional
    protected void updateTransactionStatus(String txId, String hash, Transaction.TransactionStatus status) {
        transactionRepository.findById(txId).ifPresent(tx -> {
            log.info("   💾 Atualizando TX {} - Status: {} | Hash: {} → {}", txId, tx.getStatus(), tx.getHash(), hash);
            
            if (hash != null) {
                tx.setHash(hash);
                log.info("   ✏️ Hash definido para: {}", tx.getHash());
            }
            
            tx.setStatus(status);
            tx.setUpdatedAt(OffsetDateTime.now());
            
            Transaction saved = transactionRepository.save(tx);
            log.info("   ✅ TX salva no repo. Hash na BD agora: {}", saved.getHash());

            // Send notifications based on transaction status
            if (status == Transaction.TransactionStatus.BROADCASTED) {
                log.info("🔔 Sending BROADCASTED notifications for transaction {}", txId);
                // Notify sender that transaction was sent
                notificationService.notifyTransaction(
                    tx.getFromWallet(), 
                    "Transaction Sent", 
                    String.format("Your transaction of %s %s to %s has been sent to the blockchain.", tx.getAmount(), tx.getAsset(), tx.getToWallet()),
                    java.util.Map.of(
                        "transaction_id", txId,
                        "amount", tx.getAmount().toPlainString(),
                        "currency", tx.getAsset(),
                        "status", "broadcasted"
                    )
                );
            } else if (status == Transaction.TransactionStatus.CONFIRMED) {
                log.info("🔔 Sending CONFIRMED notifications for transaction {}", txId);
                // Notify sender
                notificationService.notifyTransactionCompleted(
                    tx.getFromWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset()
                );
                // Notify receiver
                notificationService.notifyTransactionCreated(
                    tx.getToWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset()
                );
            } else if (status == Transaction.TransactionStatus.FAILED) {
                log.info("🔔 Sending FAILED notifications for transaction {}", txId);
                // Notify sender about failure
                notificationService.notifyRefund(
                    tx.getFromWallet(), 
                    txId, 
                    tx.getAmount().toPlainString(), 
                    tx.getAsset(),
                    "Transaction failed"
                );
                // Refund the cache
                refundCache(tx);
            }
        });
    }

    private void refundCache(Transaction tx) {
        walletRepository.findById(tx.getFromWallet()).ifPresent(wallet -> {
            log.info("A devolver saldo à cache da carteira {} devido a falha...", tx.getFromWallet());
            if ("EUR".equals(tx.getAsset())) {
                wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
            } else {
                wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
            }
            walletRepository.save(wallet);
        });
    }
}
