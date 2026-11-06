# Implementation Tasks: Evidence-Based Agricultural AI Rice Production Workflow

## Task Ordering & Execution Plan

- [x] 1. **Agronomic Model and Calculations**
  - [x] 1.1 Implement `PhilRiceRiceProductionEngine` with Module 4, 5, 6 formulas and seed/bed calculation methods.
  - [x] 1.2 Implement dynamic schedule generator calculating the 21-day pre-transplanting land preparation tasks, soaking/incubation, and seeding milestones.
  - [x] 1.3 Add comprehensive unit tests in `PhilRiceRiceProductionEngineTest` verifying exact kilogram rates and seedbed square meters.

- [x] 2. **Planting Readiness Evaluator**
  - [x] 2.1 Implement `PlantingReadinessEvaluator` returning `SUITABLE`, `CONDITIONALLY_SUITABLE`, `NOT_RECOMMENDED`, and `INSUFFICIENT_DATA`.
  - [x] 2.2 Wire real-time telemetry checks (soil moisture, water level, sensor freshness) into the evaluation pipeline.
  - [x] 2.3 Add readiness evaluation unit tests for missing fields, short deadlines (<14 days), and ideal conditions.

- [x] 3. **Conversational Workflow State Machine Refactoring**
  - [x] 3.1 Update `FarmingChatbotServiceImpl` with Step A–F progressive profiling: Crop -> Intended Date -> Field -> Method -> Variety -> Readiness -> Plan -> Confirmation -> Execution.
  - [x] 3.2 Implement task action tier tagging: `AI_EXECUTABLE`, `FARMER_EXECUTABLE`, and `EXPERT_REQUIRED`.
  - [x] 3.3 Add plan recalculation hook when task completion or date adjustments occur.

- [x] 4. **Zero-Trust Irrigation Interlock & Safety Boundaries**
  - [x] 4.1 Validate master state, emergency stop, operator confirmation, and telemetry freshness before dispatching permitted digital commands.
  - [x] 4.2 Distinguish recommended vs authorized vs dispatched vs verified actuation states.

- [x] 5. **Validation and Verification**
  - [x] 5.1 Run all unit tests and integration tests (`mvn test -Dtest=FarmingChatbotServiceTest,PhilRiceRiceProductionEngineTest`).
  - [x] 5.2 Validate end-to-end conversation flows via REST endpoint tests.

