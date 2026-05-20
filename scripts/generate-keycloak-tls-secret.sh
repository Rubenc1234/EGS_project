#!/bin/bash
# Script para gerar o Kubernetes TLS Secret para Keycloak a partir dos certificados em certs/

set -e

CERTS_DIR="certs"
K8S_DIR="k8s"
NAMESPACE="tenant-grupo3-egs-deti-ua-pt"

# Verificar se os ficheiros de certificado existem
if [ ! -f "$CERTS_DIR/keycloak.crt" ] || [ ! -f "$CERTS_DIR/keycloak.key" ]; then
    echo "❌ Erro: Ficheiros $CERTS_DIR/keycloak.crt ou $CERTS_DIR/keycloak.key não encontrados!"
    exit 1
fi

echo "📝 Gerando Secret YAML com certificados em base64..."

# Converter para base64
CERT_B64=$(base64 -w 0 < "$CERTS_DIR/keycloak.crt")
KEY_B64=$(base64 -w 0 < "$CERTS_DIR/keycloak.key")

# Criar ficheiro Secret
SECRET_FILE="$K8S_DIR/keycloak-tls-secret.yaml"

cat > "$SECRET_FILE" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: keycloak-tls
  namespace: $NAMESPACE
type: kubernetes.io/tls
data:
  tls.crt: $CERT_B64
  tls.key: $KEY_B64
EOF

echo "✅ Secret YAML gerado com sucesso!"
echo "📂 Ficheiro: $SECRET_FILE"
echo ""
echo "📋 Próximos passos:"
echo "1. Aplicar o Secret no cluster:"
echo "   kubectl apply -f $SECRET_FILE"
echo ""
echo "2. Aplicar o Ingress atualizado (k8s/keycloak.yaml):"
echo "   kubectl apply -f $K8S_DIR/keycloak.yaml"
echo ""
echo "3. Reiniciar o pod Keycloak para garantir que reconhece o novo TLS:"
echo "   kubectl -n $NAMESPACE rollout restart deployment/keycloak"
echo ""
echo "4. Verificar que o Secret foi criado:"
echo "   kubectl -n $NAMESPACE get secret keycloak-tls"
echo ""
echo "5. Instalar o certificado da CA no teu sistema para confiar (opcional):"
echo "   sudo cp certs/ca.crt /usr/local/share/ca-certificates/egs-dev-ca.crt"
echo "   sudo update-ca-certificates"
echo ""
echo "6. Testar HTTPS sem -k:"
echo "   curl https://keycloak.grupo3-egs-deti-ua.pt/"
