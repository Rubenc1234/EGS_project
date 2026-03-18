package egs.transactions_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDTO {
    @NotBlank(message = "O ID da transação original é obrigatório")
    @JsonProperty("original_tx_id")
    private String originalTxId;

    @JsonProperty("reason")
    private String reason;
}
