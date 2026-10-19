# AI Prompt & Specification: Implementation of Business Fingerprint for Smart Idempotency (v2.0)

> **Target Agent**: Cursor, GitHub Copilot, Claude Code, Windsurf, or any LLM Coding Assistant  
> **Tech Stack**: Java 21 (Virtual Threads), Spring Boot 3.3+, AWS SDK v2 for DynamoDB, HMAC-SHA256, JUnit 5  
> **Domain**: Financial Services / Consigned Credit (Crédito Consignado)  
> **Status**: Specification & Implementation Guide for AI Developers  
> **Version**: 2.0 (Updated with The Three Concepts of Duplicity)

---

## 1. Executive Summary & Purpose for the AI Assistant

You are acting as a **Senior Principal Software Engineer**. Your objective is to implement the **Business Fingerprint (Smart Idempotency)** pattern in our Java 21 / Spring Boot 3.3 microservice, while strictly respecting the **Three Concepts of Duplicity** in distributed financial systems.

### The Problem Being Solved
In high-concurrency B2B APIs (such as consigned credit proposal ingestion during peak opening hours), integration partners often experience network timeouts. When retrying, their HTTP client libraries frequently instantiate new request objects with a **new `Idempotency-Key` UUID in the header for the exact same loan proposal**. 

Relying solely on the HTTP header `Idempotency-Key` causes the API edge to treat identical retries as brand-new requests, flooding downstream queues and Core Banking databases.

---

## 1.1. Theoretical Framework: The Three Concepts of Duplicity (Os Três Conceitos de Duplicidade)

To avoid confusing technical retries, domain equivalence, and commercial race conditions, the implementation MUST distinguish between three distinct types of duplicity:

