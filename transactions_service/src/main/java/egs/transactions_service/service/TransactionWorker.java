package egs.transactions_service.service;

import egs.transactions_service.config.BlockchainConfig;
import egs.transactions_service.entity.Transaction;
import egs.transactions_service.event.TransactionCreatedEvent;
import egs.transactions_service.repository.TransactionRepository;
import egs.transactions_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionWorker {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final Web3j web3j;
    private final BlockchainConfig blockchainConfig;
    private final KeyManagementService keyManagementService;
    private final NotificationService notificationService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processNewTransaction(TransactionCreatedEvent event) {
        String txId = event.getTransactionId();
        log.info("Worker acordou! A processar transação em background: {}", txId);

        try {
            // 1. Gets transaction details from DB
            Transaction tx = transactionRepository.findById(txId)
                    .orElseThrow(() -> new RuntimeException("Transação não encontrada: " + txId));

            if (tx.getStatus() != Transaction.TransactionStatus.PENDING) {
                log.warn("Worker: Transação {} não está em estado PENDING. Status atual: {}", txId, tx.getStatus());
                return;
            }

            // DEV MODE: Simular blockchain sem conectar realmente
            if (devMode) {
                log.warn("=== DEV MODE ATIVADO === Simulando envio para blockchain...");
                sendToBlockchainDev(txId, tx);
                return;
            }

            // Blockchain Logic
            log.info("A preparar envio de {} {} de {} para {}", 
                    tx.getAmount(), tx.getAsset(), tx.getFromWallet(), tx.getToWallet());
            
            // Credentials
            String privateKey = keyManagementService.getPrivateKeyForWallet(tx.getFromWallet());
            Credentials credentials = Credentials.create(privateKey);

            // Nonce
            EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                    tx.getFromWallet(), DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = ethGetTransactionCount.getTransactionCount();

            // Gas Price
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            // Create RawTransaction
            RawTransaction rawTransaction;
            if ("EUR".equals(tx.getAsset())) {
                // ERC20 transfer - typically needs more gas than native
                BigInteger gasLimit = BigInteger.valueOf(100000); 
                
                String contractAddress = blockchainConfig.getContract().getAddress();
                int decimals = blockchainConfig.getContract().getDecimals();
                BigInteger amountInUnits = tx.getAmount().multiply(BigDecimal.valueOf(10).pow(decimals)).toBigInteger();
                
                Function function = new Function(
                        "transfer",
                        Arrays.asList(new Address(tx.getToWallet()), new Uint256(amountInUnits)),
                        Collections.emptyList()
                );
                String data = FunctionEncoder.encode(function);
                rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, data);
            } else {
                // Native transfer (MATIC/ETH) - always 21000 gas
                BigInteger gasLimit = BigInteger.valueOf(21000);
                
                BigInteger valueInWei = Convert.toWei(tx.getAmount(), Convert.Unit.ETHER).toBigInteger();
                rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, tx.getToWallet(), valueInWei);
            }

            // Sign the transaction
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, blockchainConfig.getNode().getChainId(), credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            // Send the transaction
            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();

            if (response.hasError()) {
                throw new RuntimeException("Erro ao enviar para a Blockchain: " + response.getError().getMessage());
            }

            String txHash = response.getTransactionHash();

            // Atualizar a base de dados com a Hash da Blockchain
            updateTransactionStatus(txId, txHash, Transaction.TransactionStatus.BROADCASTED);
            
            log.info("Transação enviada para a rede com sucesso! Hash: {}", txHash);

        } catch (Exception e) {
            log.error("Erro critico ao enviar transação {} para a rede: {}", txId, e.getMessage());
            updateTransactionStatus(txId, null, Transaction.TransactionStatus.FAILED);
        }
    }

    /**
     * DEV MODE: Simula envio para blockchain sem realmente conectar.
     * Em produção, use o código real com Web3j.
     */
    private void sendToBlockchainDev(String txId, Transaction tx) {
        try {
            // Em dev mode, gerar um hash fake mas realista (0x + 64 hex chars)
            String uuidHex = UUID.randomUUID().toString().replace("-", "");
            String fakeHash = "0x" + uuidHex + uuidHex.substring(0, 64 - uuidHex.length());
            log.warn("DEV MODE: Simulando envio para blockchain. Tx {} com hash fake: {}", txId, fakeHash);
            
            // Simular sucesso na blockchain
            updateTransactionStatus(txId, fakeHash, Transaction.TransactionStatus.BROADCASTED);
            
            // Após 2s, simular confirmação
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    updateTransactionStatus(txId, fakeHash, Transaction.TransactionStatus.CONFIRMED);
                    log.info("DEV MODE: Transação {} confirmada com sucesso!", txId);
                    
                    // Atualizar saldo do receiver
                    walletRepository.findById(tx.getToWallet()).ifPresent(wallet -> {
                        log.info("DEV MODE: Creditando {} {} para receiver {}", tx.getAmount(), tx.getAsset(), tx.getToWallet());
                        if ("EUR".equals(tx.getAsset())) {
                            wallet.setLastTokenBalance(wallet.getLastTokenBalance().add(tx.getAmount()));
                        } else {
                            wallet.setLastNativeBalance(wallet.getLastNativeBalance().add(tx.getAmount()));
                        }
                        walletRepository.save(wallet);
                    });
                } catch (InterruptedException ie) {
                    log.error("DEV MODE: Thread interrompida", ie);
                }
            }).start();
            
        } catch (Exception e) {
            log.error("DEV MODE: Erro ao simular blockchain: {}", e.getMessage());
            updateTransactionStatus(txId, null, Transaction.TransactionStatus.FAILED);
        }
    }

    @Transactional
    protected void updateTransactionStatus(String txId, String hash, Transaction.TransactionStatus status) {
        transactionRepository.findById(txId).ifPresent(tx -> {
            if (hash != null) tx.setHash(hash);
            tx.setStatus(status);
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);

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
