#!/bin/bash

# garantir Node correto (assume que nvm já está instalado)
nvm install 20
nvm use 20

# limpar dependências antigas (caso existam)
rm -rf node_modules package-lock.json

# instalar dependências
npm install

# garantir versão compatível do Vite
npm install vite@7

# correr o projeto
npm run dev