package egs.transactions_service.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock Blockchain Provider — Fase 2
 * 
 * Simula um blockchain local SEM Web3j/RPC com:
 * ✅ Persistência em JSON (estado preservado entre restarts)
 * ✅ Realismo: 5% transações falham, 2% reorgs, confirmações variáveis (3-10s)
 * ✅ Thread-safe com ConcurrentHashMap
 * ✅ Perfeito para testes e desenvolvimento
 * 
 * Criada pelo BlockchainConfig (não é @Component)
 */
@Slf4j
public class MockBlockchainProvider implements BlockchainProvider {
    
    private static final String STATE_FILE = "/tmp/mock-blockchain-state.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    // Armazena balances (memória + persistência)
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    
    // Armazena transações
    private final Map<String, MockTransaction> transactions = new ConcurrentHashMap<>();
    
    // Armazena nonces por address
    private final Map<String, AtomicLong> nonces = new ConcurrentHashMap<>();
    
    // Pool para simular confirmações assíncronas
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    
    // Configuração
    private static final BigInteger MOCK_GAS_PRICE = BigInteger.valueOf(20_000_000_000L); // 20 Gwei
    private static final int CONFIRMATION_DELAY_MIN = 3;      // Mínimo 3 segundos
    private static final int CONFIRMATION_DELAY_MAX = 10;     // Máximo 10 segundos
    private static final double FAILURE_RATE = 0.05;          // 5% falhas
    private static final double REORG_RATE = 0.02;            // 2% reorgs

    public MockBlockchainProvider() {
        log.warn("🟢 MOCK BLOCKCHAIN PROVIDER INICIALIZADO (Fase 2 - Com Persistência + Realismo)");
        log.warn("   ✅ Persistência em JSON: {}", STATE_FILE);
        log.warn("   ✅ Realismo: 5% falhas, 2% reorgs, confirmações variáveis (3-10s)");
        log.warn("   ✅ Gas price: {} Gwei", MOCK_GAS_PRICE.divide(BigInteger.valueOf(1_000_000_000L)));
        
        // Carregar estado persistido
        loadStateFromFile();
    }
    
    /**
     * Carrega estado anterior do ficheiro JSON (se existir).
     */
    private synchronized void loadStateFromFile() {
        try {
            File file = new File(STATE_FILE);
            if (file.exists()) {
                log.info("   📂 Carregando estado do ficheiro: {}", STATE_FILE);
                String content = new String(Files.readAllBytes(Paths.get(STATE_FILE)));
                Map<String, Object> state = mapper.readValue(content, Map.class);
                
                // Carrega balances
                if (state.containsKey("balances")) {
                    Map<String, Object> loadedBalances = (Map<String, Object>) state.get("balances");
                    loadedBalances.forEach((addr, value) -> {
                        balances.put(addr, new BigDecimal(value.toString()));
                    });
                    log.info("   ✅ {} wallets carregadas", balances.size());
                }
                
                // Carrega nonces
                if (state.containsKey("nonces")) {
                    Map<String, Object> loadedNonces = (Map<String, Object>) state.get("nonces");
                    loadedNonces.forEach((addr, value) -> {
                        nonces.put(addr, new AtomicLong(((Number) value).longValue()));
                    });
                    log.info("   ✅ {} nonces carregados", nonces.size());
                }
                
                log.info("   💾 ✅ Estado restaurado com sucesso!");
            } else {
                log.info("   📂 Nenhum estado anterior encontrado (primeira execução)");
            }
        } catch (Exception e) {
            log.warn("   ⚠️ Erro ao carregar estado: {}", e.getMessage());
            // Continua com estado vazio
        }
    }
    
