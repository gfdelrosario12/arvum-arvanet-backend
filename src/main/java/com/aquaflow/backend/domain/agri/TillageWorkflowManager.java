package com.aquaflow.backend.domain.agri;

import com.aquaflow.backend.entity.PlantingPlan;
import com.aquaflow.backend.entity.PlantingTask;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 8-Stage Rice Farm Land Preparation Workflow Generator.
 * Aligned with PhilRice Philippine Rice Production Training Manual (2003) & IRRI AWD Protocols.
 * Stages:
 * 1. PRIMARY_PLOWING (T-21 to T-15): Moldboard / disc plowing or rotary tilling to 15-20 cm depth.
 * 2. DECOMPOSITION_SUBMERGENCE (T-15 to T-14): 5-7 days continuous submergence (3-5 cm) for organic matter decay.
 * 3. FIRST_HARROWING (T-14): Along plowing direction, initial clod reduction & volunteer weed stimulation.
 * 4. SECOND_HARROWING (T-7): Crosswise cultivation, incorporating residues & destroying weed flushes.
 * 5. THIRD_HARROWING (T-2 to T-1): Final puddle refinement and soil softening.
 * 6. LAND_LEVELING (T-1): Wooden plank leveling (Haba) and inspection of inlets, outlets, and gates.
 * 7. FINAL_READINESS (T-0): 21-day timeline verification, ArvaNode water/soil/NPK readiness assessment.
 * 8. CROP_ESTABLISHMENT: Transition to transplanting or direct seeding.
 */
@Component
public class TillageWorkflowManager {

    public List<PlantingTask> buildEightStageTillageTasks(PlantingPlan plan, boolean isEStopActive) {
        List<PlantingTask> tasks = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        // Stage 1: PRIMARY_PLOWING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("PRIMARY_PLOWING")
                .title("Primary Tillage - Initial Plowing (T-21 Days)")
                .description("Operate moldboard/disc plow or rotavator to 15-20 cm depth. Cut, turn, and loosen soil while incorporating remaining crop residues.")
                .status("PENDING")
                .priority("HIGH")
                .evidenceSource("PhilRice 21-Day Land Preparation Protocol")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 2: DECOMPOSITION_SUBMERGENCE (Automated Water Management via ArvaGate/Pump)
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .tillageStage("DECOMPOSITION_SUBMERGENCE")
                .title("Water Application & Organic Matter Decomposition (5-7 Days Submergence)")
                .description("Automated flood management: Maintain controlled 3.0 to 5.0 cm water layer across terraces via ArvaGate and central pump to promote organic matter decomposition and soften clods.")
                .status(isEStopActive ? "BLOCKED" : "PENDING")
                .priority("HIGH")
                .evidenceSource("IRRI Soil Gas and Organic Matter Decomposition Standard")
                .blockingReason(isEStopActive ? "Emergency stop is active on main circuit." : null)
                .requiresConfirmation(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 2B: DRAINAGE MONITORING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .tillageStage("DECOMPOSITION_SUBMERGENCE")
                .title("Post-Decomposition Field Drainage for Weed Germination")
                .description("Allow field water to drain naturally after decomposition to induce volunteer rice and weed seed germination prior to harrowing.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("PhilRice Weed Flush Management")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 3: FIRST_HARROWING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("FIRST_HARROWING")
                .title("First Harrowing - Along Plowing Direction (T-14 Days)")
                .description("Operate comb harrow (suyod) along the same pattern as primary plowing to break large soil clods and disturb decomposing residues.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("PhilRice PalayCheck Key Check 2")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 4: SECOND_HARROWING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("SECOND_HARROWING")
                .title("Second Harrowing - Crosswise Cultivation & Weed Flush Control (T-7 Days)")
                .description("Operate harrow crosswise to first pass. Pulverize remaining clods, mix organic residues, and destroy germinated weed flushes.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("PhilRice Weed & Stubble Management")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 5: THIRD_HARROWING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("THIRD_HARROWING")
                .title("Third/Final Harrowing - Final Puddling & Soil Conditioning (T-2 Days)")
                .description("Perform final harrowing pass to achieve soft, puddled soil consistency ideal for seedling root anchorage.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("PhilRice Puddled Field Criteria")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 6: LAND_LEVELING
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("LAND_LEVELING")
                .title("Land Leveling & Water Distribution Preparation (T-1 Day)")
                .description("Level field using wooden plank (Haba). Inspect irrigation inlets, ArvaGate flumes, and drainage outlets to eliminate high and low spots.")
                .status("PENDING")
                .priority("HIGH")
                .evidenceSource("PhilRice Key Check 2: Level Field (<2 cm water variation)")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 7: FINAL_READINESS
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .tillageStage("FINAL_READINESS")
                .title("Final 21-Day Pre-Planting Readiness & Soil Assessment (T-0 Day)")
                .description("Validate 21-day timeline completion, sensor perched water table baseline, capacitive soil moisture, and NPK nutrient balance across all 6 ArvaNodes.")
                .status("PENDING")
                .priority("CRITICAL_PREREQUISITE")
                .evidenceSource("ArvaNode Multi-Sensor Mesh & PhilRice 21-Day Decomposition Requirement")
                .requiresConfirmation(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage 8: CROP_ESTABLISHMENT
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .tillageStage("CROP_ESTABLISHMENT")
                .title("Crop Establishment - Seedling Transplanting or Direct Wet Seeding")
                .description("Transplant certified seedlings (2-3 seedlings/hill at 20x20 cm) or direct wet seed according to verified planting plan.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("DA-PhilRice Crop Establishment Standard")
                .requiresConfirmation(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        if (isEStopActive) {
            tasks.add(PlantingTask.builder()
                    .plantingPlan(plan)
                    .category("SAFETY_BLOCKED")
                    .tillageStage("DECOMPOSITION_SUBMERGENCE")
                    .title("Clear Active Emergency Stop")
                    .description("The main irrigation circuit is in Emergency Stop. Deactivate E-Stop to enable automated gate and pump actuation.")
                    .status("BLOCKED")
                    .priority("CRITICAL_PREREQUISITE")
                    .evidenceSource("ArvaCore Hardware Interlock")
                    .blockingReason("Physical Emergency Stop latch is engaged.")
                    .requiresConfirmation(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        return tasks;
    }
}
