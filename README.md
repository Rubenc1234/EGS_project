## Projeto Composer (resumo)

Este repositório contém um conjunto de micro-serviços simples para demonstração, incluindo um "composer" (front-facing API) que comunica com um serviço IAM (autenticação/autorização) que usa Keycloak (por agora só está implementado com este serviço, falta os outros).

O ficheiro principal do composer é `app.py` que expõe endpoints usados por clientes para autenticação e consulta de utilizadores. O composer delega operações relacionadas com tokens e validação para o serviço IAM (`iam_service`) através do cliente HTTP em `clients/iam_client.py`.

## Arquivos relevantes

- `app.py` - API do composer. Endpoints principais:
	- `POST /v1/composer/login` — autentica usuário (username/password). Chama `iam.get_user_token` e opcionalmente faz introspect.
	- `PATCH /v1/composer/tokens` — valida (introspect) um token: recebe `{ "token": "..." }` e retorna `{ "active": true|false }`.
	- `GET /v1/composer/users/<id>` — retorna dados do usuário consultando o IAM; requer header `Authorization: Bearer <token>`.


## Como executar (rápido)

1. Crie e ative um virtualenv Python e instale dependências (ex.: `requirements.txt`).

2. Executar o IAM (modo local):

```bash
# na raiz do projeto
python3 -m iam_service.app_iam
```

3. Executar o composer (apontando para o IAM local):

```bash
python3 app.py
```

Por omissão `app_iam.py` roda em `0.0.0.0:5000` e o `composer` (`app.py`) em `0.0.0.0:5001`.

