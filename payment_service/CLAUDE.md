# Payment Service — Claude Context

## Stack

- **Backend:** Flask 3.1 · SQLAlchemy · PostgreSQL 15 · Stripe SDK · Keycloak OIDC · reportlab
- **Frontend:** React 18 · TypeScript · Vite · MUI · Stripe React SDK · Axios

## Como lançar

```bash
# 1. PostgreSQL + Keycloak (Docker)
docker compose up -d

# 2. Backend Flask (porta 5002)
python app_payment.py

# 3. Frontend (porta 5174)
cd frontend && npm run dev

# 4. Stripe CLI — reencaminha webhooks para o Flask
stripe listen --forward-to localhost:5002/v1/payments/webhook
```

## Portas

| Serviço | Porta |
|---|---|
| Flask backend | 5002 |
| Frontend Vite | 5174 |
| PostgreSQL | 5433 (mapeado de 5432) |
| Keycloak | 8083 (mapeado de 8080) |

## Arquitectura

Padrão MVC estrito: `Controller → Service → Repository → DB`

```
controllers/           Flask routes + validação de input
  auth_controller.py     @require_token decorator + rotas /v1/pay/*
  payment_controller.py  rotas /v1/payments/*

services/
  payment_service.py     lógica de negócio (create, get, get_user_payments, update_status, handle_webhook, generate_receipt)
  providers/
    base_provider.py     interface abstracta
    stripe_provider.py   integração Stripe (PaymentIntent + webhook signature)
    mock_provider.py     mock para testes

repository/
  payment_repository.py  CRUD na BD (save, find_by_id, find_by_user_id, find_by_stripe_intent_id, update)

models/
  payment.py             Payment model + PaymentStatus enum
```

## Endpoints

| Método | Path | Auth | Descrição |
|---|---|---|---|
| POST | `/v1/payments` | Bearer token | Cria pagamento via Stripe |
| GET | `/v1/payments?user_id=X` | Bearer token | Lista pagamentos de um utilizador |
| GET | `/v1/payments/<id>` | Bearer token | Consulta pagamento |
| PATCH | `/v1/payments/<id>` | Bearer token | Atualiza status (cancelled/pending/concluded) |
| GET | `/v1/payments/<id>/receipt` | Bearer token | Descarrega recibo em PDF (reportlab) |
| POST | `/v1/payments/webhook` | Stripe-Signature | Webhook Stripe (sem Bearer) |
| GET | `/v1/pay/login` | — | Devolve URL de login Keycloak |
| GET | `/v1/pay/signup` | — | Devolve URL de registo Keycloak |
| POST | `/v1/pay/callback` | — | Troca código por access token |

Swagger UI: `http://localhost:5002/payment/docs`

## Modelo Payment

```python
id                      # UUID string, primary key
user_id                 # string (JWT sub do Keycloak)
amount                  # float (EUR)
status                  # "pending" | "concluded" | "cancelled"
phone_number            # string, nullable
stripe_payment_intent_id  # pi_..., usado para lookup no webhook
stripe_client_secret    # devolvido ao frontend para confirmCardPayment
wallet_id               # destino do pagamento
redirect_url            # para onde redirecionar após pagamento
created_at              # datetime, preenchido automaticamente na criação
```

> **Nota de migração:** `created_at` foi adicionado após a criação inicial da tabela.
> Se a BD já existia, é necessário correr manualmente:
> ```sql
> ALTER TABLE payments ADD COLUMN created_at TIMESTAMP DEFAULT NOW();
> ```
> via `docker exec payment-postgres psql -U payuser -d payment_db -c "..."`

## Frontend — Páginas

| Rota | Componente | Descrição |
|---|---|---|
| `/` | `LoginPage` | Entrada — botão OAuth Keycloak |
| `/callback` | `CallbackPage` | Troca código por token, redireciona para `/pay` |
| `/pay` | `PaymentPage` | Formulário Stripe para criar pagamento |
| `/success` | `SuccessPage` | Confirmação de pagamento, redireciona após 4s |
| `/payments` | `PaymentsPage` | Histórico de pagamentos do utilizador |

## Frontend — API (`frontend/src/api.ts`)

Funções exportadas:

```typescript
getToken()                     // lê JWT do localStorage (valida expiração)
setToken(token)                // guarda JWT
clearToken()                   // remove JWT
getUserIdFromToken()           // decoda JWT e devolve o campo `sub`
fetchLoginUrl(redirectUri)     // GET /v1/pay/login
exchangeCode(code, redirectUri) // POST /v1/pay/callback
getUserPayments(userId)        // GET /v1/payments?user_id=X
cancelPayment(paymentId)       // PATCH /v1/payments/<id> com status=cancelled
createPayment(params)          // POST /v1/payments
downloadReceipt(paymentId)     // GET /v1/payments/<id>/receipt — faz fetch autenticado e força download do PDF
```

## Fluxo de pagamento

```
Frontend                     Backend                    Stripe
   │                            │                          │
   ├── POST /v1/payments ──────►│                          │
   │◄── {stripe_client_secret} ─┤                          │
   │                            │                          │
   ├── confirmCardPayment() ─────────────────────────────►│
   │◄── {status: succeeded} ─────────────────────────────┤│
   │                            │  POST /v1/payments/webhook
   ├── navigate('/success')     │◄─────────────────────────┤
                                │  verifica Stripe-Signature│
                                │  status → concluded       │
                                │  envia notificação        │
```

O frontend NÃO faz PATCH manual para `concluded` — o webhook é a única forma de marcar como `concluded`.
O frontend PODE fazer PATCH para `cancelled` (botão Cancelar na dashboard).
O frontend tem botão **"Recibo"** na dashboard para pagamentos `concluded` — chama `downloadReceipt()` que faz fetch autenticado e força download do PDF gerado pelo backend.

## Variáveis de ambiente (.env)

```
STRIPE_Secret_key=sk_test_...
STRIPE_Publishable_key=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
DATABASE_URL=postgresql://payuser:paypassword@localhost:5433/payment_db
PAYMENT_KEYCLOAK_URL=http://localhost:8083
PAYMENT_REALM=payments-realm
PAYMENT_CLIENT_ID=payments-client
PAYMENT_CLIENT_SECRET=
NOTIFICATIONS_BASE_URL=http://localhost:8082
NOTIFICATIONS_API_KEY=
```

## Padrões importantes

- **Webhook sem Bearer:** `POST /v1/payments/webhook` não usa `@require_token` — autenticação é feita pela assinatura HMAC no header `Stripe-Signature`
- **Stripe amounts em cêntimos:** `amount * 100` no `stripe_provider.py`
- **Proxy Vite:** todo o tráfego `/v1/*` do frontend é proxiado para `localhost:5002` — o axios usa baseURL relativa `''`
- **Notificações:** enviadas via `NotificationsClient` de `clients/` (package partilhado no repo), apenas quando status muda para `concluded`
- **user_id:** vem sempre do JWT `sub` (Keycloak UUID) — nunca hardcoded. O frontend usa `getUserIdFromToken()` para o obter
- **Cartão de teste Stripe:** `4242 4242 4242 4242` · data futura · qualquer CVC
- **Recibo PDF:** gerado em memória com `reportlab` em `generate_receipt()` — usa fontes built-in (Helvetica), euro escrito como `EUR` (não `€`) para evitar problemas de encoding
