// Authoritative Analytics Engine for Divine Stamp Manufacturing MIS Web
// Mirrors com.example.analytics.ManufacturingAnalyticsService and com.example.report.MeetingPackService exactly.

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
  MetricValue
} from "./manufacturing-contracts.js";

/**
 * Generates an array of ISO date strings YYYY-MM-DD from startDate to endDate (inclusive)
 */
export function generateDateSequence(startDateStr, endDateStr) {
  const dates = [];
  if (!startDateStr || !endDateStr) return dates;
  const curr = new Date(startDateStr);
  const end = new Date(endDateStr);
  // Guard against infinite loop
  let count = 0;
  while (curr <= end && count < 366) {
    dates.push(curr.toISOString().split("T")[0]);
    curr.setDate(curr.getDate() + 1);
    count++;
  }
  return dates;
}

/**
 * Filter Matching Helper
 * Invariant: 'ALL' matches any item.
 */
export function filterMatches(filterVal, itemVal) {
  if (!filterVal || filterVal.toUpperCase() === "ALL") return true;
  return filterVal.toUpperCase() === (itemVal || "").toUpperCase();
}

/**
 * Filter Context helper
 */
export class AnalyticsFilter {
  constructor({ fromDate, toDate, model = "ALL", department = "ALL", source = "ALL", businessArea = "ALL", side = "ALL", defect = "ALL" } = {}) {
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.model = model;
    this.department = department;
    this.source = source;
    this.businessArea = businessArea;
    this.side = side;
    this.defect = defect;
  }
}

export class AnalyticsEngine {
  /**
   * Filter production records
   */
  filterProduction(records, filter) {
    return records.filter(r => {
      const matchDate = (!filter.fromDate || r.date >= filter.fromDate) &&
                        (!filter.toDate || r.date <= filter.toDate);
      const matchModel = filterMatches(filter.model, r.model);
      const matchDept = filterMatches(filter.department, r.department);
      return matchDate && matchModel && matchDept;
    });
  }

  /**
   * Filter rejection records
   */
  filterRejection(records, filter) {
    return records.filter(r => {
      const matchDate = (!filter.fromDate || r.date >= filter.fromDate) &&
                        (!filter.toDate || r.date <= filter.toDate);
      const matchModel = filterMatches(filter.model, r.model);
      const matchDept = filterMatches(filter.department, r.department);
      const matchSource = filterMatches(filter.source, r.source);
      const matchArea = filterMatches(filter.businessArea, r.businessArea);
      const matchSide = filterMatches(filter.side, r.side);
      const matchDefect = filterMatches(filter.defect, r.defectName);
      return matchDate && matchModel && matchDept && matchSource && matchArea && matchSide && matchDefect;
    });
  }

  /**
   * Filter stock records
   */
  filterStock(records, filter) {
    return records.filter(r => {
      const matchDate = (!filter.fromDate || r.date >= filter.fromDate) &&
                        (!filter.toDate || r.date <= filter.toDate);
      const matchModel = filterMatches(filter.model, r.model);
      return matchDate && matchModel;
    });
  }

  /**
   * 1. Daily Production Trend
   */
  buildDailyProductionTrend(records, filter) {
    const dates = generateDateSequence(filter.fromDate, filter.toDate);
    const filtered = this.filterProduction(records, filter);
    const byDate = new Map();
    filtered.forEach(r => {
      byDate.set(r.date, (byDate.get(r.date) || 0) + (r.quantity || 0));
    });

    const points = dates.map(d => ({
      date: d,
      value: byDate.has(d) ? byDate.get(d) : MetricValue.NO_DATA
    }));

    return {
      points,
      hasData: filtered.length > 0
    };
  }

