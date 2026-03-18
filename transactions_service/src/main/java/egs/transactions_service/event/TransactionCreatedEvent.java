package egs.transactions_service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionCreatedEvent {
    private final String transactionId;
}
