# Proposal: Evidence-Based Agricultural AI Rice Production Workflow

## Why
While ArvaCore's backend contains a prototype chatbot and task state machine (`FarmingChatbotServiceImpl`), it lacks rigorous, evidence-based agronomic rules, persistent multi-session plan state tracking, deterministic seed/fertilizer calculations, and strict alignment with authoritative Philippine agricultural standards—specifically PhilRice's *Philippine Rice Production Training Manual* (2003). Farmers currently receive generic recommendations without explicit citations, land preparation timelines (the critical 21-day pre-transplanting breakdown), soaking/incubation protocols, or transparent distinction between AI-executable, farmer-executable, and expert-required actions.

## What Changes
1. **PhilRice-Grounded Agronomic Knowledge Engine**:
   - Codify verified agricultural standards from PhilRice Modules 4, 5, 6, 7, and 8.
   - Enforce the **21-day wetland land preparation interval**: Day 1 primary plowing, 5–7 day submergence/organic matter decomposition, harrowing cycles at 5–7 day intervals (1st along plowing, 2nd crosswise, 3rd final leveling 1 day before crop establishment).
   - Dynamic calculation models for:
     - **Seed Rate**: Certified wetbed transplanting (20–40 kg/ha), dapog (45–65 kg/ha), direct wet-seeding (40–80 kg/ha), and dry seeding (60–80 kg/ha).
     - **Seedbed Area**: 400 m²/ha for wetbed; 60 m²/ha for dapog.
     - **Seed Soaking & Incubation**: 24 hr soaking in clean/running water, 24–36 hr incubation (slotted pallet aeration, half-filled sacks, embryo white-dot emergence).
     - **Nutrient Management**: MOET / Soil Test Kit (STK) requirements; no uncalibrated chemical N-P-K prescriptions from unrelated sensors.

2. **Step A–F Conversational Workflow & Plan Recalculator**:
   - Progressive profiling: Crop $\to$ Intended Start/Harvest Date $\to$ Field/Area $\to$ Method (Wetbed / Dapog / Direct-seeding) $\to$ Variety $\to$ Water Source.
   - Four-state Planting Readiness Evaluator: `SUITABLE`, `CONDITIONALLY_SUITABLE`, `NOT_RECOMMENDED`, or `INSUFFICIENT_DATA`.
   - Real-time plan schedule adjustments: When a farmer delays soaking, plowing, or leveling, subsequent dependent tasks dynamically recalculate while maintaining biological buffer windows.

3. **Three-Tier Action Classification & Zero-Trust Safety Interlocks**:
   - **`AI_EXECUTABLE`**: Digital operations with zero physical risk (e.g., config downlinks, schedule registrations, reading telemetry).
   - **`FARMER_EXECUTABLE`**: Physical labor (plowing, soaking seeds, leveling, manual weeding, harvesting).
   - **`EXPERT_REQUIRED`**: Agronomic diagnoses (soil STK analysis, chemical pesticide prescriptions, unverified pest outbreaks).
   - **Irrigation Interlocks**: Zero pump or gate actuation without explicit operator confirmation, active master state, and hardware verification.

4. **Multi-Field and Context Preservation**:
   - Context-preserving Q&A: Farmers can query the AI about specific tasks, snail management, or weather risks without losing active plan state or resetting the workflow.

## Non-Goals
- Replacing ArvaCore's low-level C++ pump controller logic (`sketch_pump_controller`).
- Prescribing chemical pesticide brand names or unverified toxic agrochemicals.
- Guessing N-P-K soil chemistry from standard electrical conductivity or moisture sensors without verified laboratory or STK inputs.

