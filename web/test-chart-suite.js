/**
 * DIVINE STAMP PVT LTD — MANUFACTURING MIS
 * FOCUSED CHART TESTS SUITE
 * 
 * Verifies:
 * 1. Chart creation
 * 2. Chart update after date filter
 * 3. Chart update after model filter
 * 4. Chart update after department filter
 * 5. NO DATA handling
 * 6. 0 versus NO DATA
 * 7. Pareto descending order
 * 8. Pareto cumulative percentage
 * 9. 80% reference line plugin
 * 10. Chart destruction/recreation without duplicates
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");

// Load Analytics Engine and Models
const {
  analyticsEngine,
  AnalyticsFilter,
  MetricValue,
  ALL_MODELS,
  CANONICAL_DEPARTMENTS
} = require("./analytics-engine.js");

// Mock MockChart class to test Chart.js options and configurations in local JVM/Node
class MockChart {
  constructor(canvas, config) {
    this.canvas = canvas;
    this.config = config;
    this.type = config.type;
    this.data = config.data;
    this.options = config.options;
    this.plugins = config.plugins || [];
    this.destroyed = false;
    this.scales = {
      y: { getPixelForValue: (v) => 100 - v },
      y1: { getPixelForValue: (v) => 200 - v * 2 }
    };
    this.chartArea = { left: 10, right: 300, top: 10, bottom: 200 };
    this.ctx = {
      save: () => {},
      restore: () => {},
      beginPath: () => {},
      setLineDash: () => {},
      moveTo: () => {},
      lineTo: () => {},
      stroke: () => {},
      fillText: () => {}
    };
  }
  destroy() {
    this.destroyed = true;
  }
}

// Global Chart mock
global.Chart = MockChart;

// Global Document mock for headless Node.js testing
global.document = {
  getElementById: (id) => ({
    id,
    classList: {
      add: () => {},
      remove: () => {}
    }
  })
};

// Dynamic import of chart-manager.js (ES Module)
async function runChartTestSuite() {
  console.log("====================================================");
  console.log(" STARTING FOCUSED CHART SUITE FOR DIVINE STAMP MIS ");
  console.log("====================================================");

  const chartManager = await import("./chart-manager.js");
  const {
    renderDailyProductionTrendChart,
    renderDailyRejectionTrendChart,
    renderModelWiseChart,
    renderDepartmentWiseChart,
    renderDefectParetoChart,
    renderRejectionByModelChart,
    destroyChart,
    destroyAllCharts,
    pareto80LinePlugin
  } = chartManager;

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

  // Sample production & rejection fixtures
  const sampleProductions = [
    { id: "p1", date: "2026-09-01", model: "U86", department: "CASTING", quantity: 500 },
    { id: "p2", date: "2026-09-02", model: "U86", department: "CASTING", quantity: 0 }, // explicit 0
    { id: "p3", date: "2026-09-03", model: "U244", department: "POST_CASTING", quantity: 750 },
    { id: "p4", date: "2026-09-05", model: "DRUM", department: "GIL_DELIVERY", quantity: 600 }
    // Note: 2026-09-04 is intentionally missing (NO DATA)
  ];

  const sampleRejections = [
    { id: "r1", date: "2026-09-01", model: "U86", department: "CASTING", defectName: "POROSITY", quantity: 40, side: "LH", source: "KAMAL", businessArea: "KAMAL" },
    { id: "r2", date: "2026-09-01", model: "U86", department: "CASTING", defectName: "BLOW_HOLE", quantity: 20, side: "RH", source: "KAMAL", businessArea: "KAMAL" },
    { id: "r3", date: "2026-09-03", model: "U244", department: "POST_CASTING", defectName: "CRACK", quantity: 10, side: "LH", source: "MRN", businessArea: "GABRIEL" },
    { id: "r4", date: "2026-09-05", model: "DRUM", department: "GIL_DELIVERY", defectName: "SCRATCH", quantity: 5, side: "RH", source: "DSPL", businessArea: "DSPL" }
  ];

  const baseFilter = new AnalyticsFilter({
    fromDate: "2026-09-01",
    toDate: "2026-09-05",
    model: "ALL",
    department: "ALL"
  });

  // TEST 1: Chart creation
  test("Test #1: Chart creation for all 6 chart types", () => {
    const prodTrend = analyticsEngine.buildDailyProductionTrend(sampleProductions, baseFilter);
    const rejTrend = analyticsEngine.buildRejectionTrend(sampleRejections, baseFilter);
    const modelProd = analyticsEngine.buildModelWiseProduction(sampleProductions, baseFilter);
    const modelRej = analyticsEngine.buildRejectionByModel(sampleRejections, baseFilter);
    const deptProd = analyticsEngine.buildDepartmentWiseProduction(sampleProductions, baseFilter);
    const deptRej = analyticsEngine.buildRejectionByDepartment(sampleRejections, baseFilter);
    const pareto = analyticsEngine.buildDefectPareto(sampleRejections, baseFilter);

    const c1 = renderDailyProductionTrendChart("c1", prodTrend, baseFilter, MockChart);
    const c2 = renderDailyRejectionTrendChart("c2", rejTrend, baseFilter, MockChart);
    const c3 = renderModelWiseChart("c3", modelProd, modelRej, baseFilter, MockChart);
    const c4 = renderDepartmentWiseChart("c4", deptProd, deptRej, baseFilter, MockChart);
    const c5 = renderDefectParetoChart("c5", pareto, baseFilter, MockChart);
    const c6 = renderRejectionByModelChart("c6", modelRej, baseFilter, MockChart);

    assert(c1 instanceof MockChart, "c1 not instance of MockChart");
    assert(c2 instanceof MockChart, "c2 not instance of MockChart");
    assert(c3 instanceof MockChart, "c3 not instance of MockChart");
    assert(c4 instanceof MockChart, "c4 not instance of MockChart");
    assert(c5 instanceof MockChart, "c5 not instance of MockChart");
    assert(c6 instanceof MockChart, "c6 not instance of MockChart");
  });

  // TEST 2: Chart update after date filter
  test("Test #2: Chart update after date filter", () => {
    const narrowFilter = new AnalyticsFilter({
      fromDate: "2026-09-01",
      toDate: "2026-09-02",
      model: "ALL",
      department: "ALL"
    });
    const prodTrend = analyticsEngine.buildDailyProductionTrend(sampleProductions, narrowFilter);
    const chart = renderDailyProductionTrendChart("date_test", prodTrend, narrowFilter, MockChart);

    assert.strictEqual(chart.data.labels.length, 2, "Expected 2 dates for 2-day filter");
    assert.deepStrictEqual(chart.data.labels, ["2026-09-01", "2026-09-02"]);
  });

  // TEST 3: Chart update after model filter
  test("Test #3: Chart update after model filter (ALL vs specific model)", () => {
    const u86Filter = new AnalyticsFilter({
      fromDate: "2026-09-01",
      toDate: "2026-09-05",
      model: "U86",
      department: "ALL"
    });
    const modelProd = analyticsEngine.buildModelWiseProduction(sampleProductions, u86Filter);
    const modelRej = analyticsEngine.buildRejectionByModel(sampleRejections, u86Filter);
    const chart = renderModelWiseChart("model_test", modelProd, modelRej, u86Filter, MockChart);

    assert.strictEqual(chart.data.labels.length, 1, "Expected only 1 model label when filtered by U86");
    assert.strictEqual(chart.data.labels[0], "U86");
    assert.strictEqual(chart.data.datasets[0].data[0], 500, "Expected 500 production for U86");
  });

  // TEST 4: Chart update after department filter
  test("Test #4: Chart update after department filter", () => {
    const castingFilter = new AnalyticsFilter({
      fromDate: "2026-09-01",
      toDate: "2026-09-05",
      model: "ALL",
      department: "CASTING"
    });
    const deptProd = analyticsEngine.buildDepartmentWiseProduction(sampleProductions, castingFilter);
    const deptRej = analyticsEngine.buildRejectionByDepartment(sampleRejections, castingFilter);
    const chart = renderDepartmentWiseChart("dept_test", deptProd, deptRej, castingFilter, MockChart);

    assert.strictEqual(chart.data.labels.length, 1, "Expected 1 department when filtered by CASTING");
    assert.strictEqual(chart.data.labels[0], "CASTING");
    assert.strictEqual(chart.data.datasets[0].data[0], deptProd.points[0].quantity, "Chart data must match analyticsEngine dept quantity");
  });

  // TEST 5: NO DATA handling
  test("Test #5: NO DATA handling when no records match filter", () => {
    const emptyFilter = new AnalyticsFilter({
      fromDate: "2026-01-01",
      toDate: "2026-01-02",
      model: "ALL",
      department: "ALL"
    });
    const prodTrend = analyticsEngine.buildDailyProductionTrend(sampleProductions, emptyFilter);
    assert.strictEqual(prodTrend.hasData, false, "prodTrend.hasData should be false");

    const chart = renderDailyProductionTrendChart("nodata_test", prodTrend, emptyFilter, MockChart);
    assert.strictEqual(chart, null, "renderDailyProductionTrendChart should return null on NO DATA");
  });

  // TEST 6: 0 versus NO DATA (gap preservation)
  test("Test #6: Explicit 0 is numeric 0 while missing date is null with spanGaps: false", () => {
    const prodTrend = analyticsEngine.buildDailyProductionTrend(sampleProductions, baseFilter);
    const chart = renderDailyProductionTrendChart("zero_vs_nodata", prodTrend, baseFilter, MockChart);

    const values = chart.data.datasets[0].data;
    // Index 0: 2026-09-01 = 500
    assert.strictEqual(values[0], 500, "2026-09-01 should be 500");
    // Index 1: 2026-09-02 = 0 (EXPLICIT ZERO)
    assert.strictEqual(values[1], 0, "2026-09-02 should be explicit numeric 0");
    // Index 3: 2026-09-04 = null (MISSING DATE NO DATA)
    assert.strictEqual(values[3], null, "2026-09-04 should be null (gap)");
    // spanGaps must be false
    assert.strictEqual(chart.data.datasets[0].spanGaps, false, "spanGaps must be false to preserve gap");
  });

  // TEST 7: Pareto descending order
  test("Test #7: Defect Pareto sorted descending by quantity", () => {
    const pareto = analyticsEngine.buildDefectPareto(sampleRejections, baseFilter);
    const chart = renderDefectParetoChart("pareto_test", pareto, baseFilter, MockChart);

    const quantities = chart.data.datasets.find(d => d.type === "bar").data;
    assert.deepStrictEqual(quantities, [40, 20, 10, 5], "Quantities must be in strict descending order");

    const defects = chart.data.labels;
    assert.deepStrictEqual(defects, ["POROSITY", "BLOW_HOLE", "CRACK", "SCRATCH"]);
  });

  // TEST 8: Pareto cumulative percentage
  test("Test #8: Pareto cumulative percentage calculation and 100% termination", () => {
    const pareto = analyticsEngine.buildDefectPareto(sampleRejections, baseFilter);
    const chart = renderDefectParetoChart("pareto_cum", pareto, baseFilter, MockChart);

    const cumLine = chart.data.datasets.find(d => d.type === "line").data;
    // Total = 40 + 20 + 10 + 5 = 75
    // Cum: 40/75 = 53.3%, (40+20)/75 = 80.0%, (40+20+10)/75 = 93.3%, 75/75 = 100.0%
    assert.strictEqual(cumLine[0], 53.3);
    assert.strictEqual(cumLine[1], 80.0);
    assert.strictEqual(cumLine[2], 93.3);
    assert.strictEqual(cumLine[3], 100.0);
    assert.strictEqual(cumLine[cumLine.length - 1], 100.0, "Last cumulative point must equal 100%");
  });

  // TEST 9: 80% reference line plugin
  test("Test #9: 80% reference line plugin registered and draws line", () => {
    const pareto = analyticsEngine.buildDefectPareto(sampleRejections, baseFilter);
    const chart = renderDefectParetoChart("pareto_plugin", pareto, baseFilter, MockChart);

    assert(chart.plugins.includes(pareto80LinePlugin), "Chart must include pareto80LinePlugin");
    assert.strictEqual(pareto80LinePlugin.id, "pareto80Line");

    // Call afterDraw to verify no exceptions
    let drawn = false;
    chart.ctx.stroke = () => { drawn = true; };
    pareto80LinePlugin.afterDraw(chart);
    assert(drawn, "80% line afterDraw stroke was executed");
  });

  // TEST 10: Chart destruction and recreation without duplicates
  test("Test #10: Chart destruction and recreation without leaks", () => {
    const prodTrend = analyticsEngine.buildDailyProductionTrend(sampleProductions, baseFilter);
    const c1 = renderDailyProductionTrendChart("leak_test", prodTrend, baseFilter, MockChart);
    assert.strictEqual(c1.destroyed, false);

    // Render again on same canvas ID
    const c2 = renderDailyProductionTrendChart("leak_test", prodTrend, baseFilter, MockChart);
    assert.strictEqual(c1.destroyed, true, "First chart instance should be destroyed");
    assert.strictEqual(c2.destroyed, false, "Second chart instance should be active");

    destroyChart("leak_test");
    assert.strictEqual(c2.destroyed, true, "Second chart instance should now be destroyed");
  });

  console.log("====================================================");
  console.log(`TOTAL CHART CHECKS: ${passed + failed}`);
  console.log(`PASSED: ${passed}`);
  console.log(`FAILED: ${failed}`);
  console.log("====================================================");

  if (failed > 0) {
    process.exit(1);
  }
}

runChartTestSuite().catch(err => {
  console.error("Fatal test error:", err);
  process.exit(1);
});
