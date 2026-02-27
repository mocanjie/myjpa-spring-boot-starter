# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is a Spring Boot starter (`myjpa-spring-boot-starter`) that provides simplified JPA-like functionality using Spring JDBC Template. It offers annotation-driven entity mapping with multi-database support.

## Common Development Commands

```bash
# Build and compile (requires JDK 21)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn clean compile

# Create JAR package
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn clean package

# Install to local Maven repository
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn clean install

# Deploy to Maven Central (requires release profile)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home mvn clean deploy -P release
```

## Architecture Overview

### Core Components
- **Auto-Configuration**: `MyJpaAutoConfiguration` registers core beans and configures SQL logging
- **Service Layer**: `IBaseService` + `BaseServiceImpl` provide CRUD operations
- **DAO Layer**: `IBaseDao` + `BaseDaoImpl` handle data access via Spring JDBC Template
- **SQL Generation**: `SqlBuilder` with database-specific implementations (MySQL, Oracle, SQL Server, PostgreSQL, KingbaseES)
- **Metadata Management**: `TableInfoBuilder` scans `@MyTable` annotations at startup
- **SQL Parser**: `JSqlDynamicSqlParser` automatically injects logical delete conditions and tenant isolation conditions into all SELECT queries
- **Multi-Tenancy**: `TenantIdProvider` (SPI), `TenantContext` (ThreadLocal), `TenantAwareSqlParameterSource` (parameter injection)

### Key Annotations
- `@MyTable`: Maps entity classes to database tables with optional logical deletion support
- `@MyField`: Maps entity fields to database columns with serialization control

### Database Support
Multi-database support implemented through `SqlBuilder` variants. Database type detection happens in `BaseDaoImpl` constructor.

## Important Implementation Details

### SQL Condition Injection Pipeline

All query methods route through `BaseDaoImpl.applyConditions()`, which decides the parse path once per query:

```
applyConditions(sql, sps)
  ├─ tenant enabled + !isSkipped() + tenantId != null
  │    → appendConditions(sql)          // 1 parse: delete + tenant together
  └─ otherwise
       → appendDeleteCondition(sql)     // 1 parse: delete only
```

Either path performs **exactly one** `CCJSqlParserUtil.parse()` call per query, regardless of whether both features are active.

### Automatic Logical Delete Condition Injection

All query methods automatically inject logical delete conditions. There are no separate `WithDeleteCondition` methods — the standard methods handle everything.

The parser is idempotent: `isDeleteConditionExists` checks before appending, so conditions are never duplicated even when methods delegate to each other internally.

### JOIN Condition Strategy
Both logical delete and tenant conditions follow the same placement rule:
- **Main table (FROM) and INNER JOIN**: conditions added to WHERE clause
- **LEFT/RIGHT JOIN**: conditions added to JOIN ON clause (preserves outer join semantics)

Tables without a configured `@MyTable` delete field (or without a tenant column in the database) are left untouched.

### SQL Transformation Examples
```sql
-- Original
SELECT * FROM user

-- After appendDeleteCondition (delete only)
SELECT * FROM user WHERE user.delete_flag = 0

-- After appendConditions (delete + tenant, single parse)
SELECT * FROM user WHERE user.delete_flag = 0 AND user.tenant_id = :myjpaTenantId

-- JOIN query with both features
SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id
-- Becomes (user has tenant_id, role has is_deleted)
SELECT u.*, r.role_name FROM user u
LEFT JOIN role r ON u.role_id = r.id AND r.is_deleted = 0
WHERE u.delete_flag = 0 AND u.tenant_id = :myjpaTenantId
```

### Multi-Tenancy Isolation

**Disabled by default.** Enable via `myjpa.tenant.enabled=true`.

**How it works:**
1. At startup, `DatabaseSchemaValidator` checks every `@MyTable` table for the configured tenant column (default `tenant_id`) and registers matching tables in `TableCacheManager.TABLE_TENANT_CACHE`.
2. At query time, `BaseDaoImpl.applyConditions()` decides whether to call `appendConditions()` (both delete + tenant, single parse) or `appendDeleteCondition()` (delete only, single parse).
3. When tenant is active, `JSqlDynamicSqlParser.appendConditions()` injects `AND table.tenant_id = :myjpaTenantId` in the same parse pass as the logical delete condition.
4. `TenantAwareSqlParameterSource` wraps the original parameter source to supply the actual tenant ID value.

**Priority for obtaining tenant ID:** `TenantIdProvider` SPI Bean → `TenantContext.getTenantId()` ThreadLocal → `null` (skip injection = super-admin).

**`applyConditions()` uses delete-only path when:**
- `myjpa.tenant.enabled=false`
- `TenantContext.isSkipped()` returns true
- `getCurrentTenantId()` returns null (super-admin — SQL is never rewritten for tenant, avoiding missing-parameter errors)

**JOIN strategy** (same as logical delete):
- Main table (FROM) and INNER JOIN → condition added to WHERE clause
- LEFT/RIGHT JOIN → condition added to JOIN ON clause (preserves outer join semantics)

**Idempotency:** `isTenantConditionExists` checks before appending, so conditions are never duplicated.

**Method-level bypass:**
```java
// Lambda form (recommended)
TenantContext.withoutTenant(() -> dao.queryListForSql(...));

// Manual
TenantContext.skip();
try { ... } finally { TenantContext.restore(); }
```

### Logging Configuration
- Uses Log4j2 instead of Spring Boot's default Logback
- SQL logging controlled by `myjpa.showsql` property
- Default configuration in `src/main/resources/application.properties`

### Spring Boot Integration
- Auto-configuration registered in `META-INF/spring.factories`
- Highest priority BeanPostProcessor (Integer.MIN_VALUE)
- Relies on Spring JDBC Template and DataSource auto-configuration

### Reflection Usage
Heavy use of reflection for:
- Classpath scanning for `@MyTable` annotated classes
- Runtime metadata building
- Dynamic SQL generation based on entity structure

### Dependencies
- Java 21
- Spring Boot 3.2.0 (via spring-boot-starter-jdbc)
- Custom `mycommon` library (version spring3)
- JSqlParser 5.3 for SQL parsing
- Log4j2 for logging

## Testing Notes
Tests live in `src/test/java`. No Spring context is required — tests set static fields directly:
```java
@BeforeAll static void setup() {
    SqlBuilder.type = 1; // 1=MySQL, 5=PostgreSQL
    TableCacheManager.initCache("io.github.mocanjie.base.myjpa.test.entity");
    // For tenant tests:
    JSqlDynamicSqlParser.tenantEnabled = true;
    TableCacheManager.registerTenantTable("user");
}
```
Current test classes: `MySQL8CompatibilityTest` (24), `PostgreSQL16CompatibilityTest` (24), `TenantIsolationTest` (28) — 76 tests total.

## Configuration Properties
- `myjpa.showsql`: Enable/disable SQL statement logging (default `true`)
- `myjpa.validate-schema`: Enable/disable schema validation at startup (default `true`)
- `myjpa.tenant.enabled`: Enable multi-tenancy isolation (default **`false`**)
- `myjpa.tenant.column`: Tenant column name (default `tenant_id`)

## Intelligent Package Scanning
The starter automatically detects and scans the main application package for @MyTable annotations:
- **Zero Configuration**: No need to specify scan packages manually
- **Smart Detection**: Automatically deduces the main application package from stack trace
- **Fallback Strategy**: Uses common business package patterns if auto-detection fails
- **Performance Optimized**: Only scans necessary packages, avoiding third-party libraries
