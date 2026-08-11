# Capability Spec: Architecture & System Resiliency

## ADDED Requirements

### Requirement: Modular Desktop App Structure
The desktop launcher SHALL separate non-GUI background tasks (GitHub version update checking and backup/restore dialog handlers) from the JavaFX `Application` lifecycle.

#### Scenario: JavaBridge Method Delegation
- **Given** the frontend JavaScript invokes `window.javaBridge.saveFile(url, name, pass)` or `window.javaBridge.importFile()`
- **When** the JavaFX WebView triggers the native Java bridge
- **Then** the application SHALL delegate the action to dedicated helper classes while maintaining strong references to prevent garbage collection.

### Requirement: Database Migration Baseline
The application SHALL manage schema evolution using Flyway migrations with backward compatibility enabled for existing user databases.

#### Scenario: Startup on Existing User Database
- **Given** an existing H2 database file `.mytwocents/data.mv.db` without a Flyway schema history table
- **When** the Spring Boot context initializes
- **Then** Flyway SHALL mark the existing schema as baseline V1 without failing or dropping user data.

### Requirement: Uniform REST Error Handling
The backend SHALL catch unhandled exceptions in REST Controllers and return standard structured JSON error responses with appropriate HTTP status codes.

#### Scenario: Invalid Request Parameter
- **Given** a REST request with missing or invalid parameters
- **When** the Controller executes
- **Then** `GlobalExceptionHandler` SHALL return an `ErrorResponseDTO` containing error timestamp, status, error message, and detail validation fields.
