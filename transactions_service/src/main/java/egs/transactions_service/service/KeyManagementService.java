package egs.transactions_service.service;

import egs.transactions_service.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManagementService {

    private final UserWalletRepository userWalletRepository;
    
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
     * Retrieves and decrypts the private key for a wallet address.
     * The private key is stored encrypted in the database and only decrypted here.
     */
    public String getPrivateKeyForWallet(String walletAddress) {
        try {
            String normalizedAddress = walletAddress.toLowerCase();
            
            // Fetch encrypted private key from database
            var userWallet = userWalletRepository.findByWalletAddress(normalizedAddress)
                .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletAddress));
            
            String encryptedPrivateKey = userWallet.getPrivateKeyEncrypted();
            if (encryptedPrivateKey == null || encryptedPrivateKey.isEmpty()) {
                throw new RuntimeException("No encrypted private key found for wallet: " + walletAddress);
            }
            
            // Decrypt and return
            String decrypted = decryptPrivateKey(encryptedPrivateKey);
            log.debug("✅ Private key decrypted for wallet: {}", normalizedAddress);
            return decrypted;
            
        } catch (Exception e) {
            log.error("❌ Error retrieving private key for wallet {}: {}", walletAddress, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve private key", e);
        }
    }
    
    /**
     * Decrypts a private key using the master key
     */
    private String decryptPrivateKey(String encryptedKey) throws Exception {
        SecretKeySpec key = getSecretKey();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedKey);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Generates the secret key from the master key
     */
    private SecretKeySpec getSecretKey() throws Exception {
        byte[] keyBytes = getMasterKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length != 32 && keyBytes.length != 24 && keyBytes.length != 16) {
            throw new IllegalArgumentException("Master key must be 16, 24 or 32 bytes");
        }
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);
    }
}
