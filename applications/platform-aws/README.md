# platform-aws

Adaptadores AWS reutilizáveis. Hoje contém um só: `KmsDataKeyProvider`.

## Por que existe

O envelope de criptografia do payload (em `shared`) depende de uma chave de dados. Em desenvolvimento ela vem de uma chave local; em produção vem do KMS. Separar o provedor KMS em um módulo próprio mantém o `shared` livre do AWS SDK e permite que ingestion e worker usem o mesmo adaptador.

## KmsDataKeyProvider

Gera e decifra chaves de dados via KMS, **com cache**. O cache é o ponto central: sem ele, cada proposta geraria uma chamada `GenerateDataKey`. A 10.000 requisições por segundo isso significa custo por chamada, risco de throttling do KMS e latência somada ao p95 da borda — tudo no caminho crítico.

Com cache, a mesma chave de dados cifra muitas propostas e o KMS é chamado apenas quando a chave expira.

```java
DataKeyProvider provider = new KmsDataKeyProvider(kmsClient, keyId, ttl);
```

## Trade-off assumido

Reutilizar a chave de dados por um período reduz chamadas, mas amplia o alcance de uma chave eventualmente comprometida. O TTL é o controle desse equilíbrio: mais curto significa mais segurança e mais custo. A escolha do valor é de operação, não de código.

## Testes

```bash
mvn -pl platform-aws test
```
