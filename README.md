# EGS Project

Plataforma distribuida para autenticacao, pagamentos, notificacoes e transacoes.

## Componentes

- `iam_service`: autenticacao, registo e Keycloak
- `payment_service`: criacao e validacao de pagamentos
- `notifications_service`: clientes, subscriptions e eventos em tempo real
- `transactions_service`: transacoes e wallet/blockchain
- `frontend`: interface principal
- `payment_service/frontend`: interface de pagamentos

## Fluxo atual

1. O frontend comunica com o Composer/BFF.
2. O Composer encaminha pedidos para IAM, Payment e Notifications.
3. O Payment Service conclui um pagamento e depois notifica o Transactions Service.
4. O Notifications Service usa um client criado em runtime para gerar a API key.

## Notifications

Antes de usar as subscriptions, cria o client no Notifications Service:

```bash
curl -s -X POST http://localhost:8082/v1/admin/clients \
	-H "Authorization: Bearer master_key" \
	-H "Content-Type: application/json" \
	-d '{
		"name": "name",
		"admin_email": "mail@gmail.com"
	}'
```

Esse endpoint devolve a API key do client e o VAPID necessários. Esta key deve ser usada no fluxo de notifications/subscriptions.

## Payments: utilizador necessário

Antes de usar o fluxo de pagamentos, cria um utilizador no Keycloak do Payments:

1. Abre http://localhost:8083/admin/master/console/#
2. Seleciona o realm `payments-realm`
3. Vai a `Users` e cria um utilizador
4. Em `Credentials`, define uma password para esse utilizador

## `set_env.sh`

O ficheiro deve seguir este formato:

```bash
export MASTER_KEY_SECRET="something"

# Notifications Service API Key
export NOTIFICATIONS_API_KEY="your_notifications_api_key"

# Notifications: Admin Secret
export MASTER_ADMIN_SECRET=something

# Notifications: JWT Secret
export JWT_SECRET=something

# Wallet Encryption: Master Key para encriptar chaves privadas
export MASTER_KEY_FOR_WALLET="something"
```

## Arranque rápido

Antes de arrancar a UI, instala as dependências dos dois frontends:

```bash
cd frontend && npm install
cd ../payment_service/frontend && npm install
cd ../..
```

```bash
./start_all.sh
```

Para parar:

```bash
./stop_all.sh
```

## URLs locais

- Keycloak: http://localhost:8080
- IAM: http://localhost:5000
- Payment: http://localhost:5002
- Notifications: http://localhost:8082
- Transactions: http://localhost:8081
- Frontend principal: http://localhost:5175
- Frontend de pagamentos: http://localhost:5174

## Nota rápida

Se a notifications API key mudar, atualiza `set_env.sh` e volta a arrancar os serviços.

## Blockchain no Docker raiz

O backend de transactions está configurado para usar a blockchain real no Docker raiz.
O modo de desenvolvimento está desativado (`dev-mode: false`), por isso o fluxo segue para o provider real.
