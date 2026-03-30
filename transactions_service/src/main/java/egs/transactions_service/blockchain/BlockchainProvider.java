package egs.transactions_service.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Interface abstrata para blockchain provider.
 * Permite swapping entre Mock, Real Polygon, etc.
 * 
 * Strategy Pattern: Implementações diferentes sem mudar o código cliente.
 */
public interface BlockchainProvider {

    /**
     * Query balance de um endereço.
     * @param address Endereço da wallet
     * @return Balanço em EUR (token)
     */
    BigDecimal getBalance(String address);

    /**
     * Enviar transação para o blockchain.
     * @param fromAddress Endereço origem
     * @param toAddress Endereço destino
     * @param amount Quantidade a enviar
     * @param privateKey Chave privada para assinar
     * @return Hash da transação
     */
    String sendTransaction(String fromAddress, String toAddress, BigDecimal amount, String privateKey);

    /**
     * Poll status de uma transação.
     * @param txHash Hash da transação
     * @return Receipt se confirmada, vazio se ainda pendente
     */
    Optional<TransactionReceipt> getTransactionReceipt(String txHash);

    /**
     * Obter gas price atual.
     * @return Gas price em wei
     */
    BigInteger getGasPrice();

    /**
     * Obter nonce (número de transações prévias) para um endereço.
     * @param address Endereço
     * @return Nonce
     */
    BigInteger getNonce(String address);

    /**
     * Verificar se o blockchain está disponível.
     * @return true se disponível
     */
    boolean isAvailable();

    /**
     * Obter nome do provider (para logging).
     * @return Nome (ex: "MockBlockchain", "PolygonAmoy")
     */
    String getProviderName();

    /**
     * DTO para transaction receipt.
     */
    class TransactionReceipt {
        public String transactionHash;
        public String blockNumber; // null se pendente
        public String blockHash;
        public String from;
        public String to;
        public boolean successful;
        public String gasUsed;

        public TransactionReceipt(String hash, String blockNumber, String blockHash, String from, String to, boolean successful, String gasUsed) {
            this.transactionHash = hash;
            this.blockNumber = blockNumber;
            this.blockHash = blockHash;
            this.from = from;
            this.to = to;
            this.successful = successful;
            this.gasUsed = gasUsed;
        }
    }
}
