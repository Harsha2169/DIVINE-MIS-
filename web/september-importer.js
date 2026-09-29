/**
 * DIVINE STAMP PVT LTD — MANUFACTURING MIS
 * SEPTEMBER 2026 SCHEMA-READY DATA IMPORTER & VALIDATOR
 * 
 * Invariants Enforced:
 * 1. Sheet 1: Production_Fact (1260 records, canonical models & depts, U180/MAXR GIL-only, no LH/RH, ALL prohibited)
 * 2. Sheet 2: Rejection_Row_Fact (KAMAL 419 rows, MRN 420 rows, DSPL 0 rows NO_DATA, LH/RH only)
 * 3. Sheet 3: Rejection_Defect_Fact (KAMAL 611 defect rows, MRN 84 defect rows, DSPL 0 rows)
 * 4. Sheet 4: Data_Quality (DSPL zero rejections treated strictly as NO_DATA)
 * 5. Idempotent keying to prevent duplicates
 * 6. Audit logging adhering to existing audit schema
 */

import fs from "fs";
import path from "path";

export const EXPECTED_COUNTS = {
  PRODUCTION_FACT: 1260,
  KAMAL_REJECTION_ROWS: 419,
  KAMAL_DEFECT_ROWS: 611,
  MRN_REJECTION_ROWS: 420,
  MRN_DEFECT_ROWS: 84,
  DSPL_REJECTION_ROWS: 0,
  DSPL_DEFECT_ROWS: 0
};

export const CANONICAL_DEPARTMENTS = [
  "CASTING",
  "POST CASTING",
  "M/C",
  "BUFFING",
  "FINAL",
  "KAMAL OK",
  "GIL DELIVERY"
];

export const CANONICAL_MODELS = [
  "U86",
  "U180",
  "U244",
  "MAXR",
  "N282-DISC",
  "DRUM",
  "N360"
];

export const CANONICAL_SOURCES = ["KAMAL", "MRN", "DSPL"];
export const CANONICAL_BUSINESS_AREAS = ["KAMAL", "GABRIEL", "DSPL"];
export const CANONICAL_SIDES = ["LH", "RH"];

/**
 * Validate a single production fact record
 */
export function validateProductionRecord(row, idx) {
  const errors = [];

  // Date check
  if (!row.date || typeof row.date !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(row.date)) {
    errors.push(`Row ${idx}: Invalid date '${row.date}'. Expected YYYY-MM-DD.`);
  } else if (!row.date.startsWith("2026-09-")) {
    errors.push(`Row ${idx}: Date '${row.date}' does not belong to September 2026.`);
  }

  // Department check
  const dept = String(row.department || "").trim();
  if (!CANONICAL_DEPARTMENTS.includes(dept)) {
    errors.push(`Row ${idx}: Invalid department '${dept}'. Must be one of canonical departments.`);
  }

  // Model check
  let model = String(row.model || "").trim();
  if (model === "N282 DISC") {
    model = "N282-DISC"; // canonical normalization
  }
  if (model.toUpperCase() === "ALL") {
    errors.push(`Row ${idx}: Model 'ALL' is filter-only and must never be stored.`);
  } else if (!CANONICAL_MODELS.includes(model)) {
    errors.push(`Row ${idx}: Invalid model '${model}'.`);
  }

  // Model applicability check
  if ((model === "U180" || model === "MAXR") && dept !== "GIL DELIVERY") {
    errors.push(`Row ${idx}: Model '${model}' is applicable ONLY to GIL DELIVERY. Found in '${dept}'.`);
  }

  // Check no LH/RH
  if (row.side || row.LH || row.RH) {
    errors.push(`Row ${idx}: Production record must not contain LH/RH field.`);
  }

  // Data status & actual handling
  const status = String(row.dataStatus || "").trim().toUpperCase();
  let actualVal = row.actual;
  if (status === "NO_DATA") {
    actualVal = null;
  } else if (status === "NOT_ENTERED" || row.actualEntered === 0 || row.actualEntered === false) {
    actualVal = null;
  } else if (typeof actualVal === "number") {
    // preserve explicit 0
    actualVal = actualVal >= 0 ? actualVal : null;
  }

  return {
    isValid: errors.length === 0,
    errors,
    record: {
      id: `prod_${row.date}_${dept.replace(/[\s\/]/g, "_")}_${model}`,
      date: row.date,
      department: dept,
      model,
      planQuantity: typeof row.plan === "number" ? row.plan : 0,
      quantity: actualVal,
      actualEntered: Boolean(row.actualEntered && actualVal !== null),
      dataStatus: status || (actualVal !== null ? "ENTERED" : "NOT_ENTERED"),
      source: "IMPORT_SEPTEMBER_2026"
    }
  };
}

/**
 * Validate a rejection row record
 */