  /**
   * 2. Plan vs Actual & Achievement %
   */
  buildPlanVsActual(plans, productions, filter) {
    const dates = generateDateSequence(filter.fromDate, filter.toDate);
    const filteredPlans = plans.filter(p => {
      const matchDate = (!filter.fromDate || p.date >= filter.fromDate) &&
                        (!filter.toDate || p.date <= filter.toDate);
      return matchDate && filterMatches(filter.model, p.model) && filterMatches(filter.department, p.department);
    });
    const filteredProds = this.filterProduction(productions, filter);

    const plansByDate = new Map();
    filteredPlans.forEach(p => {
      plansByDate.set(p.date, (plansByDate.get(p.date) || 0) + (p.plannedQuantity || 0));
    });

    const prodsByDate = new Map();
    filteredProds.forEach(p => {
      prodsByDate.set(p.date, (prodsByDate.get(p.date) || 0) + (p.quantity || 0));
    });

    let totalPlan = 0;
    let totalActual = 0;
    let planCount = 0;
    let prodCount = 0;

    const points = dates.map(d => {
      const planVal = plansByDate.has(d) ? plansByDate.get(d) : MetricValue.NO_DATA;
      const actualVal = prodsByDate.has(d) ? prodsByDate.get(d) : MetricValue.NO_DATA;
      if (typeof planVal === "number") {
        totalPlan += planVal;
        planCount++;
      }
      if (typeof actualVal === "number") {
        totalActual += actualVal;
        prodCount++;
      }
      let achPct = MetricValue.NO_DATA;
      if (typeof planVal === "number" && typeof actualVal === "number") {
        achPct = planVal > 0 ? (actualVal / planVal) * 100 : 0;
      }
      return { date: d, plan: planVal, actual: actualVal, achievement: achPct };
    });

    const overallAchievement = (planCount > 0 && prodCount > 0)
      ? (totalPlan > 0 ? (totalActual / totalPlan) * 100 : 0)
      : MetricValue.NO_DATA;

    return {
      points,
      totalPlan: planCount > 0 ? totalPlan : MetricValue.NO_DATA,
      totalActual: prodCount > 0 ? totalActual : MetricValue.NO_DATA,
      achievementPercentage: overallAchievement,
      hasData: filteredPlans.length > 0 || filteredProds.length > 0
    };
  }

