# Payment Service — TODO

Transformar o payment service numa aplicação completa e autónoma.
O `/pay` continua a ser a API de checkout que a app principal chama, mas o serviço tem agora a sua própria interface completa.

---

## Frontend — Novas Páginas

- [x] **`HomePage.tsx`** (`/home`) — Página principal pós-login (dashboard do utilizador)
  - 3 cards de resumo: total gasto €, nº de pagamentos, pendentes
  - Lista dos 5 pagamentos mais recentes com status chip
  - Cartões guardados em destaque (quick view)
  - Botões de ação rápida: "Novo Pagamento", "Ver Todos", "O meu Perfil"

- [x] **`PaymentDetailPage.tsx`** (`/payments/:id`) — Detalhe de um pagamento
  - Todos os campos do pagamento (ID, valor, wallet, data, status)
  - Botão de download do recibo PDF (apenas se concluído)
  - Timeline do estado (criado → pendente → concluído/cancelado)

- [x] **`CallbackPage.tsx`** — Atualizar redirecionamento pós-login
  - Se vem de app externa com `?wallet_id=&amount=` → redirecionar para `/pay` (comportamento atual)
  - Se vem de login direto na app → redirecionar para `/home` (novo comportamento)

---

## Frontend — Layout & Navegação

- [x] **`AppLayout.tsx`** — Layout partilhado com navbar persistente para páginas autenticadas
  - Links: Home, Pagamentos, Perfil, Stats (se operador), Logout
  - Indicador de utilizador autenticado (nome/email do token)
  - Wrapping de todas as páginas autenticadas: Home, Payments, Profile, Stats

- [x] Remover headers inline duplicados de `PaymentsPage.tsx`, `ProfilePage.tsx`, `StatsPage.tsx` após aplicar o `AppLayout`

---

## Frontend — Melhorias Visuais

- [x] Tema de cor consistente: uniformizar a paleta entre todas as páginas
  - Atualmente: 3 gradientes diferentes (azul escuro, roxo, azul claro)
  - Proposta: paleta única (ex: roxo `#667eea → #764ba2` como base)

- [x] Skeleton loading states para tabelas e cards de estatísticas (`MUI Skeleton`)

- [x] Empty state para lista de pagamentos quando não há nenhum (ícone + mensagem + botão de ação)

- [x] Status chips com ícones além de cor (ex: ⏳ Pendente, ✅ Concluído, ❌ Cancelado)

- [x] Página de detalhe acessível ao clicar numa linha da tabela de pagamentos (`PaymentsPage`)

---

## Backend — Novo Endpoint

- [ ] **`GET /v1/users/summary`** — Resumo do utilizador autenticado para o dashboard
  ```json
  {
    "total_spent": 150.00,
    "payment_count": 12,
    "pending_count": 2,
    "recent_payments": [...],
    "saved_cards_count": 2
  }
  ```
  - Reutilizar queries existentes em `repository/stats_repository.py`
  - Protegido por `@require_token`
  - Adicionar em `controllers/user_controller.py` + `services/user_service.py`

---

## Backend — Qualidade

- [ ] **Validação de inputs** nos endpoints:
  - `amount`: obrigatório, > 0, máximo razoável (ex: 10.000€)
  - `phone_number`: formato E.164 (`+351XXXXXXXXX`)
  - `wallet_id`: não pode ser vazio/null

- [ ] **Proteção brute-force no OTP**: máximo 3 tentativas erradas → bloquear o pagamento

---