    /**
     * Salva estado atual no ficheiro JSON (persistência).
     */
    private synchronized void saveStateToFile() {
        try {
            Map<String, Object> state = new HashMap<>();
            
            // Serializa balances
            Map<String, String> balancesData = new HashMap<>();
            balances.forEach((addr, balance) -> balancesData.put(addr, balance.toPlainString()));
            state.put("balances", balancesData);
            
            // Serializa nonces
            Map<String, Long> noncesData = new HashMap<>();
            nonces.forEach((addr, atomicLong) -> noncesData.put(addr, atomicLong.get()));
            state.put("nonces", noncesData);
            
            // Timestamp
            state.put("lastSaved", System.currentTimeMillis());
            
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
            Files.write(Paths.get(STATE_FILE), json.getBytes());
        } catch (Exception e) {
            log.warn("   ⚠️ Erro ao salvar estado: {}", e.getMessage());
        }
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
        
        // Valida balance
        BigDecimal currentBalance = getBalance(normalizedFrom);
        if (currentBalance.compareTo(amount) < 0) {
            log.error("   [MOCK] ❌ Insufficient balance: {} < {}", currentBalance, amount);
            throw new RuntimeException("Insufficient balance in mock blockchain");
        }

        // Gera hash fake mas realista (64 hex chars)
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "") + 
                               UUID.randomUUID().toString().replace("-", "");
        
        // Incrementa nonce
        long nonce = nonces.computeIfAbsent(normalizedFrom, k -> new AtomicLong(0)).getAndIncrement();
        
        // Determina se esta transação vai falhar (5% chance)
        boolean willFail = Math.random() < FAILURE_RATE;
        
        // Determina se vai ter reorg (2% chance) - apenas se não falhar
        boolean willReorg = !willFail && Math.random() < REORG_RATE;
        
        // Tempo de confirmação variável (3-10 segundos)
        int confirmationDelaySeconds = CONFIRMATION_DELAY_MIN + 
                                      (int) (Math.random() * (CONFIRMATION_DELAY_MAX - CONFIRMATION_DELAY_MIN));
        
        // Cria transação
        MockTransaction tx = new MockTransaction(
            txHash,
            normalizedFrom,
            normalizedTo,
            amount,
            nonce,
            System.currentTimeMillis(),
            willFail ? MockTransaction.Status.FAILED : MockTransaction.Status.PENDING,
            willReorg
        );
        
        transactions.put(txHash, tx);
        
        log.info("   [MOCK] 📤 sendTransaction: {} EUR from {} to {}", amount, fromAddress, toAddress);
        log.info("   [MOCK]    Hash: {}", txHash);
        log.info("   [MOCK]    Nonce: {}", nonce);
        
        if (willFail) {
            log.warn("   [MOCK]    ⚠️ Status: FAILED (5% simulado)");
            // Devolve saldo ao sender (falha imediata)
            balances.put(normalizedFrom, currentBalance);
        } else if (willReorg) {
            log.warn("   [MOCK]    ⚠️ Status: Pendente, mas terá REORG depois");
            log.info("   [MOCK]    Confirmação em ~{} segundos (com reorg simulado)...", confirmationDelaySeconds);
            // Deduz saldo (será temporário se reorg)
            balances.put(normalizedFrom, currentBalance.subtract(amount));
            scheduleConfirmationWithReorg(txHash, normalizedFrom, normalizedTo, amount, confirmationDelaySeconds);
        } else {
            log.info("   [MOCK]    ✅ Status: PENDING → confirmação em ~{} segundos", confirmationDelaySeconds);
            // Deduz saldo imediatamente (simulando mempool)
            balances.put(normalizedFrom, currentBalance.subtract(amount));
            scheduleNormalConfirmation(txHash, normalizedFrom, normalizedTo, amount, confirmationDelaySeconds);
        }
        