| Concept / Conceito | Definition & Problem | Mechanism & Architectural Responsibilities |
| :--- | :--- | :--- |
| **1. Duplicidade Técnica (Technical Duplication)** | The client sends a retry of the **exact same logical operation** due to a network timeout or missing ACK. The client is well-behaved and **preserves the `Idempotency-Key` UUID** in the HTTP header across attempts. | **Header-based Idempotency**: The API edge checks the `Idempotency-Key` header against cache/DynamoDB and returns the cached response or current processing state. |
| **2. Equivalência Semântica / Domínio (Domain/Semantic Equivalency)** | The client (e.g., an automated B2B bot under stress) re-instantiates the request payload and **generates a NEW `Idempotency-Key` UUID for the exact same loan proposal**. To the header check, this looks like a new request, but the underlying business intent is identical. | **Business Fingerprint (HMAC-SHA256)**: The API edge normalizes immutable proposal attributes (CPF, Agreement, Amount, Installments, Date) into a deterministic HMAC-SHA256 hash and performs an **atomic conditional write (`attribute_not_exists`) in DynamoDB**. |
| **3. Duplicidade Commercial / Concorrência (Commercial Violation / Shared Invariant)** | Two distinct loan proposals (different fingerprints, different amounts or dates) compete for the **same shared business resource** (e.g., the customer's available INSS/eSocial margin limit). | **Domain Invariant & State Machine**: Resolved downstream by the proposal State Machine (`RECEIVED`, `PROCESSING`, `REQUIRES_RECONCILIATION`), atomic database locks (Aurora PostgreSQL), and margin reservation checks with external government systems (Dataprev/eSocial). **The Fingerprint does NOT solve this alone.** |

> **Key Rule for the AI**: The `Business Fingerprint` is specifically designed to solve **Concept 2 (Equivalência Semântica)**. It complements Concept 1 (Technical Header) and prepares the proposal for Concept 3 (Commercial Invariants). Do not confuse or merge these three responsibilities!

---

## 2. Core Architectural & Domain Rules

### Rule 1: Header vs. Fingerprint (Two-Tier Idempotency)
- **`Idempotency-Key` Header (Concept 1)**: Handles *transport/technical retries* when the client preserves the header key.
- **`Business Fingerprint` (Concept 2)**: Handles *semantic/domain equivalence* when client retries generate new header UUIDs.
- Both mechanisms work in tandem. Do **not** replace the header; complement it.

### Rule 2: Privacy & Security (LGPD Compliance)
- **NEVER** use plain `SHA-256` directly on sensitive fields (like CPF). Plain SHA-256 on predictable numerical data (11-digit CPFs) is vulnerable to rainbow table enumeration attacks.
- **ALWAYS** compute an **HMAC-SHA256** using a secret key stored in AWS KMS or AWS Secrets Manager.

### Rule 3: Fingerprint is NOT the Entity Identifier
- The `Business Fingerprint` is an **internal deduplication key** (`PK = HASH#<fingerprint>`).
- The public proposal identifier (`proposalId`) MUST remain an independent UUID (or business key).

### Rule 4: Atomic State & Lifecycle
- Deduplication write in DynamoDB (`DurableInbox` table) uses:
  ```text
  PK = HASH#<fingerprint_hex>
  SK = METADATA
  ConditionExpression = attribute_not_exists(PK)
  ```
- If `ConditionalCheckFailedException` occurs, the request is recognized as duplicate (Concept 2).
- Response for duplicates: `HTTP 202 Accepted` with header `X-Idempotency-Status: DUPLICATE_REJECTED` and the existing proposal tracking payload.

---

## 3. Strict Canonicalization & Normalization Protocol

Before computing the HMAC, all input attributes MUST be normalized strictly to ensure that equivalent requests always yield the exact same string representation regardless of whitespace, formatting, or locale differences.

### Mathematical Formula
$$\text{BusinessFingerprint} = \text{HMAC-SHA256}(K_{\text{secret}}, \text{CPF}_{\text{norm}} \mid \text{AgreementType} \mid \text{Amount}_{\text{norm}} \mid \text{Installments} \mid \text{DateUTC})$$

### Field Canonicalization Rules

| Field | Input Example | Canonical Format | Rule |
| :--- | :--- | :--- | :--- |
| **CPF** | `"123.456.789-01"` or `12345678901` | `"12345678901"` | Strip all non-digit characters (`\D`). Must be exactly 11 numeric digits with leading zeros preserved. |
| **AgreementType** | `"inss  "` or `"INSS"` | `"INSS"` | Upper-case, trimmed string corresponding to valid Enum (`INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`). |
| **Amount** | `15000` or `15000.0` or `15000.000` | `"15000.00"` | Format using `BigDecimal` with scale 2 (`HALF_EVEN` rounding) using point `.` as decimal separator. Never use `double` or unformatted strings. |
| **Installments** | `84` or `"084"` | `"84"` | Parse as `int` and format as integer string without leading zeros. |
| **DateUTC** | `"2026-10-14T08:00:00Z"` | `"2026-10-14"` | Contractual ISO LocalDate string in UTC (`YYYY-MM-DD`). |
| **Delimiter** | N/A | `|` | Pipe character separator. |

#### Canonical String Construction Example
```text
12345678901|INSS|15000.00|84|2026-10-14
```

---

## 4. Required Implementation Plan for the AI Coding Assistant

Please generate the following Java classes and components in the package `com.bank.consignado.idempotency`:

### Task 1: `CanonicalPayloadNormalizer.java`
Implement a utility/service that accepts the incoming DTO and outputs the normalized canonical string according to the rules in Section 3.

```java
package com.bank.consignado.idempotency;

import com.bank.consignado.dto.ProposalRequestDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class CanonicalPayloadNormalizer {

    public String buildCanonicalString(ProposalRequestDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ProposalRequestDto cannot be null");
        }

        // 1. Normalize CPF (digits only, exactly 11 digits)
        String rawCpf = dto.getCustomerCpf() != null ? dto.getCustomerCpf() : "";
        String normalizedCpf = rawCpf.replaceAll("\\D", "");
        if (normalizedCpf.length() != 11) {
            throw new IllegalArgumentException("Invalid CPF length after normalization: " + normalizedCpf);
        }

        // 2. Normalize Agreement Type (Uppercase & Trimmed)
        String rawAgreement = dto.getAgreementType() != null ? dto.getAgreementType() : "";
        String normalizedAgreement = rawAgreement.trim().toUpperCase();

        // 3. Normalize Amount (BigDecimal scale 2)
        BigDecimal rawAmount = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;
        String normalizedAmount = rawAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();

        // 4. Normalize Installments (Integer string)
        int installments = dto.getInstallments() != null ? dto.getInstallments() : 0;
        String normalizedInstallments = String.valueOf(installments);

        // 5. Normalize Date (UTC LocalDate YYYY-MM-DD)
        LocalDate dateUtc = dto.getContractDate() != null 
                ? dto.getContractDate() 
                : LocalDate.now(ZoneOffset.UTC);
        String normalizedDate = dateUtc.toString();

        // 6. Assemble with Pipe Delimiter
        return String.join("|", 
                normalizedCpf, 
                normalizedAgreement, 
                normalizedAmount, 
                normalizedInstallments, 
                normalizedDate
        );
    }
}
```

---

### Task 2: `BusinessFingerprintService.java`
Implement the HMAC-SHA256 calculation service using `javax.crypto.Mac` and Spring's `@Value` or AWS KMS Provider for the secret key.

```java
package com.bank.consignado.idempotency;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
public class BusinessFingerprintService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String hmacSecretKey;
    private final CanonicalPayloadNormalizer normalizer;

    public BusinessFingerprintService(
            @Value("${app.security.hmac-secret-key:defaultSecretKeyForLocalDevOnly12345!}") String hmacSecretKey,
            CanonicalPayloadNormalizer normalizer) {
        this.hmacSecretKey = hmacSecretKey;
        this.normalizer = normalizer;
    }

    public String generateFingerprint(com.bank.consignado.dto.ProposalRequestDto dto) {
        String canonicalString = normalizer.buildCanonicalString(dto);
        return computeHmacSha256(canonicalString, hmacSecretKey);
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 fingerprint", e);
        }
    }
}
```

---

### Task 3: `DurableInboxRepository.java`
Implement the DynamoDB SDK v2 atomic conditional write component.

```java
package com.bank.consignado.idempotency;

import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Repository
public class DurableInboxRepository {

    private static final String TABLE_NAME = "DurableInbox";
    private final DynamoDbClient dynamoDbClient;

    public DurableInboxRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Atomically attempts to claim the business fingerprint in DurableInbox.
     * @return true if new item was successfully saved; false if duplicate detected.
     */
    public boolean tryAcquireFingerprint(String fingerprintHex, String proposalId, String tenantId, String rawPayloadJson) {
        String pk = "HASH#" + fingerprintHex;
        long ttlEpoch = Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(pk).build());
        item.put("SK", AttributeValue.builder().s("METADATA").build());
        item.put("proposalId", AttributeValue.builder().s(proposalId).build());
        item.put("tenantId", AttributeValue.builder().s(tenantId != null ? tenantId : "DEFAULT").build());
        item.put("status", AttributeValue.builder().s("RECEIVED").build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttlEpoch)).build());
        item.put("createdAt", AttributeValue.builder().s(Instant.now().toString()).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .conditionExpression("attribute_not_exists(PK)")
                .build();

        try {
            dynamoDbClient.putItem(request);
            return true; // Successfully acquired -> NEW
        } catch (ConditionalCheckFailedException e) {
            return false; // Already acquired -> DUPLICATE (Concept 2: Semantic Equivalency)
        }
    }
}
```

---

### Task 4: Controller & Filter Guard Integration

In the API Ingestion Controller:

```java
@PostMapping(value = "/v1/proposals", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ProposalIngestionResponseDto> ingestProposal(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
        @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
        @Valid @RequestBody ProposalRequestDto requestDto) {

    // 1. Check Concept 1: Technical Header Idempotency (if client preserved Idempotency-Key)
    if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
        // Option to check Header Cache first...
    }

    // 2. Check Concept 2: Semantic Business Fingerprint (HMAC-SHA256)
    String fingerprintHex = fingerprintService.generateFingerprint(requestDto);
    String proposalId = UUID.randomUUID().toString();

    // 3. Atomic Acquire in DynamoDB Inbox
    boolean isNewRequest = durableInboxRepository.tryAcquireFingerprint(fingerprintHex, proposalId, tenantId, objectMapper.writeValueAsString(requestDto));

    if (!isNewRequest) {
        // Handle Concept 2 Duplicate Response (Semantic Equivalency)
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Idempotency-Status", "DUPLICATE_REJECTED");
        
        ProposalIngestionResponseDto response = new ProposalIngestionResponseDto(
                "DUPLICATE_REJECTED",
                "Proposal intent already received for this customer and parameter set today.",
                null,
                "RECEIVED"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(response);
    }

    // 4. Process New Request (Pass down to Concept 3: Commercial Invariants Check)
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Idempotency-Status", "NEW");

    ProposalIngestionResponseDto response = new ProposalIngestionResponseDto(
            "ACCEPTED",
            "Proposal received and enqueued successfully.",
            proposalId,
            "RECEIVED"
    );
    return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(response);
}
```

---

## 5. Verification & Testing Requirements

Generate comprehensive JUnit 5 unit tests for:
1. **Three Concepts Coverage Test**: Verify tests explicitly test technical header retries, semantic fingerprint deduplication, and commercial margin invariants.
2. **Canonicalization Test**: Test variations of CPF (`"123.456.789-01"`, `"12345678901"`), amount formatting (`15000`, `15000.00`), and spaces in agreement type to verify they produce the exact same canonical string and HMAC.
3. **Concurrency Test**: Mock `DynamoDbClient` to simulate concurrent calls throwing `ConditionalCheckFailedException` and verify HTTP header `X-Idempotency-Status: DUPLICATE_REJECTED`.
4. **Secret Key Isolation**: Verify HMAC values change when secret keys change.

---

## 6. Definition of Done (DoD) for AI Code Generation

- [x] Clear theoretical distinction between **The Three Concepts of Duplicity**.
- [x] Code compiles under **Java 21** with Virtual Threads enabled (`spring.threads.virtual.enabled=true`).
- [x] Zero plain SHA-256 usage on sensitive fields.
- [x] `BigDecimal` scale set strictly to 2 with `HALF_EVEN` rounding.
- [x] DynamoDB `PutItem` uses `attribute_not_exists(PK)`.
- [x] All unit tests pass with > 90% code coverage on canonicalization logic.
