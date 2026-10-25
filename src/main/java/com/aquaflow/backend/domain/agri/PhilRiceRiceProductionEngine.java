package com.aquaflow.backend.domain.agri;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Philippine Rice Production Agronomic Engine.
 * Formulated from PhilRice (Philippine Rice Research Institute),
 * "Philippine Rice Production Training Manual" (2003).
 *
 * Grounded Modules:
 * - Module 4: Choosing the Appropriate Variety
 * - Module 5: Land Preparation & the 21-Day Pre-Transplanting Timeline
 * - Module 6: Methods of Crop Establishment (Wetbed, Dapog, Direct-seeding)
 * - Module 7: Water & Irrigation Management
 * - Module 8: Integrated Nutrient Management & MOET/STK Soil Testing
 */
public class PhilRiceRiceProductionEngine {

    @Data
    @Builder
    public static class SeedingPlan {
        private String method; // "WETBED", "DAPOG", "DIRECT_WET", "DIRECT_DRY"
        private double fieldAreaHectares;
        private double minSeedKg;
        private double maxSeedKg;
        private double recommendedSeedKg;
        private double seedbedAreaM2;
        private int soakingHours;
        private int minIncubationHours;
        private int maxIncubationHours;
        private int minSeedlingAgeDays;
        private int maxSeedlingAgeDays;
        private String seedbedPreparationNotes;
        private String soakingAndIncubationInstructions;
        private String citation;
    }

    @Data
    @Builder
    public static class FarmingTaskSchedule {
        private String taskName;
        private String category; // "LAND_PREPARATION", "SEEDLING_MANAGEMENT", "WATER_MANAGEMENT", "NUTRIENT_MANAGEMENT"
        private String actionTier; // "AI_EXECUTABLE", "FARMER_EXECUTABLE", "EXPERT_REQUIRED"
        private LocalDate plannedStartDate;
        private LocalDate plannedEndDate;
        private String instructions;
        private String agronomicReason;
        private String citation;
        private List<String> prerequisites;
        private String materials;
        private String completionCriteria;
    }

    @Data
    @Builder
    public static class ReadinessAssessment {
        public enum ReadinessStatus {
            SUITABLE,
            CONDITIONALLY_SUITABLE,
            NOT_RECOMMENDED,
            INSUFFICIENT_DATA
        }

        private ReadinessStatus status;
        private String summary;
        private List<String> reasons;
        private List<String> requiredActionsBeforeProceeding;
        private List<String> uncertainties;
        private String citation;
    }

