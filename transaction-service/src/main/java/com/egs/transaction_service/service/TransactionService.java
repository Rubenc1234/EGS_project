package com.egs.transaction_service.service;

import com.egs.transaction_service.dto.CreateTransactionRequest;
import com.egs.transaction_service.dto.TransactionResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    // Base de dados temporária
    private final List<TransactionResponse> mockDatabase = new ArrayList<>();

    public TransactionResponse processFakeTransaction(CreateTransactionRequest request) {
        System.out.println("Recebido pedido do Orquestrador. Saga ID: " + request.getSagaId());
        
        // Cria uma resposta de sucesso simulada
        TransactionResponse response = new TransactionResponse(request.getSagaId(), "SUCCESS");
        
        // Guarda em memória
        mockDatabase.add(response);
        
        return response;
    }

    public List<TransactionResponse> getAllTransactions() {
        // Devolve todas as transações que estão na lista
        return mockDatabase;
    }
}