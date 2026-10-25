package com.aquaflow.backend.domain.agri;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhilRiceRiceProductionEngineTest {

    @Test
    void testWetbedSeedingCalculationsForOneHectare() {
        PhilRiceRiceProductionEngine.SeedingPlan plan =
                PhilRiceRiceProductionEngine.calculateSeedingPlan("WETBED", 1.0);

        assertEquals("WETBED", plan.getMethod());
        assertEquals(20.0, plan.getMinSeedKg());
        assertEquals(40.0, plan.getMaxSeedKg());
        assertEquals(30.0, plan.getRecommendedSeedKg());
        assertEquals(400.0, plan.getSeedbedAreaM2());
        assertEquals(24, plan.getSoakingHours());
        assertEquals(24, plan.getMinIncubationHours());
        assertEquals(36, plan.getMaxIncubationHours());
        assertEquals(20, plan.getMinSeedlingAgeDays());
        assertEquals(25, plan.getMaxSeedlingAgeDays());
        assertTrue(plan.getCitation().contains("PhilRice Training Manual (2003)"));
    }

    @Test
    void testDapogSeedingCalculationsForTwoPointFiveHectares() {
        double area = 2.5;
        PhilRiceRiceProductionEngine.SeedingPlan plan =
                PhilRiceRiceProductionEngine.calculateSeedingPlan("DAPOG", area);

        assertEquals("DAPOG", plan.getMethod());
        assertEquals(45.0 * 2.5, plan.getMinSeedKg(), 0.01);
        assertEquals(65.0 * 2.5, plan.getMaxSeedKg(), 0.01);
        assertEquals(55.0 * 2.5, plan.getRecommendedSeedKg(), 0.01);
        assertEquals(60.0 * 2.5, plan.getSeedbedAreaM2(), 0.01); // 150 m2 for 2.5 ha
        assertEquals(24, plan.getSoakingHours());
        assertEquals(36, plan.getMinIncubationHours());
        assertEquals(48, plan.getMaxIncubationHours());
        assertEquals(9, plan.getMinSeedlingAgeDays());
        assertEquals(14, plan.getMaxSeedlingAgeDays());
    }

    @Test
    void test21DayScheduleGeneration() {
        LocalDate transplantDate = LocalDate.of(2026, 11, 20);
        List<PhilRiceRiceProductionEngine.FarmingTaskSchedule> tasks =
                PhilRiceRiceProductionEngine.generateFarmingSchedule(transplantDate, "WETBED", 1.0);

        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // Verify primary plowing is exactly T-21 days (Oct 30)
        PhilRiceRiceProductionEngine.FarmingTaskSchedule plowing = tasks.get(0);
        assertTrue(plowing.getTaskName().contains("Primary Tillage"));
        assertEquals(LocalDate.of(2026, 10, 30), plowing.getPlannedStartDate());
        assertEquals("FARMER_EXECUTABLE", plowing.getActionTier());
        assertTrue(plowing.getCitation().contains("Module 5"));

        // Verify field leveling is T-1 day (Nov 19)
        PhilRiceRiceProductionEngine.FarmingTaskSchedule leveling = tasks.stream()
                .filter(t -> t.getTaskName().contains("Leveling"))
                .findFirst()
                .orElse(null);
        assertNotNull(leveling);
        assertEquals(LocalDate.of(2026, 11, 19), leveling.getPlannedStartDate());
    }

    @Test
    void testReadinessEvaluationRejectsTooShortTimeline() {
        // Only 5 days away: Must be NOT_RECOMMENDED per PhilRice 21-day rule
        LocalDate tooSoon = LocalDate.now().plusDays(5);
        PhilRiceRiceProductionEngine.ReadinessAssessment eval =
                PhilRiceRiceProductionEngine.evaluatePlantingReadiness(tooSoon, "WETBED", 1.0, true, 60.0, 15.0);

        assertEquals(PhilRiceRiceProductionEngine.ReadinessAssessment.ReadinessStatus.NOT_RECOMMENDED, eval.getStatus());
        assertTrue(eval.getReasons().get(0).contains("PhilRice standards strictly require a 21-day land preparation period"));
    }

    @Test
    void testReadinessEvaluationSuitableWhen25DaysAvailable() {
        LocalDate idealDate = LocalDate.now().plusDays(25);
        PhilRiceRiceProductionEngine.ReadinessAssessment eval =
                PhilRiceRiceProductionEngine.evaluatePlantingReadiness(idealDate, "WETBED", 1.0, true, 60.0, 15.0);

        assertEquals(PhilRiceRiceProductionEngine.ReadinessAssessment.ReadinessStatus.SUITABLE, eval.getStatus());
        assertTrue(eval.getReasons().get(0).contains("21-day wetland preparation requirement"));
    }

    @Test
    void testGrowthStageGuidelineFloweringRequiresContinuousSubmergence() {
        // DAT 70 is in Heading and Flowering stage
        PhilRiceRiceProductionEngine.GrowthStageGuideline guideline =
                PhilRiceRiceProductionEngine.getGrowthStageGuideline(70);

        assertEquals("STAGE_5_6", guideline.getStageCode());
        assertFalse(guideline.isAwdPermitted());
        assertEquals(3.0, guideline.getMinWaterDepthCm());
        assertEquals(5.0, guideline.getMaxWaterDepthCm());
        assertTrue(guideline.getWaterManagementStrategy().contains("CRITICAL SENSITIVE STAGE"));
    }

    @Test
    void testGrowthStageGuidelineTerminalDrainageBeforeHarvest() {
        // DAT 100 is in mature grain stage
        PhilRiceRiceProductionEngine.GrowthStageGuideline guideline =
                PhilRiceRiceProductionEngine.getGrowthStageGuideline(100);

        assertEquals("STAGE_9", guideline.getStageCode());
        assertEquals(0.0, guideline.getMinWaterDepthCm());
        assertEquals(0.0, guideline.getMaxWaterDepthCm());
        assertTrue(guideline.getWaterManagementStrategy().contains("TERMINAL DRAINAGE"));
        assertTrue(guideline.getNutrientAction().contains("80-85% grains straw-colored"));
    }
}

