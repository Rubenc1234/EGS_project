package egs.transactions_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceDTO {
    @JsonProperty("wallet_id")
    private String walletId;
    private String symbol;
    private String balance;
    @JsonProperty("native_balance")
    private String nativeBalance;
    @JsonProperty("native_symbol")
    private String nativeSymbol;
    @JsonProperty("balance_in_fiat")
    private BigDecimal balanceInFiat;
    private String currency;
    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private OffsetDateTime updatedAt;
}