        // Salva estado
        saveStateToFile();
        return txHash;
    }
    
    /**
     * Confirma transação normalmente.
     */
    private void scheduleNormalConfirmation(String txHash, String from, String to, BigDecimal amount, int delaySeconds) {
        executor.schedule(() -> {
            MockTransaction tx = transactions.get(txHash);
            if (tx != null && tx.status == MockTransaction.Status.PENDING) {
                tx.status = MockTransaction.Status.CONFIRMED;
                tx.blockNumber = System.currentTimeMillis() / 1000;
                
                // Creditação ao destinatário
                BigDecimal toBalance = balances.getOrDefault(to, BigDecimal.ZERO);
                balances.put(to, toBalance.add(amount));
                
                log.info("   [MOCK] ✅ CONFIRMADA: {} EUR de {} para {}", amount, from, to);
                saveStateToFile();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Confirma transação com REORG (falha depois de confirmar).
     */
    private void scheduleConfirmationWithReorg(String txHash, String from, String to, BigDecimal amount, int delaySeconds) {
        // Primeira confirmação
        executor.schedule(() -> {
            MockTransaction tx = transactions.get(txHash);
            if (tx != null && tx.status == MockTransaction.Status.PENDING) {
                tx.status = MockTransaction.Status.CONFIRMED;
                tx.blockNumber = System.currentTimeMillis() / 1000;
                
                BigDecimal toBalance = balances.getOrDefault(to, BigDecimal.ZERO);
                balances.put(to, toBalance.add(amount));
                
                log.info("   [MOCK] ✅ CONFIRMADA (temporária): {} EUR de {} para {}", amount, from, to);
                
                // Agenda reorg (falha após 5-8 segundos)
                int reorgDelaySeconds = 5 + (int) (Math.random() * 3);
                executor.schedule(() -> {
                    log.warn("   [MOCK] 🔄 REORG SIMULADO: {} - transação REVERSA!", txHash);
                    tx.status = MockTransaction.Status.FAILED;
                    
                    // Reverte balances (reorg)
                    BigDecimal senderBalance = balances.getOrDefault(from, BigDecimal.ZERO);
                    balances.put(from, senderBalance.add(amount));
                    
                    BigDecimal receiverBalance = balances.getOrDefault(to, BigDecimal.ZERO);
                    if (receiverBalance.compareTo(amount) >= 0) {
                        balances.put(to, receiverBalance.subtract(amount));
                    }
                    
                    log.warn("   [MOCK] 💔 Transação REVERTIDA por reorg");
                    saveStateToFile();
                }, reorgDelaySeconds, TimeUnit.SECONDS);
            }
        }, delaySeconds, TimeUnit.SECONDS);
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
        boolean canReorg;

        MockTransaction(String hash, String from, String to, BigDecimal amount, long nonce, long timestamp, Status status) {
            this(hash, from, to, amount, nonce, timestamp, status, false);
        }
        
        MockTransaction(String hash, String from, String to, BigDecimal amount, long nonce, long timestamp, Status status, boolean canReorg) {
            this.hash = hash;
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.nonce = nonce;
            this.timestamp = timestamp;
            this.status = status;
            this.blockNumber = 0;
            this.canReorg = canReorg;
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
     * Dev Helper: Reseta estado para testes (limpa balances, nonces, transações, JSON).
     */
    public synchronized void resetForTesting() {
        balances.clear();
        nonces.clear();
        transactions.clear();
        
        try {
            Files.deleteIfExists(Paths.get(STATE_FILE));
            log.info("   [TEST RESET] ✅ Estado reseta para testes");
        } catch (Exception e) {
            log.warn("   [TEST RESET] ⚠️ Erro ao limpar JSON: {}", e.getMessage());
        }
    }

    /**
     * Dev Helper: Adiciona fundos a uma wallet.
     */
    public void addFundsForTesting(String address, BigDecimal amount) {
        String normalized = address.toLowerCase();
        BigDecimal current = balances.getOrDefault(normalized, BigDecimal.ZERO);
        balances.put(normalized, current.add(amount));
        log.info("   [MOCK] 💰 Adicionados {} EUR a {}", amount, address);
        saveStateToFile();  // ← Persiste a mudança
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
