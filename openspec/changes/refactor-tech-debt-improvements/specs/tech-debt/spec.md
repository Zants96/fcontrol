# Capability Spec: Tech Debt Elimination & Performance

## MODIFIED Requirements

### Requirement: Externalized AI Prompt Management
The system SHALL load AI prompts and JSON structures from resource template files instead of hardcoded Java string blocks.

#### Scenario: Prompt Template Loading
- **Given** an AI service request for financial analysis
- **When** `AiService` builds the system prompt
- **Then** it SHALL read template contents from `classpath:prompts/system_base.st` and `classpath:prompts/filosofia.st`.

### Requirement: Cached Quote Retrieval
The system SHALL cache external financial quotes from BrAPI, BACEN, and CoinGecko to minimize network latency and prevent thread starvation.

#### Scenario: Subsequent Quote Request
- **Given** a ticker quote has been fetched within the last 15 minutes
- **When** the application requests the current price for the ticker
- **Then** it SHALL return the cached value without performing an external HTTP call.

### Requirement: Frontend State Store
The frontend JavaScript architecture SHALL centralize reactive application state in a store module (`store.js`).

#### Scenario: Investment List Update
- **Given** a user adds or updates an investment transaction
- **When** the API response returns the updated state
- **Then** `store.js` SHALL update its internal reactive state and trigger subscribers to refresh UI components.

### Requirement: Dev-Only Execution of Inspection Scripts
Inspection and debug scripts SHALL only run when the Spring active profile is explicitly set to `dev`.

#### Scenario: Production Application Startup
- **Given** the application starts under default/production profile
- **When** Spring Boot context loads
- **Then** `InvestigateDatabase` SHALL NOT execute database inspection routines.
