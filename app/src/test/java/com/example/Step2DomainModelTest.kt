package com.example

import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.BusinessArea
import com.example.model.DefectMaster
import com.example.model.Department
import com.example.model.ManufacturingFlow
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.RebuffingRule
import com.example.model.RejectionQuantity
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.model.StockQuantity
import com.example.model.StockRecord
import com.example.model.validation.DomainValidator
import com.example.repository.MasterDataRepository
import com.example.repository.PlanningRepository
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository
import com.example.repository.memory.InMemoryMasterDataRepository
import com.example.repository.memory.InMemoryPlanningRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Step 2 Verification Test Suite: Master Data, Domain Models, and Repository Contracts.
 */
class Step2DomainModelTest {

    private lateinit var masterDataRepo: MasterDataRepository

    @Before
    fun setUp() {
        masterDataRepo = InMemoryMasterDataRepository()
    }

    // 1. Exact Model Master
    @Test
    fun testExactModelMaster() {
        val models = masterDataRepo.getModels()
        assertEquals(7, models.size)

        val expectedCodes = listOf("U86", "U180", "U244", "MAXR", "N282-DISC", "DRUM", "N360")
        assertEquals(expectedCodes, models.map { it.code })

        assertEquals("U86", ManufacturingModel.U86.displayName)
        assertEquals("U180", ManufacturingModel.U180.displayName)
        assertEquals("U244", ManufacturingModel.U244.displayName)
        assertEquals("MAXR", ManufacturingModel.MAXR.displayName)
        assertEquals("N282-DISC", ManufacturingModel.N282_DISC.displayName)
        assertEquals("DRUM", ManufacturingModel.DRUM.displayName)
        assertEquals("N360", ManufacturingModel.N360.displayName)
    }

    // 2. Exact Department Master & Sequence
    @Test
    fun testExactDepartmentMasterAndSequence() {
        val departments = masterDataRepo.getDepartments()
        assertEquals(7, departments.size)

        val expectedSequence = listOf(
            "CASTING",
            "POST CASTING",
            "M/C",
            "BUFFING",
            "FINAL",
            "KAMAL OK",
            "GIL DELIVERY"
        )
        assertEquals(expectedSequence, departments.map { it.displayName })

        // Check sequence numbers 1 to 7
        for (i in 0 until 7) {
            assertEquals(i + 1, departments[i].sequenceNumber)
        }
    }

