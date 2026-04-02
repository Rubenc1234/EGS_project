# EGS Project

Plataforma distribuida com arquitetura de microservicos para autenticacao, pagamentos, notificacoes em tempo real e transacoes.

## Visao geral

Este repositorio contem:

- Composer/BFF em Flask (entrypoint principal em `app.py`)
- IAM Service em Flask + Keycloak
- Payment Service em Flask + PostgreSQL
- Notifications Service em Go + PostgreSQL + Redis
- Transactions Service em Java/Spring + PostgreSQL
- Frontend principal em React + Vite
- Frontend de pagamentos em `payment_service/frontend`

Fluxo geral:

1. O frontend chama o Composer.
2. O Composer agrega e encaminha chamadas para IAM, Payment, Notifications e Transactions.
3. O IAM trata autenticacao/autorizacao (OIDC com Keycloak).

## Stack tecnologica

- Python 3 (Flask)
- Go (Gin)
- Java (Spring Boot)
- React + TypeScript + Vite
- PostgreSQL, Redis, Docker Compose

## Pre-requisitos

- Linux/macOS (ou WSL no Windows)
- Python 3.10+
- Node.js 18+
- Go 1.22+
- Java 17+
- Maven Wrapper (`transactions_service/mvnw`)
- Docker + Docker Compose

## Estrutura principal

```text
.
├── app.py                    # Composer/BFF
├── iam_service/              # IAM + Keycloak
├── payment_service/          # Pagamentos
├── notifications_service/    # Notificacoes (Go)
├── transactions_service/     # Transacoes (Java)
├── frontend/                 # Frontend principal
├── start_all.sh              # Arranque de servicos
└── stop_all.sh               # Paragem de servicos
```

## Setup rapido

### 1) Dependencias Python

Na raiz do projeto:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2) Dependencias frontend

```bash
cd frontend && npm install
cd ../payment_service/frontend && npm install
cd ../..
```

### 3) Variaveis de ambiente

O script `start_all.sh` espera um ficheiro `set_env.sh` na raiz.

Cria `set_env.sh` com pelo menos:

```bash
export MASTER_ADMIN_SECRET=master_key
export JWT_SECRET=jwt_key
export BANK_WALLET=bank-wallet-default
```

Se usares integracao Stripe no Payment Service, adiciona tambem as variaveis necessarias desse servico.

## Arranque do sistema

### Opcao A: script unico

```bash
./start_all.sh
```

Para parar tudo:

```bash
./stop_all.sh
```

Notas importantes:

- `start_all.sh` usa `venv/bin/activate`. Se o teu ambiente estiver em `.venv`, ajusta esse caminho no script.
- O Composer (`app.py`) esta comentado no `start_all.sh`; se precisares dele, executa manualmente (ver opcao B) ou remove os comentarios no script.

### Opcao B: arranque manual (desenvolvimento)

1. Infraestrutura:

```bash
(cd iam_service && docker-compose up -d)
(cd payment_service && docker-compose up -d)
(cd notifications_service && docker-compose up -d)
(cd transactions_service && docker-compose up -d)
```

2. Servicos:

```bash
# terminal 1
python3 -m iam_service.app_iam

# terminal 2
python3 -m payment_service.app_payment

# terminal 3
cd notifications_service && go run cmd/api/main.go

# terminal 4
cd transactions_service && ./mvnw spring-boot:run

# terminal 5 (raiz)
python3 app.py
```

3. Frontends:

```bash
# frontend principal
cd frontend && npm run dev -- --port 5175

# frontend pagamentos
cd payment_service/frontend && npm run dev -- --port 5174
```

## Enderecos locais (default)

- Keycloak IAM: http://localhost:8080
- IAM Service: http://localhost:5000
- Payment Service: http://localhost:5002
- Notifications Service: http://localhost:5003 (ou 8082 quando executado via docker-compose local do servico)
- Transactions Service: http://localhost:8081
- Composer: http://localhost:5001
- Frontend principal: http://localhost:5175
- Frontend pagamentos: http://localhost:5174

## Swagger / OpenAPI

- Composer: http://localhost:5001/composer/docs
- IAM: http://localhost:5000/iam/docs
- Payment: http://localhost:5002/payment/docs
- Notifications: http://localhost:8082/docs (quando executado pelo compose do servico)

## Endpoints principais do Composer

- `GET /v1/composer/login`
- `POST /v1/composer/login`
- `POST /v1/composer/callback`
- `PATCH /v1/composer/tokens`
- `POST /v1/composer/payments`
- `GET /v1/composer/payments/{payment_id}`
- `PATCH /v1/composer/payments/{payment_id}`
- `POST /v1/composer/transactions`
- `POST /v1/composer/notifications`
- `GET /v1/composer/events/{user_id}` (SSE)

## Testes

- Python:

```bash
pytest iam_service/tests payment_service/tests
```

- Notifications (HTTP scenarios):

Usar ficheiros em `notifications_service/tests/` no cliente HTTP da tua IDE.

- Scripts utilitarios:

```bash
./scripts/test_dev_funding.sh
./scripts/test_send_transaction.sh
```

## Troubleshooting

- Porta ocupada: usa `./stop_all.sh` e volta a arrancar.
- Docker sem permissao: adiciona o teu utilizador ao grupo docker ou usa `sudo`.
- Falhas no arranque do Go/Java: confirma versoes de Go e Java 17.
- Frontend sem comunicar com backend: valida portas e proxies Vite.

## Estado atual

Projeto em evolucao ativa. Alguns fluxos de integracao entre servicos podem estar em modo de desenvolvimento e sujeitos a ajuste.