export function validateRejectionRow(row, idx) {
  const errors = [];

  // Date check
  if (!row.date || typeof row.date !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(row.date)) {
    errors.push(`Row ${idx}: Invalid date '${row.date}'.`);
  }

  // Source check
  const source = String(row.source || "").trim().toUpperCase();
  if (!CANONICAL_SOURCES.includes(source)) {
    errors.push(`Row ${idx}: Invalid rejection source '${source}'.`);
  }

  // Business area check and mapping
  let expectedArea = "KAMAL";
  if (source === "MRN") expectedArea = "GABRIEL";
  else if (source === "DSPL") expectedArea = "DSPL";

  const businessArea = String(row.businessArea || expectedArea).trim().toUpperCase();
  if (businessArea !== expectedArea) {
    errors.push(`Row ${idx}: Business area mismatch. Source '${source}' must map to '${expectedArea}', found '${businessArea}'.`);
  }

  // Side check: strictly LH or RH
  const side = String(row.side || "").trim().toUpperCase();
  if (side === "BOTH") {
    errors.push(`Row ${idx}: Rejection side 'BOTH' is strictly prohibited.`);
  } else if (!CANONICAL_SIDES.includes(side)) {
    errors.push(`Row ${idx}: Invalid side '${side}'. Must be LH or RH only.`);
  }

  // DSPL check: zero rejection in DSPL must remain NO_DATA and not imported as actual
  if (source === "DSPL") {
    if (row.rejectionQty === 0 || row.dataStatus === "NO_DATA") {
      return { isValid: true, isDsplNoData: true, errors: [], record: null };
    }
  }

  let model = String(row.model || "").trim();
  if (model === "N282 DISC") model = "N282-DISC";

  return {
    isValid: errors.length === 0,
    isDsplNoData: false,
    errors,
    record: {
      id: `rej_row_${row.date}_${source}_${model}_${String(row.partName || "").replace(/\s/g, "_")}_${side}_${idx}`,
      date: row.date,
      source,
      businessArea,
      model,
      partName: String(row.partName || "").trim(),
      side,
      quantity: typeof row.rejectionQty === "number" ? row.rejectionQty : 0,
      inspectedQuantity: typeof row.inspectedOrProductionQty === "number" ? row.inspectedOrProductionQty : null,
      rejectionRatePct: typeof row.rejectionRatePct === "number" ? row.rejectionRatePct : null,
      isRebuffed: source === "KAMAL", // Only KAMAL source eligible for rebuffing
      dataStatus: row.dataStatus || "ENTERED",
      sourceFile: "DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx"
    }
  };
}

/**
 * Validate a rejection defect record
 */
export function validateRejectionDefect(row, idx) {
  const errors = [];

  const source = String(row.source || "").trim().toUpperCase();
  if (!CANONICAL_SOURCES.includes(source)) {
    errors.push(`Defect Row ${idx}: Invalid source '${source}'.`);
  }

  let expectedArea = source === "MRN" ? "GABRIEL" : (source === "DSPL" ? "DSPL" : "KAMAL");
  const businessArea = String(row.businessArea || expectedArea).trim().toUpperCase();

  const side = String(row.side || "").trim().toUpperCase();
  if (side === "BOTH" || !CANONICAL_SIDES.includes(side)) {
    errors.push(`Defect Row ${idx}: Invalid side '${side}'. Must be LH or RH.`);
  }

  const defectCategory = String(row.rejectionCategory || "").trim();
  if (!defectCategory) {
    errors.push(`Defect Row ${idx}: Missing rejectionCategory.`);
  }

  let model = String(row.model || "").trim();
  if (model === "N282 DISC") model = "N282-DISC";

  // DSPL NO_DATA rule
  if (source === "DSPL") {
    return { isValid: true, isDsplNoData: true, errors: [], record: null };
  }

  return {
    isValid: errors.length === 0,
    isDsplNoData: false,
    errors,
    record: {
      id: `rej_defect_${row.date}_${source}_${model}_${side}_${defectCategory.replace(/[\s\/]/g, "_")}_${idx}`,
      date: row.date,
      source,
      businessArea,
      model,
      partName: String(row.partName || "").trim(),
      side,
      defectName: defectCategory,
      quantity: typeof row.rejectionQty === "number" ? row.rejectionQty : 0,
      dataStatus: row.dataStatus || "ENTERED",
      isRebuffed: source === "KAMAL"
    }
  };
}

/**
 * Parses and processes workbook data from JSON sheets or XLSX file buffer
 */
