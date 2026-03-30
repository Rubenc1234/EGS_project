package egs.transactions_service.blockchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock Blockchain Provider — Fase 1
 * 
 * Simula um blockchain local SEM Web3j/RPC.
 * Armazena tudo em memória com delays realistas.
 * 
 * Perfeito para testes sem dependências externas.
 * 
 * Ativa: spring.profiles.active=mock
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "blockchain.provider", havingValue = "mock", matchIfMissing = false)
public class MockBlockchainProvider implements BlockchainProvider {

    // Armazena balances (memória)
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    
    // Armazena transações
    private final Map<String, MockTransaction> transactions = new ConcurrentHashMap<>();
    
    // Armazena nonces por address
    private final Map<String, AtomicLong> nonces = new ConcurrentHashMap<>();
    
    // Pool para simular confirmações assíncronas
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    
    // Configuração
    private static final BigInteger MOCK_GAS_PRICE = BigInteger.valueOf(20_000_000_000L); // 20 Gwei
    private static final int CONFIRMATION_DELAY_SECONDS = 3; // Simula delay de ~3 blocos

    public MockBlockchainProvider() {
        log.info("🟢 MOCK BLOCKCHAIN PROVIDER INICIALIZADO (Fase 1)");
        log.info("   - Armazena em memória (sem Web3j/RPC)");
        log.info("   - Confirmações após {} segundos", CONFIRMATION_DELAY_SECONDS);
        log.info("   - Gas price: {} Gwei", MOCK_GAS_PRICE.divide(BigInteger.valueOf(1_000_000_000L)));
    }

    @Override
    public BigDecimal getBalance(String address) {
        String normalized = address.toLowerCase();
        BigDecimal balance = balances.getOrDefault(normalized, BigDecimal.ZERO);
        log.debug("   [MOCK] getBalance({}) = {}", address, balance);
        return balance;
    }

    @Override
    public String sendTransaction(String fromAddress, String toAddress, BigDecimal amount, String privateKey) {
        String normalizedFrom = fromAddress.toLowerCase();
        String normalizedTo = toAddress.toLowerCase();
        
        // Valida balance (simples check)
        BigDecimal currentBalance = getBalance(normalizedFrom);
        if (currentBalance.compareTo(amount) < 0) {
            log.error("   [MOCK] ❌ Insufficient balance: {} < {}", currentBalance, amount);
            throw new RuntimeException("Insufficient balance in mock blockchain");
        }

        // Gera hash fake mas realista (64 hex chars = 32 bytes)
        // Usa 2 UUIDs para ter 64 caracteres hexadecimais (UUID tem 32 hex chars)
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "") + 
                               UUID.randomUUID().toString().replace("-", "");
        
        // Incrementa nonce
        long nonce = nonces.computeIfAbsent(normalizedFrom, k -> new AtomicLong(0)).getAndIncrement();
        
        // Cria transação
        MockTransaction tx = new MockTransaction(
            txHash,
            normalizedFrom,
            normalizedTo,
            amount,
            nonce,
            System.currentTimeMillis(),
            MockTransaction.Status.PENDING
        );
        
        transactions.put(txHash, tx);
        
        log.info("   [MOCK] 📤 sendTransaction: {} EUR from {} to {}", amount, fromAddress, toAddress);
        log.info("   [MOCK]    Hash: {}", txHash);
        log.info("   [MOCK]    Nonce: {}", nonce);
        log.info("   [MOCK]    Confirmação em ~{} segundos...", CONFIRMATION_DELAY_SECONDS);
        
        // Deduz saldo imediatamente (simulando mempool)
        balances.put(normalizedFrom, currentBalance.subtract(amount));
        
        // Simula confirmação após CONFIRMATION_DELAY_SECONDS
        executor.schedule(() -> {
            MockTransaction pendingTx = transactions.get(txHash);
            if (pendingTx != null && pendingTx.status == MockTransaction.Status.PENDING) {
                pendingTx.status = MockTransaction.Status.CONFIRMED;
                pendingTx.blockNumber = System.currentTimeMillis() / 1000; // Fake block number
                
                // Creditação ao destinatário
                BigDecimal toBalance = balances.getOrDefault(normalizedTo, BigDecimal.ZERO);
                balances.put(normalizedTo, toBalance.add(amount));
                
                log.info("   [MOCK] ✅ CONFIRMADA: {} EUR de {} para {}", amount, fromAddress, toAddress);
            }
        }, CONFIRMATION_DELAY_SECONDS, TimeUnit.SECONDS);
        