    // 3. ALL Cannot Be Stored
    @Test
    fun testAllCannotBeStored() {
        // Domain validator must strictly reject "ALL" for models
        try {
            DomainValidator.assertModelCanBeStored("ALL")
            fail("DomainValidator.assertModelCanBeStored('ALL') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }

        // Domain validator must strictly reject "ALL" for departments
        try {
            DomainValidator.assertDepartmentCanBeStored("ALL")
            fail("DomainValidator.assertDepartmentCanBeStored('ALL') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }

        // ManufacturingModel.fromCode("ALL") must throw
        try {
            ManufacturingModel.fromCode("ALL")
            fail("ManufacturingModel.fromCode('ALL') must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }

        // Department.fromDisplayName("ALL") must throw
        try {
            Department.fromDisplayName("ALL")
            fail("Department.fromDisplayName('ALL') must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }
    }

    // 4. Model Applicability (U180 and MAXR GIL-only)
    @Test
    fun testModelApplicabilityU180AndMaxr() {
        // U180
        assertTrue(masterDataRepo.isModelApplicable(ManufacturingModel.U180, Department.GIL_DELIVERY))
        val u180Depts = masterDataRepo.getApplicableDepartments(ManufacturingModel.U180)
        assertEquals(listOf(Department.GIL_DELIVERY), u180Depts)

        // MAXR
        assertTrue(masterDataRepo.isModelApplicable(ManufacturingModel.MAXR, Department.GIL_DELIVERY))
        val maxrDepts = masterDataRepo.getApplicableDepartments(ManufacturingModel.MAXR)
        assertEquals(listOf(Department.GIL_DELIVERY), maxrDepts)

        // Other models have all 7 departments applicable
        val otherModels = listOf(
            ManufacturingModel.U86,
            ManufacturingModel.U244,
            ManufacturingModel.N282_DISC,
            ManufacturingModel.DRUM,
            ManufacturingModel.N360
        )
        for (model in otherModels) {
            assertEquals(7, masterDataRepo.getApplicableDepartments(model).size)
            assertTrue(masterDataRepo.isModelApplicable(model, Department.CASTING))
            assertTrue(masterDataRepo.isModelApplicable(model, Department.GIL_DELIVERY))
        }

        // Invariant enforcement throws when recording production for U180 in CASTING
        try {
            ProductionRecord(
                id = "PRD-INV",
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U180,
                department = Department.CASTING,
                quantity = ProductionQuantity(10)
            )
            fail("Expected exception when creating ProductionRecord for U180 in CASTING")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY only", ignoreCase = true) == true)
        }
    }

    // 5. Rejection Source, Area, and Side Validation
    @Test
    fun testRejectionMasterValidation() {
        val sources = masterDataRepo.getRejectionSources()
        assertEquals(3, sources.size)
        assertEquals(listOf("KAMAL", "MRN", "DSPL"), sources.map { it.code })

        val areas = masterDataRepo.getBusinessAreas()
        assertEquals(3, areas.size)
        assertEquals(listOf("KAMAL", "GABRIEL", "DSPL"), areas.map { it.code })

        val sides = masterDataRepo.getRejectionSides()
        assertEquals(2, sides.size)
        assertEquals(listOf("LH", "RH"), sides.map { it.code })
    }

    // 6. BOTH Rejection Is Strictly Invalid
    @Test
    fun testBothRejectionInvalid() {
        try {
            DomainValidator.validateRejectionSide("BOTH")
            fail("DomainValidator.validateRejectionSide('BOTH') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid", ignoreCase = true) == true)
        }

        try {
            RejectionSide.parse("BOTH")
            fail("RejectionSide.parse('BOTH') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid", ignoreCase = true) == true)
        }

        assertNull(RejectionSide.parseOrNull("BOTH"))
    }

    // 7. Production Has No LH/RH Concept
    @Test
    fun testProductionHasNoLhRh() {
        val qty = ProductionQuantity(100)
        assertEquals(100, qty.value)

        val prodRecord = ProductionRecord(
            id = "PRD-VALID",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = qty
        )
        // Assert reflection on ProductionRecord fields contains NO side
        val fieldNames = ProductionRecord::class.java.declaredFields.map { it.name }
        assertFalse("ProductionRecord must not have a 'side' field", fieldNames.contains("side"))
        assertFalse("ProductionQuantity must not have a 'side' field", ProductionQuantity::class.java.declaredFields.any { it.name == "side" })
    }

    // 8. Rebuffing Rule: KAMAL Rejection ONLY
    @Test
    fun testRebuffingRule() {
        assertTrue(masterDataRepo.validateRebuffingEligibility(RejectionSource.KAMAL))
        assertFalse(masterDataRepo.validateRebuffingEligibility(RejectionSource.MRN))
        assertFalse(masterDataRepo.validateRebuffingEligibility(RejectionSource.DSPL))

        // Valid KAMAL rebuffing
        val kamalRejection = RejectionRecord(
            id = "REJ-KAMAL",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.BUFFING,
            defectName = "Rough Buffing",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 5,
            isRebuffed = true
        )
        assertTrue(kamalRejection.isRebuffed)

        // Invalid MRN rebuffing
        try {
            RejectionRecord(
                id = "REJ-MRN-BAD",
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.BUFFING,
                defectName = "Rough Buffing",
                source = RejectionSource.MRN,
                businessArea = BusinessArea.GABRIEL,
                side = RejectionSide.LH,
                quantity = 5,
                isRebuffed = true
            )
            fail("Expected exception when MRN rejection is marked as rebuffed")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("KAMAL", ignoreCase = true) == true)
        }
    }

    // 9. Canonical Manufacturing Flow Sequence & Navigation
    @Test
    fun testManufacturingFlowSequence() {
        val flow = masterDataRepo.getManufacturingFlow()
        assertEquals(7, flow.size)
        assertEquals(Department.CASTING, flow[0])
        assertEquals(Department.POST_CASTING, flow[1])
        assertEquals(Department.MACHINING, flow[2])
        assertEquals(Department.BUFFING, flow[3])
        assertEquals(Department.FINAL, flow[4])
        assertEquals(Department.KAMAL_OK, flow[5])
        assertEquals(Department.GIL_DELIVERY, flow[6])

        // Step transitions
        assertTrue(ManufacturingFlow.isSequentialTransition(Department.CASTING, Department.POST_CASTING))
        assertTrue(ManufacturingFlow.isSequentialTransition(Department.POST_CASTING, Department.MACHINING))
        assertTrue(ManufacturingFlow.isSequentialTransition(Department.FINAL, Department.KAMAL_OK))
        assertTrue(ManufacturingFlow.isSequentialTransition(Department.KAMAL_OK, Department.GIL_DELIVERY))
        assertFalse(ManufacturingFlow.isSequentialTransition(Department.CASTING, Department.FINAL))

        assertEquals(Department.POST_CASTING, ManufacturingFlow.getNextStep(Department.CASTING))
        assertEquals(Department.CASTING, ManufacturingFlow.getPreviousStep(Department.POST_CASTING))
        assertNull(ManufacturingFlow.getNextStep(Department.GIL_DELIVERY))
        assertNull(ManufacturingFlow.getPreviousStep(Department.CASTING))
    }

    // 10. Blank & Invalid Master Data Validation
    @Test
    fun testBlankAndInvalidMasterDataValidation() {
        // Blank model
        try {
            DomainValidator.assertModelCanBeStored("")
            fail("Blank model must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Blank department
        try {
            DomainValidator.assertDepartmentCanBeStored("   ")
            fail("Blank department must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Blank defect
        try {
            DefectMaster.validateDefectName("")
            fail("Blank defect name must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Negative quantity
        try {
            DomainValidator.validateNonNegativeQuantity(-1, "TestQty")
            fail("Negative quantity must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Normalized defect name
        assertEquals("BLOWHOLE", DefectMaster.normalizeDefectName("blowhole"))
        assertEquals("CRACK", DefectMaster.normalizeDefectName("Crack"))
    }

    // 11. Stock Record Invariants (Manual Entry, No Auto-Derivation)
    @Test
    fun testStockRecordInvariants() {
        val stockRecord = StockRecord(
            id = "STK-100",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            openingQuantity = 400,
            closingQuantity = 380
        )
        assertEquals(400, stockRecord.openingQuantity)
        assertEquals(380, stockRecord.closingQuantity)

        // Negative opening or closing must fail
        try {
            StockRecord(
                id = "STK-BAD",
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                openingQuantity = -10,
                closingQuantity = 50
            )
            fail("Negative opening stock must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            StockRecord(
                id = "STK-BAD-2",
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                openingQuantity = 10,
                closingQuantity = -5
            )
            fail("Negative closing stock must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 12. Repository Contracts & Implementation Consistency
    @Test
    fun testRepositoryContractConsistency() = runBlocking {
        val prodRepo: ProductionRepository = InMemoryProductionRepository()
        val rejRepo: RejectionRepository = InMemoryRejectionRepository()
        val stkRepo: StockRepository = InMemoryStockRepository()
        val plnRepo: PlanningRepository = InMemoryPlanningRepository()

        val p = ProductionRecord(
            id = "P1",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = ProductionQuantity(250)
        )
        prodRepo.insert(p)
        assertEquals(p, prodRepo.getById("P1"))

        val r = RejectionRecord(
            id = "R1",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            defectName = "Blowhole",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 5
        )
        rejRepo.insert(r)
        assertEquals(r, rejRepo.getById("R1"))

        val s = StockRecord(
            id = "S1",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            openingQuantity = 100,
            closingQuantity = 120
        )
        stkRepo.insert(s)
        assertEquals(s, stkRepo.getById("S1"))

        assertTrue(prodRepo.deleteById("P1"))
        assertNull(prodRepo.getById("P1"))
    }
}
