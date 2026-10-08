# OpenTelemetry / Jaeger - Troubleshooting Guide

## ❌ Erro: "Failed to publish metrics to OTLP receiver - java.net.ConnectException"

### Causa
A aplicação não consegue conectar ao Jaeger no endpoint OTLP.

### Solução

#### 1. Verificar se Jaeger está rodando
```bash
# Na instância EC2
docker ps | grep jaeger
```

Saída esperada:
```
jaegertracing/jaeger:2.10.0    Up 45 minutes    0.0.0.0:4317-4318->4317-4318/tcp
```

#### 2. Verificar URL do endpoint
**ERRADO** (localhost - só funciona se app estiver no mesmo container):
```yaml
endpoint: http://localhost:4318/v1/traces
```

**CORRETO** (endpoint público da instância EC2):
```yaml
endpoint: http://ec2-54-232-1-143.sa-east-1.compute.amazonaws.com:4318/v1/traces
```

#### 3. Testar conectividade
```bash
# Do seu PC local
curl -i http://ec2-54-232-1-143.sa-east-1.compute.amazonaws.com:4318/v1/traces

# Resposta esperada: 415 Unsupported Media Type (está funcionando!)
```

#### 4. Verificar se a porta está aberta na AWS
- **Security Group** da instância EC2 deve permitir entrada na porta **4318**
- Protocolo: **TCP**
- Source: Seu IP ou 0.0.0.0/0 (menos seguro)

---

## ❌ Erro: "WARN - Tracing not available in Spring"

### Causa
Dependência OTLP exporter não está no classpath.

### Solução
Confirme que o `pom.xml` tem:
```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Depois:
```bash
mvn clean install
```

---

## ⚠️ Aviso: "WARN - Sending metrics instead of traces to OTLP"

### Causa
Você configurou o endpoint para `/v1/metrics` (métricas) em vez de `/v1/traces` (traces).

### Solução
Mude em `application.yml`:
```yaml
# ❌ ERRADO
endpoint: http://localhost:4318/v1/metrics

# ✅ CORRETO
endpoint: http://localhost:4318/v1/traces
```

---

## 🔍 Verificar se Traces estão chegando no Jaeger

### 1. Abrir Jaeger UI
```
http://ec2-54-232-1-143.sa-east-1.compute.amazonaws.com:16686
```

### 2. Selecionar serviço
- Dropdown **Service**: escolha `pedidos-api`
- Clique em **Find Traces**

### 3. Se não aparecer nada
- **Faça requisições** na aplicação para gerar spans
- **Aguarde 15-30 segundos** (Jaeger pode levar para processar)
- **Clique em Refresh** (F5)

---

## 🧪 Teste Completo (Checklist)

- [ ] Jaeger está rodando: `docker compose ps | grep jaeger`
- [ ] Porta 4318 está aberta: `curl -i http://ec2-...:4318/v1/traces`
- [ ] URL em `application.yml` está correta
- [ ] Aplicação foi reiniciada após mudança no YAML
- [ ] Requisições foram feitas para gerar spans
- [ ] No log, não há `ConnectException`
- [ ] Spans aparecem em `http://localhost:16686`

---

## 🔧 Configurações Alternativas

### Se Jaeger estiver em um Docker Compose local

```yaml
# docker-compose.yaml
services:
  app:
    environment:
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318
```

### Se Jaeger estiver em outra rede/datacenter

```yaml
endpoint: http://jaeger-prod.example.com:4318/v1/traces
```

### Se Jaeger usar autenticação

```yaml
export:
  otlp:
    enabled: true
    endpoint: http://jaeger:4318/v1/traces
    headers:
      Authorization: "Bearer YOUR_TOKEN_HERE"
```

---

## 📊 Validar no Prometheus também

Se você está usando **métricas** (não apenas traces):

```bash
# Verificar se Prometheus está coletando
curl http://localhost:9090/api/v1/targets
```

Procure por `pedidos-api` em "up" status ✅

---

## 🐛 Debug: Aumentar verbosidade de logs

Em `application.yml`:
```yaml
logging:
  level:
    io.opentelemetry: DEBUG  # Verbose OTel logs
    io.opentelemetry.exporter.otlp: DEBUG
```

Isso vai mostrar o que está acontecendo com a exportação.

---

## 📈 Monitorar Saúde do Tracing

Endpoint de health:
```bash
curl http://localhost:8080/actuator/health/tracing
```

Se tiver `TracingHealthIndicator`:
```json
{
  "status": "UP",
  "tracing_sdk": "OpenTelemetrySdk",
  "status": "ACTIVE"
}
```

---

## 🆘 Ainda não funciona?

1. Verifique logs da aplicação: `docker logs pedidos-app | grep OTLP`
2. Verifique logs do Jaeger: `docker logs jaeger | grep -i error`
3. Teste a conectividade diretamente: `nc -zv ec2-54-232-1-143.sa-east-1.compute.amazonaws.com 4318`

Se nada funcionar, use só **Prometheus** por enquanto (OTLP é opcional).

---

## ✅ Tudo funcionando!

Quando conseguir conectar, você verá:
```
2026-09-22 20:00:00 [otlp-span-exporter-0] INFO ... - Successfully exported 15 spans to Jaeger
```

Aí é só abrir http://localhost:16686 e ver seus traces em tempo real! 🎉
