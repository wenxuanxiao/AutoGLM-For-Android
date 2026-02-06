# AGENTS.md - AutoGLM For Android Development Guide

This file provides guidance for agentic coding agents working on this codebase.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore credentials)
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.kevinluo.autoglm.ExampleUnitTest"

# Run a single test method
./gradlew test --tests "com.kevinluo.autoglm.action.ActionParserTest.parseTapAction"

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Lint checks
./gradlew lint
```

## Project Overview

AutoGLM For Android is a native Android application that uses AI to automate smartphone tasks. Key technical details:

- **Language**: Kotlin 2.0.21
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **Build Tool**: Gradle 8.13.2 with Android Gradle Plugin
- **Test Framework**: Kotest 5.8.0 with JUnit 5
- **Core Dependency**: Shizuku 13.1.5 for system-level operations

## Code Style Guidelines

### Kotlin Conventions

- **Line length**: Maximum 120 characters
- **Indent style**: Spaces (4 spaces for Kotlin/Gradle, 2 for JSON/YAML)
- **Wildcard imports**: Disabled (see .editorconfig)
- **Blank lines**: Required before declarations (ktlint rule enabled)
- **Declarations with annotations**: Require spacing

### Naming Conventions

- **Classes/Interfaces**: PascalCase (e.g., `PhoneAgent`, `ActionHandler`)
- **Functions/Properties**: camelCase (e.g., `executeAction`, `currentStepNumber`)
- **Constants**: UPPER_SNAKE_CASE in companion objects (e.g., `MAX_EMPTY_ACTION_RETRIES`)
- **Package names**: Lowercase, no underscores (e.g., `com.kevinluo.autoglm.agent`)
- **Test classes**: Suffix with `Test` or `PropertyTest` (e.g., `ActionParserPropertyTest`)

### Data Classes

Use `data class` for simple holders. Example from codebase:

```kotlin
data class AgentConfig(
    val maxSteps: Int = 0,
    val language: String = "cn",
    val verbose: Boolean = true,
    val screenshotDelayMs: Long = 2000L
)
```

### Error Handling

- Use `ErrorHandler.handle*` functions for consistent error handling
- Log errors with appropriate level: `Logger.e()` for errors, `Logger.w()` for warnings
- Return user-friendly messages with `HandledError.userMessage`
- Always wrap risky operations in try-catch blocks

### Coroutines

- Use `suspend` for asynchronous functions
- Handle `CancellationException` explicitly for cancellation support
- Use `coroutineScope` for structured concurrency
- Check `ensureActive()` in loops for cancellability

### Imports

- No wildcard imports (disabled by ktlint rule)
- Explicit imports for all dependencies
- Group imports: standard library, AndroidX, third-party, project-local

### Documentation

- Use KDoc for public API documentation
- Include feature tags and requirement validation notes in test documentation
- Document callback interfaces and their contract

### Testing

- Property-based tests use Kotest with `Arb` generators
- Test naming: `ClassNameTest` for examples, `ClassNamePropertyTest` for properties
- Tests validate against requirements specified in documentation comments
- Use `shouldBe`, `shouldNotBe`, `shouldContain` matchers

### File Structure

```
app/src/main/java/com/kevinluo/autoglm/
├── action/           # Action execution (ActionHandler, ActionParser)
├── agent/            # Core agent logic (PhoneAgent, AgentContext)
├── app/              # Application lifecycle
├── config/           # Configuration (I18n, SystemPrompts)
├── device/           # Device operations via Shizuku
├── history/          # Task history management
├── input/            # Keyboard and text input
├── model/            # LLM API client
├── screenshot/       # Screenshot capture
├── settings/         # Settings management
├── ui/               # UI components (floating window, activities)
└── util/             # Utilities (logging, coordinates, errors)
```

### Key Patterns

- **Companion objects**: Place at bottom of class following code style
- **Atomic references**: Use `AtomicReference`/`AtomicBoolean` for thread-safe state
- **Callback interfaces**: Define as nested interfaces (e.g., `ConfirmationCallback`)
- **Result types**: Use data classes like `ActionResult`, `StepResult`, `TaskResult`
- **State management**: Use `AgentState` enum (IDLE, RUNNING, PAUSED, CANCELLED)
