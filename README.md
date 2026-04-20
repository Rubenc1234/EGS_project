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

Antes de usar as subscriptions, vê primeiro se já existe um client registado:

```bash
curl -s -X GET http://notifications.pt/v1/admin/clients \
	-H "Authorization: Bearer master_key"
```

Se a lista vier vazia, então cria um novo client no Notifications Service:

```bash
curl -s -X POST http://notifications.pt/v1/admin/clients \
	-H "Authorization: Bearer master_key" \
	-H "Content-Type: application/json" \
	-d '{
		"name": "name",
		"admin_email": "mail@gmail.com"
	}'
```

O GET devolve a lista de clients já registados. O POST devolve a API key do client e o VAPID necessários. Essa key deve ser usada no fluxo de notifications/subscriptions.

## Hosts locais

Para a primeira fase, executa o script na raiz do projeto para mapear os nomes públicos para o IP local:

```bash
./setup_hosts.sh 127.0.0.1
```

Os nomes principais são `app.pt`, `payment.pt`, `iam.pt`, `composer.pt`, `transactions.pt`, `notifications.pt`, `keycloak.pt` e `payment-keycloak.pt`.

## Payments: utilizador necessário

Antes de usar o fluxo de pagamentos, cria um utilizador no Keycloak do Payments:

1. Abre http://payment-keycloak.pt/admin/master/console/#
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

O Docker expõe apenas o Traefik na porta 80; os restantes serviços ficam na rede interna.

Para parar:

```bash
./stop_all.sh
```

## URLs locais

- App principal: http://app.pt
- Payment frontend: http://payment.pt
- IAM: http://iam.pt
- Composer: http://composer.pt
- Transactions: http://transactions.pt
- Notifications: http://notifications.pt
- Keycloak: http://keycloak.pt
- Payment Keycloak: http://payment-keycloak.pt

## Nota rápida

Se a notifications API key mudar, atualiza `set_env.sh` e volta a arrancar os serviços.

## Blockchain no Docker raiz

O backend de transactions está configurado para usar a blockchain real no Docker raiz.
O modo de desenvolvimento está desativado (`dev-mode: false`), por isso o fluxo segue para o provider real.
O Traefik é o único serviço exposto diretamente e encaminha o tráfego para a rede interna.
