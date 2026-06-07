# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Backend (requires JDK 25, uses Maven Wrapper — no Maven install needed)
./mvnw spring-boot:run                    # Start backend on :8080
./mvnw test                               # Run all tests
./mvnw package -DskipTests                # Build fat JAR to target/

# Frontend
cd frontend && npm install && npm run dev # Dev server on :3000 (hot reload, proxies /api → :8080)
cd frontend && npm run build              # Build to ../src/main/resources/static/

# Production packaging (single .exe installer)
build.bat                                 # Full pipeline: frontend + backend + jlink + jpackage
```

## Architecture

**Backend** (`com.example.watermanagement`): Standard Spring Boot layered architecture. `controller` → `service/impl` → `repository`. JSON API responses wrapped in `ApiResponse<T>` (code/message/data). Global exceptions caught by `GlobalExceptionHandler`. `BusinessException` throws return `ApiResponse.fail()`.

**Frontend**: Vue 3 SPA with Vue Router (history mode). `SpaConfig.java` filter forwards unknown paths → `index.html`. API layer (`api/index.js`) has an Axios response interceptor that unwraps `ApiResponse.data` for JSON responses and passes blobs through unchanged (important for Excel download endpoints).

**Database**: SQLite via `hibernate-community-dialects` (SQLiteDialect). Stored at `${user.home}/.water-management/data/water_meter.db`. JPA `ddl-auto: update` auto-creates tables from entities. `WaterManagementApplication.main()` creates the data directory before Spring starts.

**Relationships**: `households` (water meter) → `readings` (meter readings) → `water_bills` (monthly charges). `households` 1:1 `material_bills`. `payments` reference `bill_type` + `bill_id` (polymorphic, no FK constraint — by design for SQLite).

## Key Business Logic

**Batch readings** (`ReadingServiceImpl.processSingleReading`): Given a water meter ID + current reading → looks up previous reading → computes usage → checks for anomalies (negative usage or spike > threshold) → saves `readings` row → creates/updates `water_bills` row (charge = usage × water price, default 1.8 yuan/ton).

**Multi-month payment** (`PaymentServiceImpl.payWaterBills`): Distributes a single payment amount across multiple bills proportionally by each bill's remaining due. Updates `actualWaterPaid` and recalculates `waterStatus` (未收/部分收/已收) per bill.

**Excel import/export** uses EasyExcel. `ReadingExportRow` has `@ExcelProperty` annotations for column mapping. `ExcelUtil` wraps EasyExcel read/write with HTTP response streaming.

## Critical Gotchas

1. **Spring Boot 4.0 JarLauncher path**: The main class is `org.springframework.boot.loader.launch.JarLauncher` (note the `.launch.` — this changed from SB3). This is critical for jpackage main-class. The JAR manifest already has the correct value; failure to match means ClassNotFoundException.

2. **SPA routing**: Vue Router history mode means browser refresh on `/readings` hits the server. `SpaConfig` filter catches non-API/non-static paths and forwards to `/index.html`. Don't remove this.

3. **Blob responses in frontend**: The Axios interceptor checks `response.config.responseType === 'blob'` before unwrapping JSON. Excel download endpoints return blobs — without this check the interceptor shows spurious "请求失败" errors.

4. **SQLite directory permissions**: Database path uses `${user.home}` (resolved by Spring). The app creates `~/.water-management/data/` on startup. Never use relative paths like `./data/` — they fail when installed under `C:\Program Files\`.

5. **Port 8080 bind failure**: Common when a previous run didn't shut down. Kill lingering `java.exe` / `VillageWaterManagement.exe` processes before restarting.

6. **jpackage `--type exe` requires WiX Toolset** installed at `C:\Program Files (x86)\WiX Toolset v3.14\bin`. Without WiX, fall back to `--type app-image` (portable directory, not a single-file installer).

7. **JVM flag `--enable-native-access=ALL-UNNAMED`** is required in jpackage for SQLite JDBC native library loading on JDK 25.

## Startup Log

On any machine, check `%USERPROFILE%\.water-management\logs\startup.log` for startup diagnostics including system info, database path, and any crash stacktraces.
