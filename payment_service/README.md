# Payment Service

Serviço de pagamentos com autenticação Keycloak, processamento via Stripe, OTP por WhatsApp (Twilio) e geração de recibos PDF.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Flask 3.1 · SQLAlchemy · PostgreSQL 15 |
| Pagamentos | Stripe SDK |
| Autenticação | Keycloak OIDC |
| OTP | Twilio WhatsApp sandbox |
| Recibos | reportlab |
| Frontend | React 18 · TypeScript · Vite · MUI · Stripe React SDK |

## Pré-requisitos

- Docker + Docker Compose
- Python 3.11+
- Node.js 18+
- [Stripe CLI](https://stripe.com/docs/stripe-cli) (para webhooks em desenvolvimento)
- Ficheiro `.env` preenchido (ver secção [Variáveis de ambiente](#variáveis-de-ambiente))

## Como correr

A partir da directoria `payment_service/`, abre 4 terminais:

```bash
# Terminal 1 — PostgreSQL + Keycloak
docker compose up -d

# Terminal 2 — Backend Flask (porta 5002)
python app_payment.py

# Terminal 3 — Frontend Vite (porta 5174)
cd frontend && npm run dev

# Terminal 4 — Stripe webhooks
stripe listen --forward-to localhost:5002/v1/payments/webhook
```

A aplicação fica disponível em `http://localhost:5174`.

## Portas

| Serviço | Porta |
|---|---|
| Frontend (Vite) | 5174 |
| Backend (Flask) | 5002 |
| PostgreSQL | 5433 (mapeado de 5432) |
| Keycloak | 8083 (mapeado de 8080) |

Swagger UI: `http://localhost:5002/payment/docs`

## Endpoints

### Pagamentos

| Método | Path | Auth | Descrição |
|---|---|---|---|
| POST | `/v1/payments` | Bearer token | Cria pagamento via Stripe |
| GET | `/v1/payments?user_id=X` | Bearer token | Lista pagamentos do utilizador |
| GET | `/v1/payments/<id>` | Bearer token | Consulta pagamento |
| PATCH | `/v1/payments/<id>` | Bearer token | Atualiza status (`cancelled` / `pending` / `concluded`) |
| GET | `/v1/payments/<id>/receipt` | Bearer token | Descarrega recibo em PDF |
| POST | `/v1/payments/<id>/send-otp` | Bearer token | Gera e envia OTP via WhatsApp |
| POST | `/v1/payments/<id>/verify` | Bearer token | Valida código OTP |
| POST | `/v1/payments/webhook` | Stripe-Signature | Webhook Stripe (sem Bearer) |
| GET | `/v1/payments/stats` | Bearer token + role `operator` | Estatísticas agregadas do serviço |

### Autenticação

| Método | Path | Auth | Descrição |
|---|---|---|---|
| GET | `/v1/pay/login` | — | Devolve URL de login Keycloak |
| GET | `/v1/pay/signup` | — | Devolve URL de registo Keycloak |
| POST | `/v1/pay/callback` | — | Troca código OAuth por access token |

### Utilizadores

| Método | Path | Auth | Descrição |
|---|---|---|---|
| GET | `/v1/users/profile` | Bearer token | Devolve perfil (telefone + cartões guardados) |
| PUT | `/v1/users/profile` | Bearer token | Atualiza número de telefone |
| POST | `/v1/users/cards` | Bearer token | Guarda cartão Stripe (PaymentMethod) |
| DELETE | `/v1/users/cards/<id>` | Bearer token | Remove cartão guardado |

## Fluxo de pagamento

```
Frontend                     Backend                    Stripe / Twilio
   │                            │                          │
   ├── POST /v1/payments ──────►│── PaymentIntent.create ─►│
   │◄── {stripe_client_secret} ─┤◄─────────────────────────┤
   │                            │                          │
   ├── confirmCardPayment() ──────────────────────────────►│
   │◄── {status: succeeded} ────────────────────────────────┤
   │                            │                          │
   ├── POST /send-otp ─────────►│── twilio.send() ─────────►│ WhatsApp
   │◄── {sent: true} ───────────┤                          │
   │                            │                          │
   ├── [utilizador insere OTP]  │                          │
   ├── POST /verify ───────────►│── SHA256 compare         │
   │◄── {verified: true} ───────┤                          │
   │                            │                          │
   ├── navigate('/success')     │  POST /webhook ◄─────────┤ Stripe
                                │  status → concluded       │
```

**Notas:**
- O status `concluded` é definido exclusivamente pelo webhook Stripe — nunca pelo frontend.
- Se o utilizador não tiver telefone, `/send-otp` retorna `422 no_phone_number` e o frontend salta o OTP.
- O OTP expira em 5 minutos e fica inválido após uma verificação bem-sucedida.

## Cartão de teste Stripe

```
Número:     4242 4242 4242 4242
Validade:   qualquer data futura
CVC:        qualquer 3 dígitos
```

## Estatísticas do serviço

O endpoint `GET /v1/payments/stats` devolve métricas agregadas calculadas on-the-fly a partir da base de dados. Requer a role Keycloak `operator`.

### 1. Criar a role e atribuir ao utilizador

1. Abre `http://localhost:8083` e faz login como `admin`
2. Seleciona o realm **payments-realm** (dropdown no topo esquerdo)
3. Sidebar → **Realm roles** → **Create role** → nome: `operator` → **Save**
4. Sidebar → **Users** → escolhe o utilizador → aba **Role mapping** → **Assign role** → seleciona `operator` → **Assign**

### 2. Aumentar a validade do token (recomendado para testes)

Por defeito os access tokens expiram em 5 minutos.

1. **Realm settings** → aba **Tokens**
2. **Access Token Lifespan** → `30 minutes` → **Save**

### 3. Obter o token

Faz login em `http://localhost:5174` com o utilizador com role `operator`, abre as DevTools do browser e vai a **Application → Local Storage → http://localhost:5174**. Copia o valor da chave `payment_token`.

Em alternativa, obtém o token directamente via curl (substituir `<password>`):

```bash
curl -s -X POST http://localhost:8083/realms/payments-realm/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=payments-client" \
  -d "client_secret=<PAYMENT_CLIENT_SECRET>" \
  -d "username=operator" \
  -d "password=<password>" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
```

### 4. Chamar o endpoint

```bash
# fish shell
set TOKEN "<access_token>"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:5002/v1/payments/stats | python3 -m json.tool

# bash/zsh
TOKEN="<access_token>"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:5002/v1/payments/stats | python3 -m json.tool
```

### Estrutura da resposta

```json
{
  "overview": {
    "total_users": 42,
    "total_payments": 318,
    "total_revenue_eur": 9540.50,
    "avg_transaction_amount": 29.99,
    "min_transaction_amount": 5.00,
    "max_transaction_amount": 500.00,
    "avg_payments_per_user": 7.57
  },
  "payments_by_status": {
    "pending": 12,
    "concluded": 290,
    "cancelled": 16,
    "success_rate_pct": 94.8
  },
  "daily_trends_last_30_days": [
    { "date": "2026-04-01", "count": 14, "revenue": 420.00 }
  ],
  "cards": {
    "total_saved_cards": 65,
    "users_with_saved_cards": 38,
    "avg_cards_per_user": 1.71,
    "card_brand_distribution": { "visa": 40, "mastercard": 25 },
    "most_popular_brand": "visa"
  },
  "user_profiles": {
    "total_profiles": 42,
    "users_with_phone": 30,
    "users_with_stripe_customer": 38
  },
  "otps": {
    "total_otps_sent": 210,
    "total_otps_verified": 198,
    "otp_success_rate_pct": 94.3
  },
  "activity_patterns": {
    "payments_by_hour": { "9": 45, "10": 62, "14": 58 },
    "payments_by_weekday": { "Monday": 70, "Tuesday": 65 }
  }
}
```

**Respostas de erro:**
- `401` — token inválido ou expirado
- `403` — token válido mas sem role `operator`

## Variáveis de ambiente

Cria um ficheiro `.env` na raiz de `payment_service/`:

```env
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
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_FROM_NUMBER=whatsapp:+14155238886
```

> **Twilio sandbox:** antes de receber mensagens, o número de destino tem de enviar `join <palavra>` para `+1 415 523 8886` no WhatsApp.
