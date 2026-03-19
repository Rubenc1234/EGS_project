# User Stories - EGS Project (wallet, payments, transactions, notifications)

## Epic: Onboarding & Wallet

### US-001: User signup via Keycloak (OIDC)
- Actor: End user (browser)
- Description: Como utilizador quero criar uma conta usando Keycloak (signup / social login) para poder aceder à minha wallet.
- Acceptance Criteria:
  - Frontend redireciona o utilizador para Keycloak (Composer realm) para signup/login.
  - Após autenticação o Composer recebe o token e cria automaticamente uma wallet com saldo 0 para o utilizador.
  - Utilizador é redirecionado para dashboard com wallet id visível.

### US-002: User login
- Actor: End user
- Description: Como utilizador quero fazer login para ver o meu saldo e histórico de transacções.
- Acceptance Criteria:
  - Login via Keycloak (Composer realm) ou sessão persistente via cookie segura.
  - Composer valida token e retorna dados do user/profile e wallet.

## Epic: Payments (separado)

### US-010: Make a payment (via Payment UI)
- Actor: End user (via Payment Frontend)
- Description: Como utilizador quero iniciar um pagamento para comprar um activo/crYPTO e ver o resultado no meu dashboard.
- Acceptance Criteria:
  - O Payment UI autentica o utilizador no Keycloak (Payments realm).
  - Payment Service processa a transacção e notifica o Composer (POST /v1/composer/transactions) com auth apropriado (service token in env for Composer).
  - Composer persiste a transacção no ledger e atualiza o saldo.
  - Composer envia notificação ao utilizador via Notifications Service.

### US-011: Payment history
- Actor: End user
- Description: Ver o histórico de pagamentos e estados das transacções.
- Acceptance Criteria:
  - Composer expõe endpoint para listar transacções do utilizador (com paginação).
  - UI apresenta histórico com filtros por estado/data.

## Epic: Notifications

### US-020: Receive real-time event notifications
- Actor: End user (browser)
- Description: Enquanto estiver autenticado quero receber notificações em tempo real sobre o status das minhas transacções.
- Acceptance Criteria:
  - Browser abre SSE para Composer `/v1/composer/events/{user_id}`.
  - Composer autoriza/valida a ligação (via sessão) e inscreve o cliente.
  - Composer envia evento a Notifications Service quando uma transacção muda de estado e o utilizador recebe a mensagem.

## Non-functional & Security
- NF-001: Service-to-service tokens
  - Cada serviço tem tokens/credentials configurados (em env or vault). Composer terá tokens para contactar Notifications e (se necessário) outros serviços.
- NF-002: Data protection
  - NIF e dados sensíveis não devem ser armazenados em texto simples. Validação externa necessária para NIF se exigir compliance.


## Notes / Implementation decisions (current)
- Separate Keycloak realms used: one for Composer (users+wallet) and one for Payments (payments users). Payments users are a distinct identity model.
- Signup flow chosen: redirect to Keycloak (OIDC) for signup/login.
- Service tokens: Composer receives tokens via environment variables to authenticate against Notifications and other services; Payment Service not required to accept user tokens for Composer backed ledger — Payment Service calls Composer to record transactions.


---

_Pronto. Se quiseres, exporto os diagramas para PNG/SVG e adiciono um README com instruções para visualizar os Mermaid files (ex.: usar VS Code Mermaid preview ou gerar com mermaid-cli)._