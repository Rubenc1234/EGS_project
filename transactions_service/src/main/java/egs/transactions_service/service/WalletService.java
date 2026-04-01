package egs.transactions_service.service;

import egs.transactions_service.entity.UserWallet;
import egs.transactions_service.repository.UserWalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Serviço para gerir wallets blockchain.
 * 
 * Responsabilidades:
 * - Gerar pares de chaves pública-privada
 * - Encriptar/desencriptar chaves privadas
 * - Guardar wallets no BD
 * - Recuperar wallets por user
 */
@Service
@Slf4j
public class WalletService {
    
    @Autowired
    private UserWalletRepository walletRepo;
    
    private static final String ALGORITHM = "AES";
    
    /**
     * Gets the master key from environment variable.
     * Falls back to default for development if not set.
     * IMPORTANTE: Em produção, esta chave deve vir de um vault seguro (AWS Secrets Manager, Vault, etc.)
     */
    private static String getMasterKey() {
        String key = System.getenv("MASTER_KEY_FOR_WALLET");
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("MASTER_KEY_FOR_WALLET environment variable not set!");
        }
        return key;
    }
    
    /**
     * Gera um novo par de chaves (público + privado).
     * A privada é encriptada com a master key antes de guardar.
     * 
     * @param keycloakSub ID do user do Keycloak
     * @return Wallet criada (só com endereço público, sem privada!)
     */
    public UserWallet createWalletForUser(String keycloakSub) {
        try {
            log.info("🔑 Gerando nova wallet para user: {}", keycloakSub);
            
            // 1. Verifica se user já tem wallet
            Optional<UserWallet> existing = walletRepo.findByKeycloakSub(keycloakSub);
            if (existing.isPresent()) {
                log.warn("   ⚠️ User já tem wallet: {}", existing.get().getWalletAddress());
                return existing.get();
            }
            
            // 2. Gera par de chaves
            ECKeyPair keyPair = Keys.createEcKeyPair();
            String publicAddress = "0x" + Keys.getAddress(keyPair);
            String privateKey = "0x" + keyPair.getPrivateKey().toString(16);
            
            log.info("   ✅ Public address:  {}", publicAddress);
            log.info("   ✅ Private key:     [ENCRYPTED]");
            
            // 3. Encripta privada
            String encryptedPrivate = encryptPrivateKey(privateKey);
            
            // 4. Guarda no BD
            UserWallet wallet = UserWallet.builder()
                .keycloakSub(keycloakSub)
                .walletAddress(publicAddress.toLowerCase())
                .privateKeyEncrypted(encryptedPrivate)
                .build();
            
            UserWallet saved = walletRepo.save(wallet);
            log.info("   💾 Wallet guardada no BD!");
            
            return saved;
        } catch (Exception e) {
            log.error("❌ Erro ao gerar wallet: {}", e.getMessage());
            throw new RuntimeException("Erro ao gerar wallet", e);
        }
    }
    
    /**
     * Recupera a privada de um user (desencriptada).
     * ⚠️ NUNCA enviar para o frontend!
     * 
     * @param keycloakSub ID do user do Keycloak
     * @return Privada desencriptada (para uso interno)
     */
    public String getPrivateKeyForUser(String keycloakSub) {
        try {
            Optional<UserWallet> wallet = walletRepo.findByKeycloakSub(keycloakSub);
            if (wallet.isEmpty()) {
                throw new RuntimeException("Wallet não encontrada para user: " + keycloakSub);
            }
            
            if (wallet.get().getPrivateKeyEncrypted() == null) {
                throw new RuntimeException("Privada não armazenada (wallet importada?)");
            }
            
            String privateKey = decryptPrivateKey(wallet.get().getPrivateKeyEncrypted());
            log.debug("🔓 Privada desencriptada para user: {}", keycloakSub);
            
            return privateKey;
        } catch (Exception e) {
            log.error("❌ Erro ao recuperar privada: {}", e.getMessage());
            throw new RuntimeException("Erro ao recuperar privada", e);
        }
    }
    
    /**
     * Recupera endereço público de um user.
     * 
     * @param keycloakSub ID do user do Keycloak
     * @return Endereço público (0x...)
     */
    public Optional<String> getPublicAddressForUser(String keycloakSub) {
        return walletRepo.findByKeycloakSub(keycloakSub)
            .map(UserWallet::getWalletAddress);
    }
    
    /**
     * Encripta uma chave privada com a master key.
     */
    private String encryptPrivateKey(String privateKey) throws Exception {
        SecretKey key = getSecretKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        byte[] encryptedBytes = cipher.doFinal(privateKey.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * Desencripta uma chave privada com a master key.
     */
    private String decryptPrivateKey(String encryptedKey) throws Exception {
        SecretKey key = getSecretKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedKey);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
    
    /**
     * Gera a secret key a partir da master key.
     */
    private SecretKey getSecretKey() throws Exception {
        byte[] keyBytes = getMasterKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length != 32 && keyBytes.length != 24 && keyBytes.length != 16) {
            throw new IllegalArgumentException("Master key deve ter 16, 24 ou 32 bytes");
        }
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);
    }
}