  /**
   * 3. Model-wise Production
   */
  buildModelWiseProduction(records, filter) {
    const filtered = this.filterProduction(records, filter);
    const byModel = new Map();
    filtered.forEach(r => {
      byModel.set(r.model, (byModel.get(r.model) || 0) + (r.quantity || 0));
    });

    const points = ALL_MODELS
      .filter(m => filterMatches(filter.model, m.code))
      .map(m => ({
        model: m.code,
        quantity: byModel.has(m.code) ? byModel.get(m.code) : MetricValue.NO_DATA
      }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 4. Department-wise Production & Flow
   */
  buildDepartmentWiseProduction(records, filter) {
    const filtered = this.filterProduction(records, filter);
    const byDept = new Map();
    filtered.forEach(r => {
      byDept.set(r.department, (byDept.get(r.department) || 0) + (r.quantity || 0));
    });

    const points = CANONICAL_DEPARTMENTS
      .filter(d => filterMatches(filter.department, d.code) || filterMatches(filter.department, d.displayName))
      .map(d => ({
        department: d.displayName,
        code: d.code,
        sequence: d.sequenceNumber,
        quantity: (byDept.has(d.code) || byDept.has(d.displayName))
          ? ((byDept.get(d.code) || 0) + (byDept.get(d.displayName) || 0))
          : MetricValue.NO_DATA
      }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 5. Rejection Trend
   */
  buildRejectionTrend(records, filter) {
    const dates = generateDateSequence(filter.fromDate, filter.toDate);
    const filtered = this.filterRejection(records, filter);
    const byDate = new Map();
    filtered.forEach(r => {
      byDate.set(r.date, (byDate.get(r.date) || 0) + (r.quantity || 0));
    });

    const points = dates.map(d => ({
      date: d,
      value: byDate.has(d) ? byDate.get(d) : MetricValue.NO_DATA
    }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 6. Rejection by Model
   */
  buildRejectionByModel(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const byModel = new Map();
    filtered.forEach(r => {
      byModel.set(r.model, (byModel.get(r.model) || 0) + (r.quantity || 0));
    });

    const points = ALL_MODELS
      .filter(m => filterMatches(filter.model, m.code))
      .map(m => ({
        model: m.code,
        quantity: byModel.has(m.code) ? byModel.get(m.code) : MetricValue.NO_DATA
      }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 7. Rejection by Department
   */
  buildRejectionByDepartment(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const byDept = new Map();
    filtered.forEach(r => {
      byDept.set(r.department, (byDept.get(r.department) || 0) + (r.quantity || 0));
    });

    const points = CANONICAL_DEPARTMENTS
      .filter(d => filterMatches(filter.department, d.code) || filterMatches(filter.department, d.displayName))
      .map(d => ({
        department: d.displayName,
        code: d.code,
        quantity: (byDept.has(d.code) || byDept.has(d.displayName))
          ? ((byDept.get(d.code) || 0) + (byDept.get(d.displayName) || 0))
          : MetricValue.NO_DATA
      }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 8. Rejection by Source (KAMAL, MRN, DSPL)
   */
  buildRejectionBySource(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const bySource = new Map();
    filtered.forEach(r => {
      bySource.set(r.source, (bySource.get(r.source) || 0) + (r.quantity || 0));
    });

    const sources = [RejectionSource.KAMAL, RejectionSource.MRN, RejectionSource.DSPL];
    const points = sources.map(s => ({
      source: s,
      quantity: bySource.has(s) ? bySource.get(s) : MetricValue.NO_DATA
    }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 9. Rejection by Business Area (KAMAL, GABRIEL, DSPL)
   */
  buildRejectionByBusinessArea(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const byArea = new Map();
    filtered.forEach(r => {
      byArea.set(r.businessArea, (byArea.get(r.businessArea) || 0) + (r.quantity || 0));
    });

    const areas = [BusinessArea.KAMAL, BusinessArea.GABRIEL, BusinessArea.DSPL];
    const points = areas.map(a => ({
      businessArea: a,
      quantity: byArea.has(a) ? byArea.get(a) : MetricValue.NO_DATA
    }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 10. Rejection Side: LH vs RH (strictly LH / RH, no BOTH)
   */
  buildLhVsRh(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const bySide = new Map();
    filtered.forEach(r => {
      bySide.set(r.side, (bySide.get(r.side) || 0) + (r.quantity || 0));
    });

    const points = [
      { side: "LH", quantity: bySide.has("LH") ? bySide.get("LH") : MetricValue.NO_DATA },
      { side: "RH", quantity: bySide.has("RH") ? bySide.get("RH") : MetricValue.NO_DATA }
    ];

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 11. Defect-wise Rejection & Pareto
   */
  buildDefectPareto(records, filter) {
    const filtered = this.filterRejection(records, filter);
    const byDefect = new Map();
    let totalRejection = 0;

    filtered.forEach(r => {
      const count = r.quantity || 0;
      byDefect.set(r.defectName, (byDefect.get(r.defectName) || 0) + count);
      totalRejection += count;
    });

    const sortedDefects = Array.from(byDefect.entries())
      .map(([name, qty]) => ({ defectName: name, quantity: qty }))
      .sort((a, b) => b.quantity - a.quantity);

    let cumulative = 0;
    const points = sortedDefects.map(d => {
      cumulative += d.quantity;
      const cumPct = totalRejection > 0 ? (cumulative / totalRejection) * 100 : 0;
      return {
        defectName: d.defectName,
        quantity: d.quantity,
        percentage: totalRejection > 0 ? (d.quantity / totalRejection) * 100 : 0,
        cumulativePercentage: cumPct
      };
    });

    return {
      points,
      totalRejection: filtered.length > 0 ? totalRejection : MetricValue.NO_DATA,
      hasData: filtered.length > 0
    };
  }

  /**
   * 12. Rebuffing Trend (KAMAL source only)
   */
  buildRebuffingTrend(records, filter) {
    const dates = generateDateSequence(filter.fromDate, filter.toDate);
    const filtered = this.filterRejection(records, filter).filter(r => 
      r.isRebuffed && r.source === RejectionSource.KAMAL
    );

    const byDate = new Map();
    filtered.forEach(r => {
      byDate.set(r.date, (byDate.get(r.date) || 0) + (r.quantity || 0));
    });

    const points = dates.map(d => ({
      date: d,
      value: byDate.has(d) ? byDate.get(d) : MetricValue.NO_DATA
    }));

    return { points, hasData: filtered.length > 0 };
  }

  /**
   * 13. Stock Reconciliation & Trend
   */
  buildStockAnalytics(records, filter) {
    const filtered = this.filterStock(records, filter);
    let totalOpening = 0;
    let totalClosing = 0;
    let totalReceived = 0;
    let totalConsumed = 0;
    let totalVariance = 0;

    filtered.forEach(r => {
      totalOpening += (r.openingQuantity || 0);
      totalClosing += (r.closingQuantity || 0);
      totalReceived += (r.receivedQuantity || 0);
      totalConsumed += (r.consumedQuantity || 0);
      // Variance formula: Closing - (Opening + Received - Consumed)
      const expected = (r.openingQuantity || 0) + (r.receivedQuantity || 0) - (r.consumedQuantity || 0);
      const varVal = typeof r.variance === "number" ? r.variance : ((r.closingQuantity || 0) - expected);
      totalVariance += varVal;
    });

    return {
      totalOpening: filtered.length > 0 ? totalOpening : MetricValue.NO_DATA,
      totalClosing: filtered.length > 0 ? totalClosing : MetricValue.NO_DATA,
      totalReceived: filtered.length > 0 ? totalReceived : MetricValue.NO_DATA,
      totalConsumed: filtered.length > 0 ? totalConsumed : MetricValue.NO_DATA,
      totalVariance: filtered.length > 0 ? totalVariance : MetricValue.NO_DATA,
      records: filtered,
      hasData: filtered.length > 0
    };
  }

  /**
   * 14. Action Tracker Summary
   */
  computeActionSummary(actions, filter) {
    const filtered = actions.filter(a => {
      const matchDept = filterMatches(filter?.department, a.department);
      return matchDept;
    });

    const total = filtered.length;
    const open = filtered.filter(a => ["OPEN", "ASSIGNED", "IN_PROGRESS"].includes(a.status)).length;
    const critical = filtered.filter(a => a.priority === "CRITICAL" && !["CLOSED", "CANCELLED"].includes(a.status)).length;
    const high = filtered.filter(a => a.priority === "HIGH" && !["CLOSED", "CANCELLED"].includes(a.status)).length;
    const onHold = filtered.filter(a => a.status === "ON_HOLD").length;
    const completed = filtered.filter(a => a.status === "COMPLETED").length;
    const closed = filtered.filter(a => a.status === "CLOSED").length;

    return {
      totalActions: total,
      openActions: open,
      criticalActions: critical,
      highActions: high,
      onHoldActions: onHold,
      completedActions: completed,
      closedActions: closed,
      items: filtered
    };
  }

  /**
   * 15. Overall Executive Management Summary
   */
  generateExecutiveSummary({ productions, plans, rejections, stocks, actions, alerts, filter }) {
    const planVsActual = this.buildPlanVsActual(plans, productions, filter);
    const rejFiltered = this.filterRejection(rejections, filter);
    const prodFiltered = this.filterProduction(productions, filter);
    const stockData = this.buildStockAnalytics(stocks, filter);
    const actionSummary = this.computeActionSummary(actions, filter);

    const totalProd = prodFiltered.length > 0 
      ? prodFiltered.reduce((sum, r) => sum + (r.quantity || 0), 0)
      : MetricValue.NO_DATA;

    const totalRej = rejFiltered.length > 0 
      ? rejFiltered.reduce((sum, r) => sum + (r.quantity || 0), 0)
      : MetricValue.NO_DATA;

    let rejPct = MetricValue.NO_DATA;
    if (typeof totalProd === "number" && typeof totalRej === "number") {
      const denom = totalProd + totalRej;
      rejPct = denom > 0 ? (totalRej / denom) * 100 : 0;
    }

    const rebuffedTotal = rejFiltered
      .filter(r => r.isRebuffed && r.source === RejectionSource.KAMAL)
      .reduce((sum, r) => sum + (r.quantity || 0), 0);

    const activeAlertsCount = alerts ? alerts.filter(a => a.status === "OPEN").length : 0;

    return {
      totalProduction: totalProd,
      totalPlanned: planVsActual.totalPlan,
      achievementPercentage: planVsActual.achievementPercentage,
      totalRejection: totalRej,
      rejectionPercentage: rejPct,
      totalRebuffed: rejFiltered.length > 0 ? rebuffedTotal : MetricValue.NO_DATA,
      openingStock: stockData.totalOpening,
      closingStock: stockData.totalClosing,
      stockVariance: stockData.totalVariance,
      actionSummary,
      activeAlertsCount,
      hasData: prodFiltered.length > 0 || rejFiltered.length > 0 || stocks.length > 0
    };
  }
}

export const analyticsEngine = new AnalyticsEngine();
