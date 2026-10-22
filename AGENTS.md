# TasteDNA — Agent Instructions

## 1. Project Overview

TasteDNA is a fashion preference assessment system designed to integrate into existing fashion e-commerce platforms.

The goal is to transform user questionnaire responses into explainable fashion preference observations, Raw TasteDNA analysis, and RESULT classifications.

A small demonstration storefront may be built to showcase TasteDNA, but developing a full e-commerce platform is not the primary goal.

## 2. Technology Stack

- Java 21
- Spring Boot 4.1.1
- Gradle
- MySQL 8.4
- Spring Data JPA
- Flyway
- JUnit 5 and Mockito
- IntelliJ IDEA Ultimate

Follow existing project conventions and dependencies. Do not introduce new frameworks or libraries without a clear justification.

## 3. Repository Structure

- `definition`: Test and question definitions
- `session`: Session lifecycle, answer validation and persistence
- `scoring`: Observation collection and future analysis logic
- `config`: Application configuration and security
- `src/main/resources/db/migration`: Flyway database migrations
- `dev/fixtures`: Local development and verification data
- `docs`: Design contracts and technical documentation

Inspect existing code before creating new packages, abstractions, or services.

## 4. Core Business Rules

- SIMPLE assessment contains 10 questions.
- DEEP assessment contains 25 questions.
- The RESULT design includes 25 candidate categories.
- Do not invent RESULT scoring formulas, weights, thresholds, or classification rules.
- Preserve explicit user selections as evidence.
- Never interpret an unselected option as a negative preference without an approved rule.
- Preserve question IDs, option IDs, definition IDs, and relevant version information.
- Comparison questions must preserve comparative relationships rather than reducing them to ordinary single-choice observations.
- `MENS` and `WOMENS` represent apparel scopes, not preference scoring attributes.
- Do not generate artificial RESULT classifications simply to fill a requested number of results.

Design documents are authoritative for approved product decisions. When documents contain unresolved or conflicting rules, report the conflict instead of inventing a resolution.

## 5. Current Architecture

The observation pipeline includes:

1. Stored session answers
2. Selected option extraction
3. Observation rule resolution
4. Direct observation collection
5. Observation payload parsing
6. Raw observation aggregation
7. Development verification API

Raw observations are intermediate evidence, not final TasteDNA scores or RESULT classifications.

The `/raw-observations` endpoint is currently a development verification interface, not a finalized public product API.

Inspect repository source code to determine the actual implementation state. Do not assume a feature is complete merely because a class or endpoint exists.

## 6. Coding Principles

- Follow the Single Responsibility Principle without unnecessary fragmentation.
- Prefer existing services, repositories, validators, and data structures.
- Avoid duplicated implementations.
- Keep business logic out of controllers.
- Maintain clear separation between API, application, domain, and persistence responsibilities.
- Preserve backward compatibility unless a breaking change is explicitly approved.
- Keep implementations simple, readable, and maintainable.
- Avoid unnecessary refactoring outside the requested task.

## 7. Security and Data Integrity

- Never commit passwords, tokens, API keys, or other secrets.
- Use environment variables for credentials.
- Verify session ownership using authenticated identity.
- Do not trust client-provided scoring rules or definition identifiers.
- Do not alter existing production-like development data without explicit authorization.
- Distinguish the `tastedna` development database from the `tastedna_test` test database.
- Do not activate incomplete test definitions merely to pass a test.
- Do not use placeholder scoring values as genuine assessment results.

## 8. Testing and Verification

- Write meaningful tests for implemented behavior.
- Prioritize representative feature tests and critical failure boundaries.
- Avoid redundant tests for trivial implementations.
- Run relevant tests after changes when the environment allows.
- Report tests that could not be executed and explain why.
- Never claim successful execution without evidence.
- Prefer reproducible fixtures and isolated test data.
- Avoid tests that permanently modify or delete shared development data.

## 9. Working Agreement

Before implementing a task:

1. Inspect relevant existing files and documentation.
2. Summarize the intended change.
3. Identify affected files and dependencies.
4. State unresolved assumptions or design decisions.

During implementation:

- Work within the approved scope.
- Avoid unrelated changes.
- Do not silently change business requirements.
- Do not generate fake functionality to simulate completion.

After implementation:

- Summarize changed files.
- Explain what behavior was implemented.
- Report tests performed and their outcomes.
- Identify remaining limitations.

Do not perform destructive database operations, dependency upgrades, Git pushes, or major architectural changes without explicit approval.

## 10. Development Priorities

Prioritize an operational, explainable TasteDNA assessment flow:

Questionnaire → Answers → Observations → Raw TasteDNA → RESULT → User-facing presentation.

Maintain integration readiness for existing shopping malls.

Favor demonstrable functionality over premature abstraction or excessive scaffolding.

Implementation priorities and delivery milestones may change based on explicit user instructions.

## 11. Source of Truth

Use the following priority when resolving implementation questions:

1. Explicit instructions for the current approved task
2. Approved TasteDNA product and scoring design documents
3. Existing repository contracts and code
4. These repository-level engineering instructions

If important requirements conflict, explain the conflict and request a decision rather than silently choosing a new product policy.
