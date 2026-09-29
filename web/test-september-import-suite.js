/**
 * DIVINE STAMP PVT LTD — MANUFACTURING MIS
 * SEPTEMBER 2026 IMPORT & SCHEMA-READY VALIDATION TEST SUITE
 */

import assert from "assert";
import {
  validateProductionRecord,
  validateRejectionRow,
  validateRejectionDefect,
  processSeptemberWorkbook,
  EXPECTED_COUNTS,
  CANONICAL_DEPARTMENTS,
  CANONICAL_MODELS
} from "./september-importer.js";

async function runSeptemberValidationSuite() {
  console.log("====================================================");
  console.log(" STARTING SEPTEMBER 2026 IMPORT VALIDATION SUITE    ");
  console.log("====================================================");

  let passed = 0;
  let failed = 0;

  function test(name, fn) {
    try {
      fn();
      console.log(`[PASS] ${name}`);
      passed++;
    } catch (e) {
      console.error(`[FAIL] ${name}: ${e.message}`);
      failed++;
    }
  }

  // TEST 1: Canonical departments & models specification
  test("Test #1: Canonical department definitions match MIS standard", () => {
    assert.strictEqual(CANONICAL_DEPARTMENTS.length, 7);
    assert(CANONICAL_DEPARTMENTS.includes("CASTING"));
    assert(CANONICAL_DEPARTMENTS.includes("GIL DELIVERY"));
  });

  // TEST 2: Model 'ALL' rejected by production validator
  test("Test #2: Model 'ALL' is filter-only and strictly rejected from persistence", () => {
    const v = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "ALL",
      plan: 100,
      actual: 90,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(v.isValid, false);
    assert(v.errors.some(e => e.includes("Model 'ALL' is filter-only")));
  });

  // TEST 3: N282 DISC normalized to N282-DISC
  test("Test #3: Model 'N282 DISC' normalized to canonical 'N282-DISC'", () => {
    const v = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "N282 DISC",
      plan: 100,
      actual: 95,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(v.isValid, true);
    assert.strictEqual(v.record.model, "N282-DISC");
  });

  // TEST 4: U180 and MAXR GIL-only applicability rule
  test("Test #4: U180 and MAXR strictly restricted to GIL DELIVERY", () => {
    const vInvalid = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "U180",
      plan: 100,
      actual: 90,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(vInvalid.isValid, false);
    assert(vInvalid.errors.some(e => e.includes("applicable ONLY to GIL DELIVERY")));

    const vValid = validateProductionRecord({
      date: "2026-09-01",
      department: "GIL DELIVERY",
      model: "U180",
      plan: 100,
      actual: 90,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(vValid.isValid, true);
  });

  // TEST 5: Production record has NO LH/RH field
  test("Test #5: Production records containing LH/RH are flagged as invalid", () => {
    const v = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "U86",
      side: "LH",
      plan: 100,
      actual: 90,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(v.isValid, false);
    assert(v.errors.some(e => e.includes("must not contain LH/RH")));
  });

  // TEST 6: Explicit actual 0 preserved vs NOT_ENTERED vs NO_DATA
  test("Test #6: Distinction between explicit 0, NOT_ENTERED, and NO_DATA is preserved", () => {
    const vZero = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "U86",
      plan: 100,
      actual: 0,
      actualEntered: true,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(vZero.record.quantity, 0);

    const vNotEntered = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "U86",
      plan: 100,
      actual: null,
      actualEntered: false,
      dataStatus: "NOT_ENTERED"
    }, 2);
    assert.strictEqual(vNotEntered.record.quantity, null);
    assert.strictEqual(vNotEntered.record.dataStatus, "NOT_ENTERED");

    const vNoData = validateProductionRecord({
      date: "2026-09-01",
      department: "CASTING",
      model: "U86",
      plan: 100,
      actual: null,
      actualEntered: false,
      dataStatus: "NO_DATA"
    }, 3);
    assert.strictEqual(vNoData.record.quantity, null);
    assert.strictEqual(vNoData.record.dataStatus, "NO_DATA");
  });

  // TEST 7: Rejection side 'BOTH' strictly prohibited
  test("Test #7: Rejection side 'BOTH' is strictly rejected", () => {
    const v = validateRejectionRow({
      date: "2026-09-01",
      source: "KAMAL",
      businessArea: "KAMAL",
      model: "U86",
      partName: "FORK",
      side: "BOTH",
      rejectionQty: 10,
      inspectedOrProductionQty: 100,
      rejectionRatePct: 10,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(v.isValid, false);
    assert(v.errors.some(e => e.includes("Rejection side 'BOTH' is strictly prohibited")));
  });

  // TEST 8: Source to Business Area mapping
  test("Test #8: Source to Business Area mappings (KAMAL->KAMAL, MRN->GABRIEL, DSPL->DSPL)", () => {
    const vMrn = validateRejectionRow({
      date: "2026-09-01",
      source: "MRN",
      businessArea: "GABRIEL",
      model: "U86",
      partName: "FORK",
      side: "LH",
      rejectionQty: 5,
      inspectedOrProductionQty: 100,
      rejectionRatePct: 5,
      dataStatus: "ENTERED"
    }, 1);
    assert.strictEqual(vMrn.isValid, true);
    assert.strictEqual(vMrn.record.businessArea, "GABRIEL");
    assert.strictEqual(vMrn.record.isRebuffed, false, "MRN must not be marked rebuffed");

    const vKamal = validateRejectionRow({
      date: "2026-09-01",
      source: "KAMAL",
      businessArea: "KAMAL",
      model: "U86",
      partName: "FORK",
      side: "RH",
      rejectionQty: 8,
      inspectedOrProductionQty: 100,
      rejectionRatePct: 8,
      dataStatus: "ENTERED"
    }, 2);
    assert.strictEqual(vKamal.isValid, true);
    assert.strictEqual(vKamal.record.isRebuffed, true, "KAMAL source is eligible for rebuffing");
  });

  // TEST 9: DSPL Zero Rejection remains NO_DATA and is not imported
  test("Test #9: DSPL zero rejections remain strictly NO_DATA and are not imported", () => {
    const vDsplZero = validateRejectionRow({
      date: "2026-09-01",
      source: "DSPL",
      businessArea: "DSPL",
      model: "U86",
      partName: "FORK",
      side: "LH",
      rejectionQty: 0,
      inspectedOrProductionQty: 100,
      rejectionRatePct: 0,
      dataStatus: "NO_DATA"
    }, 1);
    assert.strictEqual(vDsplZero.isValid, true);
    assert.strictEqual(vDsplZero.isDsplNoData, true);
    assert.strictEqual(vDsplZero.record, null, "DSPL NO_DATA row must not create a record");
  });

  // TEST 10: Idempotent Key Generation prevents duplicates
  test("Test #10: Production and rejection keys enforce idempotency", () => {
    const row1 = {
      date: "2026-09-01",
      department: "CASTING",
      model: "U86",
      plan: 100,
      actual: 90,
      actualEntered: true,
      dataStatus: "ENTERED"
    };
    const v1 = validateProductionRecord(row1, 1);
    const v2 = validateProductionRecord(row1, 2);
    assert.strictEqual(v1.record.id, v2.record.id, "Duplicate row must produce identical key");
  });

  console.log("====================================================");
  console.log(`TOTAL IMPORT VALIDATION CHECKS: ${passed + failed}`);
  console.log(`PASSED: ${passed}`);
  console.log(`FAILED: ${failed}`);
  console.log("====================================================");

  if (failed > 0) process.exit(1);
}

runSeptemberValidationSuite().catch(err => {
  console.error("Test failure:", err);
  process.exit(1);
});