        return txHash;
    }

    @Override
    public Optional<TransactionReceipt> getTransactionReceipt(String txHash) {
        MockTransaction tx = transactions.get(txHash);
        
        if (tx == null) {
            log.debug("   [MOCK] getTransactionReceipt({}) = NOT FOUND", txHash);
            return Optional.empty();
        }
        
        if (tx.status == MockTransaction.Status.PENDING) {
            log.debug("   [MOCK] getTransactionReceipt({}) = PENDING", txHash);
            return Optional.empty();
        }
        
        // Transação confirmada
        BlockchainProvider.TransactionReceipt receipt = new BlockchainProvider.TransactionReceipt(
            txHash,
            String.valueOf(tx.blockNumber),
            "0xmockblockhash",
            tx.from,
            tx.to,
            true,
            "21000"  // Gas estimado
        );
        
        log.debug("   [MOCK] getTransactionReceipt({}) = CONFIRMED", txHash);
        return Optional.of(receipt);
    }

    @Override
    public BigInteger getGasPrice() {
        log.debug("   [MOCK] getGasPrice() = {} Gwei", MOCK_GAS_PRICE.divide(BigInteger.valueOf(1_000_000_000L)));
        return MOCK_GAS_PRICE;
    }

    @Override
    public BigInteger getNonce(String address) {
        String normalized = address.toLowerCase();
        long nonce = nonces.getOrDefault(normalized, new AtomicLong(0)).get();
        log.debug("   [MOCK] getNonce({}) = {}", address, nonce);
        return BigInteger.valueOf(nonce);
    }

    @Override
    public boolean isAvailable() {
        return true; // Sempre disponível
    }

    @Override
    public String getProviderName() {
        return "MockBlockchain (Fase 1)";
    }

    /**
     * Modelo interno de transação.
     */
    private static class MockTransaction {
        enum Status { PENDING, CONFIRMED, FAILED }

        String hash;
        String from;
        String to;
        BigDecimal amount;
        long nonce;
        long timestamp;
        Status status;
        long blockNumber;

        MockTransaction(String hash, String from, String to, BigDecimal amount, long nonce, long timestamp, Status status) {
            this.hash = hash;
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.nonce = nonce;
            this.timestamp = timestamp;
            this.status = status;
            this.blockNumber = 0;
        }
    }

    /**
     * Debug: Ver estado do mock blockchain.
     */
    public void debug() {
        log.info("\n╔════════════════════════════════════════════╗");
        log.info("║  MOCK BLOCKCHAIN STATE                     ║");
        log.info("╠════════════════════════════════════════════╣");
        
        log.info("║ Wallets ({}):", balances.size());
        balances.forEach((addr, balance) -> 
            log.info("║   {} = {} EUR", addr, balance)
        );
        
        log.info("║");
        log.info("║ Transações ({}):", transactions.size());
        transactions.forEach((hash, tx) -> 
            log.info("║   {} ({}) = {} EUR", hash, tx.status, tx.amount)
        );
        
        log.info("╚════════════════════════════════════════════╝\n");
    }

    /**
     * Dev Helper: Adiciona fundos a uma wallet.
     */
    public void addFundsForTesting(String address, BigDecimal amount) {
        String normalized = address.toLowerCase();
        BigDecimal current = balances.getOrDefault(normalized, BigDecimal.ZERO);
        balances.put(normalized, current.add(amount));
        log.info("   [MOCK] 💰 Adicionados {} EUR a {}", amount, address);
    }

    /**
     * Dev Helper: Retorna o saldo REAL do MockBlockchain.
     */
    public BigDecimal getRealBalance(String address) {
        String normalized = address.toLowerCase();
        return balances.getOrDefault(normalized, BigDecimal.ZERO);
    }

    /**
     * Dev Helper: Retorna todos os balances.
     */
    public Map<String, BigDecimal> getAllBalances() {
        return new ConcurrentHashMap<>(balances);
    }
}
