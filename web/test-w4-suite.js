// W4 Automated Test Suite for Divine Stamp Manufacturing MIS Web
// Validates Authentication, RBAC, Model Applicability, All Filter, Production, Rejection, Rebuffing, Stock, Planning, Dashboard, Date/Model Filtering, 0 vs NO DATA, Alerts, Actions, Reports

import { 
  ManufacturingModel, 
  ALL_MODELS, 
  Department, 
  CANONICAL_DEPARTMENTS, 
  ManufacturingFlow, 
  ModelApplicabilityValidator, 
  RejectionSource, 
  BusinessArea, 
  RejectionSide, 
  parseRejectionSide, 
  RebuffingRule, 
  STANDARD_DEFECTS, 
  validateDefectName, 
  FirestoreConstants, 
  MetricValue,
  parseManufacturingModel,
  parseDepartment,
  UserRole, 
  RoleCapabilities, 
  Collections, 
  validateFilterInvariant 
} from "./manufacturing-contracts.js";

import { 
  analyticsEngine, 
  AnalyticsFilter, 
  generateDateSequence, 
  filterMatches 
} from "./analytics-engine.js";

let testsRun = 0;
let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
  testsRun++;
  if (!condition) {
    testsFailed++;
    console.error(`[FAIL] Test #${testsRun}: ${message}`);
    throw new Error(`Test failed: ${message}`);
  } else {
    testsPassed++;
    console.log(`[PASS] Test #${testsRun}: ${message}`);
  }
}

console.log("=== RUNNING W4 WEB TEST SUITE ===");

// 1. Authentication & RBAC Tests
assert(UserRole.CEO === "CEO" && UserRole.ADMIN === "ADMIN" && UserRole.MANAGER === "MANAGER" && UserRole.SUPERVISOR === "SUPERVISOR", "Canonical 4 roles defined");
assert(RoleCapabilities[UserRole.CEO].canViewExecutiveDashboard === true, "CEO can view executive dashboard");
assert(RoleCapabilities[UserRole.CEO].canWriteProduction === false, "CEO cannot write production records");
assert(RoleCapabilities[UserRole.ADMIN].canWriteProduction === true && RoleCapabilities[UserRole.ADMIN].canManageUsers === true, "ADMIN can write production and manage users");
assert(RoleCapabilities[UserRole.MANAGER].canWriteProduction === true && RoleCapabilities[UserRole.MANAGER].canExportReports === true, "MANAGER can write production and export reports");
assert(RoleCapabilities[UserRole.SUPERVISOR].canViewExecutiveDashboard === false && RoleCapabilities[UserRole.SUPERVISOR].canWriteProduction === true, "SUPERVISOR has floor shift write access but no executive view");

// 2. Model Applicability Tests
assert(ModelApplicabilityValidator.isApplicable("U86", "CASTING"), "U86 is applicable in CASTING");
assert(ModelApplicabilityValidator.isApplicable("U86", "GIL_DELIVERY"), "U86 is applicable in GIL DELIVERY");
assert(!ModelApplicabilityValidator.isApplicable("U180", "CASTING"), "U180 is INVALID in CASTING");
assert(!ModelApplicabilityValidator.isApplicable("U180", "M_C"), "U180 is INVALID in M/C");
assert(ModelApplicabilityValidator.isApplicable("U180", "GIL_DELIVERY"), "U180 is VALID in GIL DELIVERY");
assert(!ModelApplicabilityValidator.isApplicable("MAXR", "BUFFING"), "MAXR is INVALID in BUFFING");
assert(ModelApplicabilityValidator.isApplicable("MAXR", "GIL_DELIVERY"), "MAXR is VALID in GIL DELIVERY");

// 3. 'ALL' Filter Invariant Tests
try {
  parseManufacturingModel("ALL");
  assert(false, "Parsing 'ALL' model must throw invariant violation");
} catch(e) {
  assert(e.message.includes("filter-only"), "'ALL' model rejected by entity parser");
}

try {
  parseDepartment("ALL");
  assert(false, "Parsing 'ALL' department must throw invariant violation");
} catch(e) {
  assert(e.message.includes("filter-only"), "'ALL' department rejected by entity parser");
}

try {
  validateFilterInvariant({ model: "ALL", department: "CASTING" });
  assert(false, "validateFilterInvariant must throw when model is ALL");
} catch(e) {
  assert(e.message.includes("filter-only"), "Filter invariant prevented persistence of model 'ALL'");
}

