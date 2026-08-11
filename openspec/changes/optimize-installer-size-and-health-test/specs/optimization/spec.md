# Capability Spec: Installer Optimization & Health Verification

## MODIFIED Requirements

### Requirement: Optimized Custom JRE Generation
The build script SHALL generate a custom runtime image using `jlink` with minimal required modules, debug stripping, and bytecode compression enabled.

#### Scenario: RPM / EXE Installer Build
- **Given** a developer or CI pipeline builds the native installer using `build-installer.sh` or `build-installer-windows.bat`
- **When** `jlink` creates `target/custom-jre`
- **Then** it SHALL include `--compress=2`, `--strip-debug`, `--no-header-files` and omit `java.se`.

### Requirement: Platform-Specific JavaFX Dependency Profiling
The Maven build configuration SHALL selectively bundle only the target OS platform's native JavaFX libraries.

#### Scenario: Linux Package Generation
- **Given** building a Linux target distribution
- **When** Maven resolves project dependencies
- **Then** it SHALL exclude Windows native `.dll` classifiers from the compiled package.

### Requirement: Automated Health Verification
The application suite SHALL run automated unit and integration tests to verify overall system health.

#### Scenario: Running Health Check Test Suite
- **Given** code changes or optimization updates
- **When** executing `./mvnw test`
- **Then** 100% of test cases in services, controllers, and backup routines SHALL pass cleanly.
