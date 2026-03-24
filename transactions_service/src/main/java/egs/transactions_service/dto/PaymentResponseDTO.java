package egs.transactions_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {
    private String id;
    
    @JsonProperty("user_id")
    private String userId;
    
    private BigDecimal amount;
    
    private String currency;
    
    private String status;
    
    @JsonProperty("wallet_id")
    private String walletId;
    
    @JsonProperty("to_wallet")
    private String toWallet;
    
    @JsonProperty("redirect_url")
    private String redirectUrl;
    
    @JsonProperty("stripe_payment_intent_id")
    private String stripePaymentIntentId;
    
    @JsonProperty("stripe_client_secret")
    private String stripeClientSecret;

    public String getTargetWallet() {
        return walletId != null ? walletId : toWallet;
    }
}
