# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is a Spring Boot starter (`myjpa-spring-boot-starter`) that provides simplified JPA-like functionality using Spring JDBC Template. It offers annotation-driven entity mapping with multi-database support.

## Common Development Commands

```bash
# Build and compile
mvn clean compile

# Create JAR package
mvn clean package

# Install to local Maven repository
mvn clean install

# Deploy to Maven Central (requires release profile)
mvn clean deploy -P release
```

## Architecture Overview

### Core Components
- **Auto-Configuration**: `MyJpaAutoConfiguration` registers core beans and configures SQL logging
- **Service Layer**: `IBaseService` + `BaseServiceImpl` provide CRUD operations
- **DAO Layer**: `IBaseDao` + `BaseDaoImpl` handle data access via Spring JDBC Template
- **SQL Generation**: `SqlBuilder` with database-specific implementations (MySQL, Oracle, SQL Server, PostgreSQL, KingbaseES)
- **Metadata Management**: `TableInfoBuilder` scans `@MyTable` annotations at startup

### Key Annotations
- `@MyTable`: Maps entity classes to database tables with optional logical deletion support
- `@MyField`: Maps entity fields to database columns with serialization control

### Database Support
Multi-database support implemented through `SqlBuilder` variants. Database type detection happens in `BaseDaoImpl` constructor.

## Important Implementation Details

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
- Spring Boot 2.7.14 (via spring-boot-starter-jdbc)
- Custom `mycommon` library (version 2401)
- Reflections library for annotation scanning
- Log4j2 for logging

## Testing Notes
Currently no unit tests exist. When adding tests, use Spring Boot testing conventions with `src/test/java` structure.

## New Features (Dynamic SQL with Delete Conditions)

### Core Components
- **TableCacheManager**: Caches @MyTable annotation information at startup
- **DynamicSqlParser**: Automatically appends delete conditions to SELECT queries
- **Enhanced IBaseDao**: New query methods with automatic delete condition support

### New Query Methods
- `queryListForSqlWithDeleteCondition()`: Auto-detect tables and append delete conditions
- `queryPageForSqlWithDeleteCondition()`: Paginated queries with delete conditions
- `querySingleForSqlWithDeleteCondition()`: Single record queries with delete conditions

### SQL Transformation Examples
```sql
-- Original
SELECT * FROM user

-- Auto-transformed  
SELECT * FROM user WHERE user.delete_flag = 0

-- JOIN queries
SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id
-- Becomes
SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id 
WHERE u.delete_flag = 0 AND r.is_deleted = 1
```

### Usage Patterns
1. **Automatic mode**: Based on table names in SQL
2. **Original mode**: Use existing methods when no delete condition filtering needed

### JOIN Condition Optimization
The system intelligently handles different JOIN types to preserve SQL semantics:
- **Main table (FROM)**: Delete conditions in WHERE clause
- **LEFT/RIGHT JOIN**: Delete conditions in JOIN ON clause (preserves outer join semantics)
- **INNER JOIN**: Delete conditions in WHERE clause (better performance)

## Configuration Properties
- `myjpa.showsql`: Enable/disable SQL statement logging

## Intelligent Package Scanning
The starter automatically detects and scans the main application package for @MyTable annotations:
- **Zero Configuration**: No need to specify scan packages manually
- **Smart Detection**: Automatically deduces the main application package from stack trace
- **Fallback Strategy**: Uses common business package patterns if auto-detection fails
- **Performance Optimized**: Only scans necessary packages, avoiding third-party libraries