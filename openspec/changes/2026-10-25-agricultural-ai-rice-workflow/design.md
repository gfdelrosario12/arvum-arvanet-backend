# Design: Evidence-Based Agricultural AI Rice Production Workflow

## Architecture Overview

The Agricultural AI Workflow integrates into ArvaCore's Spring Boot backend alongside the IoT Ingestion Engine, ArvaGate irrigation subsystem, and ArvaLink Flutter dashboard:

```mermaid
flowchart TD
    subgraph UI ["ArvaLink Farmer Application (Flutter)"]
        CHAT_UI["FarmingChatbotScreen"]
        PLAN_CARD["PlantingPlanProgressView"]
        TASK_ITEM["ActionableTaskTile (AI / Farmer / Expert)"]
        CONFIRM_DLG["Readiness & Proceed Confirmation"]
    end

    subgraph CoreBackend ["ArvaNet Backend (Spring Boot 4.1.1)"]
        CONTROLLER["FarmingChatbotController (/api/v1/farming-chat/*)"]
        SERVICE["FarmingChatbotServiceImpl"]
        AGRI_ENGINE["PhilRiceAgronomicEngine (Deterministic Rules)"]
        CALC_ENGINE["AgriculturalCalculatorService (Seeds/Beds/Dates)"]
        SAFETY_INTERLOCK["IrrigationSafetyInterlockValidator"]
    end

    subgraph DataPersistence ["PostgreSQL 16"]
        SESSIONS["chatbot_sessions (State Machine)"]
        MESSAGES["chatbot_messages (History)"]
        PLANS["planting_plans (Context & Readiness)"]
        TASKS["planting_tasks (Dependencies & Classifications)"]
        TELEMETRY["telemetry_readings & edge_nodes"]
    end

    CHAT_UI -->|POST /api/v1/farming-chat/message| CONTROLLER
    CONTROLLER --> SERVICE
    SERVICE --> AGRI_ENGINE
    SERVICE --> CALC_ENGINE
    SERVICE --> SAFETY_INTERLOCK
    SERVICE --> SESSIONS
    SERVICE --> PLANS
    SERVICE --> TASKS
    SAFETY_INTERLOCK -.->|Validate| TELEMETRY
    PLANS --> PLAN_CARD
    TASKS --> TASK_ITEM
    CONFIRM_DLG -->|POST /api/v1/farming-chat/plans/{id}/confirm| CONTROLLER
```

## Technical Specifications & Domain Rules

### 1. PhilRice Agronomic Calculation Specifications
All calculations are deterministic and unit-tested:
* **Transplanting Wetbed**:
  * Seed Rate: $20\text{--}40\text{ kg/ha}$ (Default: $30\text{ kg/ha}$). For area $A$ hectares: $\text{Seeds} = 30 \times A\text{ kg}$.
  * Seedbed Area: $400\text{ m}^2/\text{ha}$. For area $A$: $\text{Bed Area} = 400 \times A\text{ m}^2$.
  * Seed Soaking: $24\text{ hours}$ in clean running water.
  * Seed Incubation: $24\text{--}36\text{ hours}$ until white dot (radicle) emerges.
  * Seedling Age at Transplanting: $20\text{--}25\text{ days after sowing (DAS)}$.
* **Transplanting Dapog**:
  * Seed Rate: $45\text{--}65\text{ kg/ha}$ (Default: $55\text{ kg/ha}$).
  * Seedbed Area: $60\text{ m}^2/\text{ha}$ on banana leaves/plastic/concrete.
  * Incubation: $36\text{--}48\text{ hours}$.
  * Seedling Age: $9\text{--}14\text{ DAS}$.
* **Direct Wet-Seeding**:
  * Seed Rate: $40\text{--}80\text{ kg/ha}$.
  * Saturated paddy, zero standing water at broadcasting.
* **Land Preparation (21-Day Pre-Transplanting Timeline)**:
  * $\mathbf{T-21\text{ to }T-20}$: Dike repair, flooding/irrigation, primary plowing.
  * $\mathbf{T-19\text{ to }T-14}$: 5–7 days submergence to soften clods and decompose organic matter / eradicate volunteer weed seeds.
  * $\mathbf{T-14\text{ to }T-13}$: 1st harrowing (lengthwise along plowing pattern).
  * $\mathbf{T-7\text{ to }T-6}$: 2nd harrowing (crosswise) to incorporate crop residues.
  * $\mathbf{T-1}$: 3rd final harrowing and wooden plank leveling.

### 2. Planting Readiness State Model
* `SUITABLE`: Field verified ready, water available, land prepared $\ge 21\text{ days}$, seed variety matched to season.
* `CONDITIONALLY_SUITABLE`: Timing possible but requires specific prerequisite completion (e.g., land preparation not yet started, dike repairs pending).
* `NOT_RECOMMENDED`: Severe weather warning from PAGASA, drought with no irrigation source, or target planting date falls under $<14\text{ days}$ preparation window.
* `INSUFFICIENT_DATA`: Missing target field area, variety, or water availability.

### 3. Action Taxonomy
1. `AI_EXECUTABLE`: Setting system configs, telemetry snapshots, registering schedules.
2. `FARMER_EXECUTABLE`: Plowing, dike repair, seed soaking, incubation, harrowing, leveling, transplanting, weeding.
3. `EXPERT_REQUIRED`: MOET soil testing, diagnostic verification of viral diseases (tungro/grassy stunt), specialized chemical prescription.

