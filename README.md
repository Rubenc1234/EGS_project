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
curl -X GET https://services.tiagorg.pt/notifications/v1/admin/clients \
	-H "Authorization: Bearer master_key"
```

Se a lista vier vazia, então cria um novo client no Notifications Service:

```bash
curl -X POST https://services.tiagorg.pt/notifications/v1/admin/clients \
	-H "Authorization: Bearer master_key" \
	-H "Content-Type: application/json" \
	-d '{
		"name": "abc",
		"admin_email": "a@a.com",
		"notifications_email": "b@b.com"
	}'
```

O GET devolve a lista de clients já registados. O POST devolve a API key do client e o VAPID necessários. Essa key deve ser usada no fluxo de notifications/subscriptions.

necessário ir ao `useNotificationSubscription` e mudar a `VAPID_PUBLIC_KEY`.
e ir ao set_env.sh mudar a NOTIFICATIONS_API_KEY.

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

## Vault persistente (setup inicial)

Para inicializar o Vault persistente, criar secrets e gerar AppRoles:

```bash
./scripts/vault_setup_persistent.sh
```

Guarda o ficheiro `.vault/init.json` em local seguro. E necessario para unseal e para recuperar o root token.

## Vault no Kubernetes

Subir o Vault no cluster:

```bash
kubectl apply -f k8s/vault.yaml
```

Inicializar e unseal (gera `vault-init.json` local):

```bash
kubectl -n tenant-grupo3-egs-deti-ua-pt exec -it deploy/vault -- \
	vault operator init -key-shares=1 -key-threshold=1 -format=json > vault-init.json

UNSEAL_KEY="$(jq -r '.unseal_keys_b64[0]' vault-init.json)"
kubectl -n tenant-grupo3-egs-deti-ua-pt exec -it deploy/vault -- \
	vault operator unseal "$UNSEAL_KEY"
```

Bootstrap de secrets e AppRoles no Vault do cluster:

```bash
export VAULT_TOKEN="$(jq -r '.root_token' vault-init.json)"
./scripts/vault_bootstrap_k8s.sh
```

Migrar secrets do Vault local para o Vault do cluster:

```bash
export VAULT_TOKEN_LOCAL="<root-token-local>"
export VAULT_TOKEN="$(jq -r '.root_token' vault-init.json)"
./scripts/vault_migrate_to_k8s.sh
```


Antes de arrancar a UI, instala as dependências dos dois frontends:

```bash
cd frontend && npm install
cd ../payment_service/frontend && npm install
cd ../..
```

Dps de executar setup persistente é necessário dar unseal todas as vezes...
```bash
docker-compose -f docker-compose.vault.yml up -d vault
```

```bash
UNSEAL_KEY="$(jq -r '.unseal_keys_b64[0]' .vault/init.json)"
docker-compose -f docker-compose.vault.yml exec -T vault sh -lc \
"export VAULT_ADDR=http://127.0.0.1:8200; vault operator unseal ${UNSEAL_KEY}"
```

UNSEAL_KEY está no .vault/init.json

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
- Grafana: http://grafana.pt

## Nota rápida

Se a notifications API key mudar, atualiza `set_env.sh` e volta a arrancar os serviços.

## Blockchain no Docker raiz

O backend de transactions está configurado para usar a blockchain real no Docker raiz.
O modo de desenvolvimento está desativado (`dev-mode: false`), por isso o fluxo segue para o provider real.
O Traefik é o único serviço exposto diretamente e encaminha o tráfego para a rede interna.
