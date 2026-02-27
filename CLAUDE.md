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
- **SQL Parser**: `JSqlDynamicSqlParser` automatically injects logical delete conditions into all SELECT queries

### Key Annotations
- `@MyTable`: Maps entity classes to database tables with optional logical deletion support
- `@MyField`: Maps entity fields to database columns with serialization control

### Database Support
Multi-database support implemented through `SqlBuilder` variants. Database type detection happens in `BaseDaoImpl` constructor.

## Important Implementation Details

### Automatic Logical Delete Condition Injection
All query methods (`queryListForSql`, `querySingleForSql`, `queryPageForSql`, `queryById`, `querySingleByField`) automatically inject logical delete conditions via `JSqlDynamicSqlParser.appendDeleteCondition()`. There are no separate `WithDeleteCondition` methods — the standard methods handle everything.

The parser is idempotent: it checks `isDeleteConditionExists` before appending, so conditions are never duplicated even when methods delegate to each other internally.

### JOIN Condition Optimization
- **Main table (FROM)**: Delete conditions added to WHERE clause
- **LEFT/RIGHT JOIN**: Delete conditions added to JOIN ON clause (preserves outer join semantics)
- **INNER JOIN**: Delete conditions added to WHERE clause (better performance)

Tables without a configured `@MyTable` delete field are left untouched.

### SQL Transformation Examples
```sql
-- Original
SELECT * FROM user

-- Auto-transformed
SELECT * FROM user WHERE user.delete_flag = 0

-- JOIN queries
SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id
-- Becomes
SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id AND r.is_deleted = 0
WHERE u.delete_flag = 0
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
Currently no unit tests exist. When adding tests, use Spring Boot testing conventions with `src/test/java` structure.

## Configuration Properties
- `myjpa.showsql`: Enable/disable SQL statement logging

## Intelligent Package Scanning
The starter automatically detects and scans the main application package for @MyTable annotations:
- **Zero Configuration**: No need to specify scan packages manually
- **Smart Detection**: Automatically deduces the main application package from stack trace
- **Fallback Strategy**: Uses common business package patterns if auto-detection fails
- **Performance Optimized**: Only scans necessary packages, avoiding third-party libraries