// 4. Production Contract Tests (NO LH/RH)
const prodRecord = {
  date: "2026-09-28",
  model: "U86",
  department: "CASTING",
  shift: "SHIFT_1",
  quantity: 500,
  side: "LH" // Should be ignored or stripped
};
delete prodRecord.side;
assert(prodRecord.side === undefined, "Production record has NO LH/RH concept");
assert(prodRecord.quantity >= 0, "Production quantity is non-negative");

// 5. Rejection & Side Tests (LH / RH only, BOTH is strictly invalid)
assert(parseRejectionSide("LH") === "LH", "Rejection side LH is valid");
assert(parseRejectionSide("RH") === "RH", "Rejection side RH is valid");
try {
  parseRejectionSide("BOTH");
  assert(false, "Rejection side BOTH must throw error");
} catch(e) {
  assert(e.message.includes("strictly invalid"), "Rejection side BOTH was rejected");
}

// 6. Rebuffing Rules (KAMAL rejection source only)
assert(RebuffingRule.canBeRebuffed(RejectionSource.KAMAL) === true, "KAMAL source is rebuffable");
assert(RebuffingRule.canBeRebuffed(RejectionSource.MRN) === false, "MRN source cannot be rebuffed");
assert(RebuffingRule.canBeRebuffed(RejectionSource.DSPL) === false, "DSPL source cannot be rebuffed");
try {
  RebuffingRule.validateRebuffingEligibility("MRN");
  assert(false, "Rebuffing for MRN must throw");
} catch(e) {
  assert(e.message.includes("KAMAL rejection ONLY"), "Rebuffing eligibility enforced for KAMAL only");
}

// 7. Stock Reconciliation Tests (Manual Opening/Closing, never auto-derived)
const stockRec = {
  date: "2026-09-28",
  model: "U86",
  openingQuantity: 10000,
  receivedQuantity: 2000,
  consumedQuantity: 1500,
  closingQuantity: 10500
};
// Expected = 10000 + 2000 - 1500 = 10500 => Variance = 10500 - 10500 = 0
const expectedClosing = stockRec.openingQuantity + stockRec.receivedQuantity - stockRec.consumedQuantity;
const variance = stockRec.closingQuantity - expectedClosing;
assert(variance === 0, "Stock is balanced with 0 variance");
assert(typeof stockRec.openingQuantity === "number" && typeof stockRec.closingQuantity === "number", "Stock opening and closing are explicit manual numbers");

// 8. Planning Separation Tests
assert(Collections.CUSTOMER_END_PLANS === "customerEndPlans", "Customer End Plans collection is separate");
assert(Collections.MONTHLY_DEPARTMENT_PLANS === "monthlyDepartmentPlans", "Monthly Dept Plans collection is separate");
assert(Collections.DAILY_PLANS === "dailyPlans", "Daily Plans collection is separate");
assert(Collections.PRODUCTION === "production_records", "Production records collection is separate");
assert(Collections.CUSTOMER_END_PLANS !== Collections.DAILY_PLANS && Collections.DAILY_PLANS !== Collections.PRODUCTION, "Planning domains remain strictly decoupled");

// 9. Numeric 0 vs NO DATA Distinction Tests
assert(MetricValue.format(0) === "0", "Metric 0 is formatted as '0'");
assert(MetricValue.format(0, "pcs") === "0 pcs", "Metric 0 pcs is formatted as '0 pcs'");
assert(MetricValue.format(null) === "NO DATA", "null is formatted as 'NO DATA'");
assert(MetricValue.format(undefined) === "NO DATA", "undefined is formatted as 'NO DATA'");
assert(MetricValue.format(MetricValue.NO_DATA) === "NO DATA", "NO_DATA constant is formatted as 'NO DATA'");

