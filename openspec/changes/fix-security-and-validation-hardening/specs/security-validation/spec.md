# Capability Spec: Security & Validation Hardening

## ADDED Requirements

### Requirement: Authenticated Database Reset
The database reset endpoint SHALL require password authentication before executing any destructive table truncation.

#### Scenario: Unauthorized Database Reset Request
- **Given** an HTTP POST request to `/api/backup/reset` without valid `X-Backup-Password` header
- **When** the controller evaluates the request
- **Then** it SHALL reject the request with HTTP status 401 Unauthorized without modifying table data.

### Requirement: Encrypted Storage of External API Keys
The system SHALL encrypt sensitive API keys (`apiKey`, `brapiToken`) using AES encryption prior to persisting them in the database.

#### Scenario: Saving AI Configuration
- **Given** a user inputs a new API Key for Gemini or BrAPI
- **When** `AiConfig` entity is saved via repository
- **Then** the key SHALL be stored in encrypted format and decrypted only in memory when invoking external services.

### Requirement: Positive Value Validation on Transactions
All financial entry DTOs SHALL enforce positive numerical values for monetary amounts and investment quantities.

#### Scenario: Negative Amount Payload
- **Given** a POST request to `/api/lancamentos` or `/api/investimentos/lancamentos` with negative amount
- **When** Spring MVC validates the `@Valid` DTO
- **Then** `GlobalExceptionHandler` SHALL reject the payload with a HTTP 400 Bad Request listing the field validation error.