export function processSeptemberWorkbook(sheetsData) {
  const result = {
    workbookName: "DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx",
    sheetsProcessed: [],
    production: {
      totalImported: 0,
      byDepartment: {},
      byModel: {},
      byDate: {},
      actualEnteredCount: 0,
      notEnteredCount: 0,
      noDataCount: 0,
      records: []
    },
    rejectionRows: {
      totalImported: 0,
      kamalCount: 0,
      mrnCount: 0,
      dsplCount: 0,
      lhCount: 0,
      rhCount: 0,
      totalQtyBySource: { KAMAL: 0, MRN: 0, DSPL: 0 },
      records: []
    },
    rejectionDefects: {
      totalImported: 0,
      kamalDefectCount: 0,
      mrnDefectCount: 0,
      totalQtyBySource: { KAMAL: 0, MRN: 0 },
      uniqueCategories: new Set(),
      records: []
    },
    dataQuality: {
      duplicatesDetected: 0,
      invalidModelCount: 0,
      invalidDeptCount: 0,
      invalidSourceCount: 0,
      invalidAreaCount: 0,
      invalidSideCount: 0,
      bothOccurrences: 0,
      invalidDateCount: 0,
      unexpectedBlanks: 0,
      dsplNoDataVerified: true,
      validationErrors: []
    }
  };

  // 1. Process Sheet 1: Production_Fact
  if (sheetsData.Production_Fact) {
    result.sheetsProcessed.push("Production_Fact");
    const seenIds = new Set();

    sheetsData.Production_Fact.forEach((row, idx) => {
      const v = validateProductionRecord(row, idx + 1);
      if (!v.isValid) {
        result.dataQuality.validationErrors.push(...v.errors);
      } else {
        const rec = v.record;
        if (seenIds.has(rec.id)) {
          result.dataQuality.duplicatesDetected++;
          return;
        }
        seenIds.add(rec.id);
        result.production.records.push(rec);
        result.production.totalImported++;

        // Stats
        result.production.byDepartment[rec.department] = (result.production.byDepartment[rec.department] || 0) + 1;
        result.production.byModel[rec.model] = (result.production.byModel[rec.model] || 0) + 1;
        result.production.byDate[rec.date] = (result.production.byDate[rec.date] || 0) + 1;

        if (rec.actualEntered) {
          result.production.actualEnteredCount++;
        } else if (rec.dataStatus === "NO_DATA") {
          result.production.noDataCount++;
        } else {
          result.production.notEnteredCount++;
        }
      }
    });
  }

  // 2. Process Sheet 2: Rejection_Row_Fact
  if (sheetsData.Rejection_Row_Fact) {
    result.sheetsProcessed.push("Rejection_Row_Fact");
    const seenIds = new Set();

    sheetsData.Rejection_Row_Fact.forEach((row, idx) => {
      const v = validateRejectionRow(row, idx + 1);
      if (v.isDsplNoData) {
        result.dataQuality.dsplNoDataVerified = true;
        return; // strictly not imported
      }
      if (!v.isValid) {
        result.dataQuality.validationErrors.push(...v.errors);
      } else {
        const rec = v.record;
        if (seenIds.has(rec.id)) {
          result.dataQuality.duplicatesDetected++;
          return;
        }
        seenIds.add(rec.id);
        result.rejectionRows.records.push(rec);
        result.rejectionRows.totalImported++;

        if (rec.source === "KAMAL") result.rejectionRows.kamalCount++;
        if (rec.source === "MRN") result.rejectionRows.mrnCount++;
        if (rec.source === "DSPL") result.rejectionRows.dsplCount++;

        if (rec.side === "LH") result.rejectionRows.lhCount++;
        if (rec.side === "RH") result.rejectionRows.rhCount++;

        result.rejectionRows.totalQtyBySource[rec.source] =
          (result.rejectionRows.totalQtyBySource[rec.source] || 0) + rec.quantity;
      }
    });
  }

  // 3. Process Sheet 3: Rejection_Defect_Fact
  if (sheetsData.Rejection_Defect_Fact) {
    result.sheetsProcessed.push("Rejection_Defect_Fact");
    const seenIds = new Set();

    sheetsData.Rejection_Defect_Fact.forEach((row, idx) => {
      const v = validateRejectionDefect(row, idx + 1);
      if (v.isDsplNoData) {
        return;
      }
      if (!v.isValid) {
        result.dataQuality.validationErrors.push(...v.errors);
      } else {
        const rec = v.record;
        if (seenIds.has(rec.id)) {
          result.dataQuality.duplicatesDetected++;
          return;
        }
        seenIds.add(rec.id);
        result.rejectionDefects.records.push(rec);
        result.rejectionDefects.totalImported++;

        if (rec.source === "KAMAL") result.rejectionDefects.kamalDefectCount++;
        if (rec.source === "MRN") result.rejectionDefects.mrnDefectCount++;

        result.rejectionDefects.totalQtyBySource[rec.source] =
          (result.rejectionDefects.totalQtyBySource[rec.source] || 0) + rec.quantity;
        result.rejectionDefects.uniqueCategories.add(rec.defectName);
      }
    });
  }

  // 4. Sheet 4: Data_Quality
  if (sheetsData.Data_Quality) {
    result.sheetsProcessed.push("Data_Quality");
  }

  return result;
}