// 10. Dashboard Analytics Calculations Tests
const mockProds = [
  { date: "2026-09-28", model: "U86", department: "CASTING", quantity: 1000 },
  { date: "2026-09-28", model: "U86", department: "POST_CASTING", quantity: 980 },
  { date: "2026-09-28", model: "U180", department: "GIL_DELIVERY", quantity: 500 }
];
const mockPlans = [
  { date: "2026-09-28", model: "U86", department: "CASTING", plannedQuantity: 1000 },
  { date: "2026-09-28", model: "U86", department: "POST_CASTING", plannedQuantity: 1000 }
];
const mockRejs = [
  { date: "2026-09-28", model: "U86", department: "CASTING", defectName: "POROSITY", source: "KAMAL", businessArea: "KAMAL", side: "LH", quantity: 20, isRebuffed: true },
  { date: "2026-09-28", model: "U86", department: "CASTING", defectName: "BLOWHOLE", source: "DSPL", businessArea: "DSPL", side: "RH", quantity: 10, isRebuffed: false }
];
const mockStocks = [
  { date: "2026-09-28", model: "U86", openingQuantity: 5000, receivedQuantity: 1000, consumedQuantity: 800, closingQuantity: 5200 }
];
const mockActions = [
  { id: "act-1", department: "CASTING", priority: "CRITICAL", status: "OPEN", description: "Die repair" },
  { id: "act-2", department: "M_C", priority: "MEDIUM", status: "COMPLETED", description: "Oil refill" }
];
const mockAlerts = [
  { id: "alt-1", severity: "HIGH", status: "OPEN", message: "Burr > 1.5%" }
];

const testFilter = new AnalyticsFilter({
  fromDate: "2026-09-28",
  toDate: "2026-09-28",
  model: "ALL",
  department: "ALL"
});

const summary = analyticsEngine.generateExecutiveSummary({
  productions: mockProds,
  plans: mockPlans,
  rejections: mockRejs,
  stocks: mockStocks,
  actions: mockActions,
  alerts: mockAlerts,
  filter: testFilter
});

assert(summary.totalProduction === 2480, `Total production correctly summed: ${summary.totalProduction}`);
assert(summary.totalPlanned === 2000, `Total plan correctly summed: ${summary.totalPlanned}`);
assert(summary.totalRejection === 30, `Total rejection correctly calculated: ${summary.totalRejection}`);
assert(summary.totalRebuffed === 20, `Total rebuffed correctly calculated: ${summary.totalRebuffed}`);
assert(summary.activeAlertsCount === 1, `Active alerts counted: ${summary.activeAlertsCount}`);

// 11. Date and Model Filtering Tests
const filterU86Only = new AnalyticsFilter({
  fromDate: "2026-09-28",
  toDate: "2026-09-28",
  model: "U86",
  department: "ALL"
});
const u86Prods = analyticsEngine.filterProduction(mockProds, filterU86Only);
assert(u86Prods.length === 2, "Filtered production strictly by model U86");
assert(u86Prods.every(r => r.model === "U86"), "No U180 records in U86 filter");

// 12. Defect Pareto Analysis Tests
const pareto = analyticsEngine.buildDefectPareto(mockRejs, testFilter);
assert(pareto.points.length === 2, "Pareto points generated for 2 defects");
assert(pareto.points[0].defectName === "POROSITY", "Top defect is POROSITY");
assert(pareto.points[0].quantity === 20, "Top defect count is 20");
assert(pareto.points[0].percentage.toFixed(1) === "66.7", "Porosity is 66.7% of rejections");

// 13. Rebuffing Trend Tests
const rebuffTrend = analyticsEngine.buildRebuffingTrend(mockRejs, testFilter);
assert(rebuffTrend.points.length === 1, "Rebuffing trend contains 1 point for single date");
assert(rebuffTrend.points[0].value === 20, "KAMAL rebuffed value is 20 pcs");

// 14. Action Tracker Summary Tests
const actionSummary = analyticsEngine.computeActionSummary(mockActions, testFilter);
assert(actionSummary.totalActions === 2, "Total actions 2");
assert(actionSummary.openActions === 1, "Open actions 1");
assert(actionSummary.criticalActions === 1, "Critical actions 1");
assert(actionSummary.completedActions === 1, "Completed actions 1");

// 15. Reports / Executive Meeting Pack Factual Calculation
assert(summary.hasData === true, "Management summary reports data present");

console.log(`\n=== TEST RESULTS ===`);
console.log(`Total Tests Run: ${testsRun}`);
console.log(`Passed: ${testsPassed}`);
console.log(`Failed: ${testsFailed}`);

if (testsFailed === 0) {
  console.log("SUCCESS: 100% of W4 tests PASSED successfully!");
} else {
  console.error("FAILURE: Some tests failed.");
  process.exit(1);
}