    /**
     * Calculates seed requirements, seedbed area, and germination parameters strictly per PhilRice Module 6.
     */
    public static SeedingPlan calculateSeedingPlan(String method, double hectares) {
        if (hectares <= 0) {
            hectares = 1.0;
        }

        if ("DAPOG".equalsIgnoreCase(method)) {
            // PhilRice Module 6 Table V: Dapog 45-65 kg/ha, 60 m2/ha seedbed, 24 hr soak, 36-48 hr incubation, 9-14 days transplanting
            double rec = 55.0 * hectares;
            return SeedingPlan.builder()
                    .method("DAPOG")
                    .fieldAreaHectares(hectares)
                    .minSeedKg(45.0 * hectares)
                    .maxSeedKg(65.0 * hectares)
                    .recommendedSeedKg(rec)
                    .seedbedAreaM2(60.0 * hectares)
                    .soakingHours(24)
                    .minIncubationHours(36)
                    .maxIncubationHours(48)
                    .minSeedlingAgeDays(9)
                    .maxSeedlingAgeDays(14)
                    .seedbedPreparationNotes("Construct beds covered with banana leaves, plastic sheets, or concrete floor to prevent roots penetrating soil. Facilitates rolling seedlings into a mat.")
                    .soakingAndIncubationInstructions("Soak seeds for 24 hours in clean running water. Incubate for 36-48 hours in half-filled sacks on slotted wooden pallets with ample aeration. Press germinating seedlings gently with a pressing board.")
                    .citation("PhilRice Training Manual (2003), Module 6: Methods of Crop Establishment, Table V & VII, pp. 62-63.")
                    .build();
        } else if ("DIRECT_WET".equalsIgnoreCase(method) || "DIRECT_SEEDING".equalsIgnoreCase(method)) {
            // PhilRice Module 6 Section XVII: Wet seeding 40-80 kg/ha broadcast into saturated field without standing water
            double rec = 60.0 * hectares;
            return SeedingPlan.builder()
                    .method("DIRECT_WET")
                    .fieldAreaHectares(hectares)
                    .minSeedKg(40.0 * hectares)
                    .maxSeedKg(80.0 * hectares)
                    .recommendedSeedKg(rec)
                    .seedbedAreaM2(0.0)
                    .soakingHours(24)
                    .minIncubationHours(24)
                    .maxIncubationHours(36)
                    .minSeedlingAgeDays(0)
                    .maxSeedlingAgeDays(0)
                    .seedbedPreparationNotes("Direct broadcasting onto puddled, leveled paddy. Field must be fully saturated with ZERO standing water at sowing time.")
                    .soakingAndIncubationInstructions("Soak for 24 hours, incubate for 24-36 hours until root radical shows a white dot. Air-dry seeds briefly before broadcasting for uniform distribution.")
                    .citation("PhilRice Training Manual (2003), Module 6, Section XVII: Wet Seeding Method, pp. 65-66.")
                    .build();
        } else {
            // Standard Certified Wetbed Transplanting: 20-40 kg/ha, 400 m2/ha seedbed, 24 hr soak, 24-36 hr incubation, 20-25 days transplanting
            double rec = 30.0 * hectares;
            return SeedingPlan.builder()
                    .method("WETBED")
                    .fieldAreaHectares(hectares)
                    .minSeedKg(20.0 * hectares)
                    .maxSeedKg(40.0 * hectares)
                    .recommendedSeedKg(rec)
                    .seedbedAreaM2(400.0 * hectares)
                    .soakingHours(24)
                    .minIncubationHours(24)
                    .maxIncubationHours(36)
                    .minSeedlingAgeDays(20)
                    .maxSeedlingAgeDays(25)
                    .seedbedPreparationNotes("Prepare 1-1.5 m wide seedbed plots raised 4-5 cm with 40 cm drainage canals between beds. Apply 5 bags organic fertilizer per 400 m2. Sow at 50-100 g/m2.")
                    .soakingAndIncubationInstructions("Soak for 24 hours in clean or running water. Incubate for 24-36 hours in half-filled tied sacks on wooden pallets. Water seedbed 2-3 days after sowing.")
                    .citation("PhilRice Training Manual (2003), Module 6, Section V & VI: Wetbed Method, pp. 62-63.")
                    .build();
        }
    }

    /**
     * Generates a complete, dated pre-transplanting land preparation and seeding timeline
     * strictly implementing PhilRice Module 5's 21-day wetland preparation rule.
     */
    public static List<FarmingTaskSchedule> generateFarmingSchedule(LocalDate targetTransplantingDate, String method, double hectares) {
        if (targetTransplantingDate == null) {
            targetTransplantingDate = LocalDate.now().plusDays(25);
        }

        SeedingPlan plan = calculateSeedingPlan(method, hectares);
        List<FarmingTaskSchedule> tasks = new ArrayList<>();

        // T-21 Days: Primary Plowing and Dike Repair
        LocalDate d21 = targetTransplantingDate.minusDays(21);
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("Dike Repair and Primary Tillage (Plowing)")
                .category("LAND_PREPARATION")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(d21)
                .plannedEndDate(d21.plusDays(1))
                .instructions("Repair and plaster field bunds/dikes to impound water. Irrigate field until soft, then plow using moldboard plow, disc plow, or floating tiller.")
                .agronomicReason("Primary tillage cuts and inverts the soil. Preparing 21 days before transplanting protects delicate seedlings from toxic decomposing organic gases, allows nutrient mineralization, and destroys sprouted weed seeds.")
                .citation("PhilRice Training Manual (2003), Module 5: Land Preparation, Sections VI & XII, pp. 54-56.")
                .prerequisites(List.of("Adequate water access or scheduled canal release"))
                .materials(String.format("Hand tractor or draft carabao with moldboard plow; mud shovel for dike plastering (Field area: %.2f ha)", hectares))
                .completionCriteria("Dikes are intact without leaks; soil is inverted and flooded to 2-3 cm depth.")
                .build());

        // T-19 to T-14 Days: 5 to 7 Days Soil Submergence & Decomposition
        LocalDate d19 = d21.plusDays(2);
        LocalDate d14 = d19.plusDays(5);
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("Field Submergence for Organic Matter Decomposition")
                .category("LAND_PREPARATION")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(d19)
                .plannedEndDate(d14)
                .instructions("Keep the plowed field submerged under 3-5 cm of water for 5 to 7 days. Allow natural water drawdown to promote volunteer and weed seed germination.")
                .agronomicReason("Submergence softens heavy soil clods, hastens the biological decomposition of incorporated rice stubbles/straw, and induces weed seed germination so they can be eradicated during subsequent harrowing.")
                .citation("PhilRice Training Manual (2003), Module 5, Section VI.D, pp. 54-55.")
                .prerequisites(List.of("Dike Repair and Primary Tillage (Plowing)"))
                .materials("Standing ponded water (monitored via ArvaCore or staff gauge)")
                .completionCriteria("Field kept flooded for minimum 5 days; organic residue shows softening.")
                .build());

        // T-14 Days: First Harrowing
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("First Harrowing (Along Plowing Pattern)")
                .category("LAND_PREPARATION")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(d14)
                .plannedEndDate(d14)
                .instructions("Pass the comb-tooth harrow lengthwise along the plowing pattern.")
                .agronomicReason("Breaks up the softened soil clods and puddles the topsoil.")
                .citation("PhilRice Training Manual (2003), Module 5, Section VI.E, p. 55.")
                .prerequisites(List.of("Field Submergence for Organic Matter Decomposition"))
                .materials("Comb-tooth harrow implement with tractor or animal draft")
                .completionCriteria("Major clods reduced in size; uniform soil puddle.")
                .build());

        // Seed Preparation Scheduling based on crop establishment method
        if ("WETBED".equalsIgnoreCase(plan.getMethod())) {
            // Sowing occurs at T-21 or T-20 days so seedlings reach 20-21 days by transplanting
            LocalDate soakDate = targetTransplantingDate.minusDays(22);
            tasks.add(FarmingTaskSchedule.builder()
                    .taskName("Seed Selection and 24-Hour Soaking")
                    .category("SEEDLING_MANAGEMENT")
                    .actionTier("FARMER_EXECUTABLE")
                    .plannedStartDate(soakDate)
                    .plannedEndDate(soakDate.plusDays(1))
                    .instructions(String.format("Select certified seeds (%.1f kg recommended). Submerge seeds in clean or running water for exactly 24 hours. Discard empty floating grains.", plan.getRecommendedSeedKg()))
                    .agronomicReason("Hydration triggers enzymatic activation and metabolic germination of the dormant embryo.")
                    .citation("PhilRice Training Manual (2003), Module 6: Methods of Crop Establishment, Section III.A & VI.F, pp. 60-62.")
                    .prerequisites(List.of("Certified seed acquisition (PhilRice accredited)"))
                    .materials(String.format("%.1f kg certified seeds, clean water container / drum", plan.getRecommendedSeedKg()))
                    .completionCriteria("Seeds fully soaked for 24 hours and saturated.")
                    .build());

            LocalDate incubateDate = soakDate.plusDays(1);
            tasks.add(FarmingTaskSchedule.builder()
                    .taskName("Seed Incubation on Aerated Wooden Pallet")
                    .category("SEEDLING_MANAGEMENT")
                    .actionTier("FARMER_EXECUTABLE")
                    .plannedStartDate(incubateDate)
                    .plannedEndDate(incubateDate.plusDays(1))
                    .instructions("Drain water. Place seeds in sacks filled only to one-half capacity. Tie securely and place on a raised slotted wooden pallet. Keep warm for 24-36 hours. Do not pile sacks.")
                    .agronomicReason("Aeration and moderate internal warmth accelerate uniform embryo emergence without overheating or suffocating roots.")
                    .citation("PhilRice Training Manual (2003), Module 6, Section III.B-D, pp. 60-61.")
                    .prerequisites(List.of("Seed Selection and 24-Hour Soaking"))
                    .materials("Burlap sacks, slotted wooden pallets")
                    .completionCriteria("White dot (radicle) visible on seed tips; no over-extended roots.")
                    .build());

            LocalDate sowDate = incubateDate.plusDays(1);
            tasks.add(FarmingTaskSchedule.builder()
                    .taskName(String.format("Seedbed Preparation and Sowing (%.0f m² Area)", plan.getSeedbedAreaM2()))
                    .category("SEEDLING_MANAGEMENT")
                    .actionTier("FARMER_EXECUTABLE")
                    .plannedStartDate(sowDate)
                    .plannedEndDate(sowDate)
                    .instructions(String.format("Construct 1-1.5 m wide beds raised 4-5 cm. Apply organic fertilizer. Broadcast incubated seeds at 50-100 g/m² across %.0f m² seedbed. Irrigate 2-3 days later.", plan.getSeedbedAreaM2()))
                    .agronomicReason("Provides nutrient-rich, disease-isolated environment away from adult insect pests and light attractions.")
                    .citation("PhilRice Training Manual (2003), Module 6, Section IV & VI, pp. 61-62.")
                    .prerequisites(List.of("Seed Incubation on Aerated Wooden Pallet"))
                    .materials(String.format("Incubated seeds, %.0f m² seedbed, organic fertilizer / carbonized rice hull", plan.getSeedbedAreaM2()))
                    .completionCriteria("Seeds sown uniformly without clustering; drainage canals unobstructed.")
                    .build());
        }

        // T-7 Days: Second Harrowing
        LocalDate d7 = targetTransplantingDate.minusDays(7);
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("Second Harrowing (Crosswise Pattern)")
                .category("LAND_PREPARATION")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(d7)
                .plannedEndDate(d7)
                .instructions("Harrow crosswise (perpendicular to the first pass) 7 days after the first harrowing.")
                .agronomicReason("Thoroughly incorporates decomposed organic crop residues and eradicates newly germinated weeds.")
                .citation("PhilRice Training Manual (2003), Module 5, Section VI.E, p. 55.")
                .prerequisites(List.of("First Harrowing (Along Plowing Pattern)"))
                .materials("Comb-tooth harrow implement")
                .completionCriteria("Weed sprouts uprooted and buried; soil texture highly fine and puddled.")
                .build());

        // T-1 Day: Third Harrowing and Wooden Plank Leveling
        LocalDate d1 = targetTransplantingDate.minusDays(1);
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("Final Harrowing and Wooden Plank Field Leveling")
                .category("LAND_PREPARATION")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(d1)
                .plannedEndDate(d1)
                .instructions("Perform final harrowing pass. Level the field thoroughly using a leveler or wooden plank drawn across the flooded paddy.")
                .agronomicReason("A precisely leveled field prevents high spots (weed emergence) and low depressions (drowning of young seedlings & snail congregation). Ensures uniform water depth and fertilizer distribution.")
                .citation("PhilRice Training Manual (2003), Module 5, Section VI.E-F, p. 55; Module 16: Golden Apple Snail Management, p. 168.")
                .prerequisites(List.of("Second Harrowing (Crosswise Pattern)"))
                .materials("Wooden plank leveler or power tiller leveling attachment")
                .completionCriteria("Water depth is completely uniform across the entire field without exposed soil humps.")
                .build());

        // T-0 Day: Transplanting
        tasks.add(FarmingTaskSchedule.builder()
                .taskName("Transplanting Seedlings to Mainfield")
                .category("CROP_ESTABLISHMENT")
                .actionTier("FARMER_EXECUTABLE")
                .plannedStartDate(targetTransplantingDate)
                .plannedEndDate(targetTransplantingDate)
                .instructions("Pull seedlings carefully. Transplant 2-3 seedlings per hill at 1.5-3 cm depth. Space hills 20 cm x 20 cm in straight rows using markers. Replant missing hills within 5-7 days.")
                .agronomicReason("Straight row spacing allows mechanical weeding and optimum sunlight penetration. Proper shallow planting avoids delayed root anchorage.")
                .citation("PhilRice Training Manual (2003), Module 6, Section X-XVI, pp. 63-65.")
                .prerequisites(List.of("Final Harrowing and Wooden Plank Field Leveling"))
                .materials("Seedling bundles, straight-row planting guide or mechanical marker")
                .completionCriteria("Hills planted uniformly at 20x20 cm spacing; water kept shallow (1-2 cm).")
                .build());

        return tasks;
    }

    @Data
    @Builder
    public static class GrowthStageGuideline {
        private String stageCode;
        private String stageName;
        private int minDaysAfterTransplanting;
        private int maxDaysAfterTransplanting;
        private double minWaterDepthCm;
        private double maxWaterDepthCm;
        private String waterManagementStrategy;
        private String nutrientAction;
        private String pestDiseaseAction;
        private boolean isAwdPermitted;
        private String citation;
    }

    /**
     * Complete PhilRice growth-stage-specific management matrix (Modules 3, 7, 8, 10, 12, 14, 16, 19).
     */
    public static GrowthStageGuideline getGrowthStageGuideline(int daysAfterTransplanting) {
        if (daysAfterTransplanting <= 7) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_1B")
                    .stageName("Transplanting Recovery & Root Anchorage")
                    .minDaysAfterTransplanting(0)
                    .maxDaysAfterTransplanting(7)
                    .minWaterDepthCm(1.0)
                    .maxWaterDepthCm(2.0)
                    .waterManagementStrategy("Maintain shallow 1-2 cm depth or soil saturation. Do not flood deeply to prevent drowning young seedlings and restricting snail mobility.")
                    .nutrientAction("Recheck basal fertilizer incorporation (100% P, 50% K). Apply 10-25 kg/ha Zinc Sulfate within 10-14 DAT.")
                    .pestDiseaseAction("Golden Apple Snail (Kuhol) critical window: Maintain shallow water, screen canal inlets, deploy bamboo stakes for egg collection.")
                    .isAwdPermitted(false)
                    .citation("PhilRice Training Manual (2003), Module 7, p. 71; Module 16, p. 168.")
                    .build();
        } else if (daysAfterTransplanting <= 35) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_2")
                    .stageName("Active Tillering & Early Vegetative Phase")
                    .minDaysAfterTransplanting(8)
                    .maxDaysAfterTransplanting(35)
                    .minWaterDepthCm(2.0)
                    .maxWaterDepthCm(3.0)
                    .waterManagementStrategy("Maintain 2-3 cm shallow water depth. Alternate Wetting and Drying (AWD) safe drying down to -15 cm permitted. Aeration stimulates deep root anchorage.")
                    .nutrientAction("Evaluate leaf color at 21-28 DAT with Leaf Color Chart (LCC). Topdress Nitrogen (Urea/Ammon) if LCC < 4. Max 40 kg N/ha.")
                    .pestDiseaseAction("Weed management: Rotary weeding along rows at 15-20 DAT. Monitor whorl maggot and vegetative deadhearts (stem borer).")
                    .isAwdPermitted(true)
                    .citation("PhilRice Training Manual (2003), Module 3, p. 42; Module 7, p. 71; Module 8, p. 83.")
                    .build();
        } else if (daysAfterTransplanting <= 50) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_3")
                    .stageName("Stem Elongation & Maximum Tillering")
                    .minDaysAfterTransplanting(36)
                    .maxDaysAfterTransplanting(50)
                    .minWaterDepthCm(3.0)
                    .maxWaterDepthCm(5.0)
                    .waterManagementStrategy("Gradually increase ponded water to 3-5 cm. Continue AWD cycling between 5 cm reflood and -15 cm perched table drawdown.")
                    .nutrientAction("Monitor tiller count and LCC. Avoid late heavy nitrogen that causes vegetative lodging.")
                    .pestDiseaseAction("Monitor sheath blight and leaf blast lesions. Rogue diseased hills.")
                    .isAwdPermitted(true)
                    .citation("PhilRice Training Manual (2003), Module 3, p. 43; Module 7, p. 72.")
                    .build();
        } else if (daysAfterTransplanting <= 65) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_4")
                    .stageName("Panicle Initiation to Booting")
                    .minDaysAfterTransplanting(51)
                    .maxDaysAfterTransplanting(65)
                    .minWaterDepthCm(3.0)
                    .maxWaterDepthCm(5.0)
                    .waterManagementStrategy("CRITICAL STAGE: SUSPEND AWD DRYING. Keep field continuously submerged under 3-5 cm ponded water. Plant is highly vulnerable to moisture stress.")
                    .nutrientAction("Apply remaining 50% Potassium (Muriate of Potash) and 3rd Nitrogen split (Early Panicle Initiation topdressing).")
                    .pestDiseaseAction("Check for stem borer egg masses; protect flag leaf from bacterial leaf blight.")
                    .isAwdPermitted(false)
                    .citation("PhilRice Training Manual (2003), Module 3, p. 43; Module 7, p. 75; Module 8, p. 83.")
                    .build();
        } else if (daysAfterTransplanting <= 80) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_5_6")
                    .stageName("Heading and Flowering")
                    .minDaysAfterTransplanting(66)
                    .maxDaysAfterTransplanting(80)
                    .minWaterDepthCm(3.0)
                    .maxWaterDepthCm(5.0)
                    .waterManagementStrategy("CRITICAL SENSITIVE STAGE: ZERO DRYING ALLOWED. Maintain 3-5 cm standing water continuously. Moisture stress causes unfertilized blank grains/whiteheads.")
                    .nutrientAction("No further nitrogen unless flag leaves are severely pale (LCC < 3).")
                    .pestDiseaseAction("Monitor rice bug (Atangya) during milk stage. Avoid pesticide spraying during peak morning anthesis (8-11 AM).")
                    .isAwdPermitted(false)
                    .citation("PhilRice Training Manual (2003), Module 3, p. 44; Module 7, p. 75; Module 10, p. 108.")
                    .build();
        } else if (daysAfterTransplanting <= 95) {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_7_8")
                    .stageName("Milk to Dough Grain Ripening")
                    .minDaysAfterTransplanting(81)
                    .maxDaysAfterTransplanting(95)
                    .minWaterDepthCm(2.0)
                    .maxWaterDepthCm(3.0)
                    .waterManagementStrategy("Maintain shallow 2-3 cm depth or saturated soil. Grains actively accumulating starch.")
                    .nutrientAction("No fertilizer application permitted.")
                    .pestDiseaseAction("Monitor rat activity and bird feeding; repair perimeter baiting traps.")
                    .isAwdPermitted(true)
                    .citation("PhilRice Training Manual (2003), Module 3, p. 45; Module 7, p. 71.")
                    .build();
        } else {
            return GrowthStageGuideline.builder()
                    .stageCode("STAGE_9")
                    .stageName("Mature Grain & Terminal Drainage")
                    .minDaysAfterTransplanting(96)
                    .maxDaysAfterTransplanting(115)
                    .minWaterDepthCm(0.0)
                    .maxWaterDepthCm(0.0)
                    .waterManagementStrategy("TERMINAL DRAINAGE: Completely cut off irrigation and drain paddies 7-10 days before expected harvest. Induces uniform ripening and solidifies field floor for mechanical harvesting.")
                    .nutrientAction("Harvest readiness: 80-85% grains straw-colored/yellow; grains at base at hard dough; kernels clear & translucent.")
                    .pestDiseaseAction("Harvest timely at 28-34 days after heading (DS) or 30-42 days after heading (WS). Thresh immediately within 24h to avoid grain discoloration. Dry to 14% moisture.")
                    .isAwdPermitted(false)
                    .citation("PhilRice Training Manual (2003), Module 7, p. 71; Module 19, pp. 188-196.")
                    .build();
        }
    }

    /**
     * Evaluates planting readiness based on PhilRice agronomic criteria,
     * available prep time, water availability, and telemetry.
     */
    public static ReadinessAssessment evaluatePlantingReadiness(
            LocalDate requestedDate,
            String method,
            double fieldArea,
            boolean hasWaterSource,
            Double currentSoilMoisturePercent,
            Double currentWaterLevelCm
    ) {
        LocalDate today = LocalDate.now();
        List<String> reasons = new ArrayList<>();
        List<String> requiredActions = new ArrayList<>();
        List<String> uncertainties = new ArrayList<>();

        if (requestedDate == null) {
            return ReadinessAssessment.builder()
                    .status(ReadinessAssessment.ReadinessStatus.INSUFFICIENT_DATA)
                    .summary("Unable to assess planting readiness because the target planting date is not specified.")
                    .reasons(List.of("No planting date provided."))
                    .requiredActionsBeforeProceeding(List.of("Specify an intended start or transplanting date."))
                    .uncertainties(List.of("Timeline cannot be evaluated without a target date."))
                    .citation("PhilRice Training Manual (2003), Module 5, p. 56.")
                    .build();
        }

        long daysUntilPlanting = java.time.temporal.ChronoUnit.DAYS.between(today, requestedDate);

        // PhilRice strict land preparation timeline check (21 days)
        if (daysUntilPlanting < 14) {
            reasons.add(String.format("Requested planting date is only %d days away. PhilRice standards strictly require a 21-day land preparation period prior to transplanting.", daysUntilPlanting));
            requiredActions.add("Adjust target transplanting date forward by at least " + (21 - daysUntilPlanting) + " days to allow proper organic matter decomposition and weed eradication.");
            uncertainties.add("Planting within <14 days poses severe risk of seedling rot from toxic gases generated by rapidly decomposing organic straw.");

            return ReadinessAssessment.builder()
                    .status(ReadinessAssessment.ReadinessStatus.NOT_RECOMMENDED)
                    .summary("Planting on the requested date is NOT RECOMMENDED due to insufficient land preparation time.")
                    .reasons(reasons)
                    .requiredActionsBeforeProceeding(requiredActions)
                    .uncertainties(uncertainties)
                    .citation("PhilRice Training Manual (2003), Module 5: Land Preparation, Section XII, p. 56.")
                    .build();
        }

        if (!hasWaterSource) {
            reasons.add("No dependable irrigation source or scheduled canal inflow confirmed for wetland puddling.");
            requiredActions.add("Verify NIA irrigation canal schedule or confirm pump availability.");
            return ReadinessAssessment.builder()
                    .status(ReadinessAssessment.ReadinessStatus.CONDITIONALLY_SUITABLE)
                    .summary("Conditionally suitable: Planting timeline is viable, but water source must be verified before flooding paddies.")
                    .reasons(reasons)
                    .requiredActionsBeforeProceeding(requiredActions)
                    .uncertainties(List.of("Canal delivery schedule unconfirmed."))
                    .citation("PhilRice Training Manual (2003), Module 7: Irrigation and Water Management, p. 69.")
                    .build();
        }

        if (daysUntilPlanting >= 21) {
            reasons.add(String.format("Timeline of %d days satisfies PhilRice's 21-day wetland preparation requirement.", daysUntilPlanting));
            reasons.add("Adequate window available for 24h seed soaking, 24-36h incubation, and 5-7 day soil submergence.");
            if (currentSoilMoisturePercent != null) {
                reasons.add(String.format("Current telemetry shows soil moisture at %.1f%%.", currentSoilMoisturePercent));
            }
            return ReadinessAssessment.builder()
                    .status(ReadinessAssessment.ReadinessStatus.SUITABLE)
                    .summary("SUITABLE: The field timeline and conditions comply with PhilRice agricultural guidelines.")
                    .reasons(reasons)
                    .requiredActionsBeforeProceeding(List.of("Initiate dike repairs and primary plowing at Day T-21."))
                    .uncertainties(List.of("Monitor local PAGASA rainfall alerts during seedbed emergence."))
                    .citation("PhilRice Training Manual (2003), Module 5 & 6, pp. 53-68.")
                    .build();
        } else {
            // Between 14 and 20 days: Conditionally suitable if fast dapog or accelerated tillage used
            reasons.add(String.format("Available preparation window is %d days (slightly under the ideal 21 days).", daysUntilPlanting));
            requiredActions.add("Ensure primary plowing starts immediately today to maximize residue breakdown.");
            return ReadinessAssessment.builder()
                    .status(ReadinessAssessment.ReadinessStatus.CONDITIONALLY_SUITABLE)
                    .summary("CONDITIONALLY SUITABLE: Viable if primary plowing and dike repair begin immediately.")
                    .reasons(reasons)
                    .requiredActionsBeforeProceeding(requiredActions)
                    .uncertainties(List.of("Slightly compressed decomposition window."))
                    .citation("PhilRice Training Manual (2003), Module 5, Section XII, p. 56.")
                    .build();
        }
    }
}

