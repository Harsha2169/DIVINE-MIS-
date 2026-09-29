/**
 * DIVINE STAMP PVT LTD — MANUFACTURING MIS
 * SEPTEMBER 2026 CLI IMPORT RUNNER
 */

import fs from "fs";
import path from "path";
import { createRequire } from "module";
const require = createRequire(import.meta.url);

import {
  processSeptemberWorkbook,
  EXPECTED_COUNTS
} from "./september-importer.js";

async function main() {
  console.log("====================================================");
  console.log(" DIVINE MIS — SEPTEMBER 2026 DATA IMPORT PROCESSOR ");
  console.log("====================================================");

  // Search paths for DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx
  const candidatePaths = [
    "./DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx",
    "./web/DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx",
    "../DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx",
    "/tmp/DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx"
  ];

  let workbookPath = null;
  for (const p of candidatePaths) {
    if (fs.existsSync(p)) {
      workbookPath = p;
      break;
    }
  }

  if (!workbookPath) {
    console.log("STATUS: WORKBOOK_FILE_NOT_FOUND");
    console.log("Searched paths:");
    candidatePaths.forEach(p => console.log(" - " + path.resolve(p)));
    console.log("\nPlease place 'DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx' into workspace root or web/ directory.");
    return { status: "FILE_NOT_FOUND" };
  }

  console.log(`Loading workbook from: ${workbookPath}`);
  const xlsx = require("xlsx");
  const workbook = xlsx.readFile(workbookPath);

  console.log("Workbook sheets detected:", workbook.SheetNames);
  const sheetsData = {};
  for (const sheetName of workbook.SheetNames) {
    sheetsData[sheetName] = xlsx.utils.sheet_to_json(workbook.Sheets[sheetName]);
  }

  const result = processSeptemberWorkbook(sheetsData);

  console.log("\n--- IMPORT SUMMARY & METRICS ---");
  console.log(`Workbook: ${result.workbookName}`);
  console.log(`Sheets Processed: ${result.sheetsProcessed.join(", ")}`);
  console.log(`Production Records: ${result.production.totalImported} (Expected: ${EXPECTED_COUNTS.PRODUCTION_FACT})`);
  console.log(`KAMAL Rejection Rows: ${result.rejectionRows.kamalCount} (Expected: ${EXPECTED_COUNTS.KAMAL_REJECTION_ROWS})`);
  console.log(`KAMAL Defect Rows: ${result.rejectionDefects.kamalDefectCount} (Expected: ${EXPECTED_COUNTS.KAMAL_DEFECT_ROWS})`);
  console.log(`MRN Rejection Rows: ${result.rejectionRows.mrnCount} (Expected: ${EXPECTED_COUNTS.MRN_REJECTION_ROWS})`);
  console.log(`MRN Defect Rows: ${result.rejectionDefects.mrnDefectCount} (Expected: ${EXPECTED_COUNTS.MRN_DEFECT_ROWS})`);
  console.log(`DSPL Status: NO_DATA (Imported rows: 0, Defect rows: 0)`);
  console.log(`Duplicates Detected: ${result.dataQuality.duplicatesDetected}`);
  console.log(`Validation Errors: ${result.dataQuality.validationErrors.length}`);

  if (result.dataQuality.validationErrors.length > 0) {
    console.log("\nValidation error sample:");
    result.dataQuality.validationErrors.slice(0, 10).forEach(e => console.log(" - " + e));
  }

  // Save compiled JSON dataset for web access
  const exportPayload = {
    metadata: {
      source: "DIVINE_MIS_SEPTEMBER_2026_SCHEMA_DATA.xlsx",
      importTimestamp: new Date().toISOString(),
      productionCount: result.production.totalImported,
      rejectionRowCount: result.rejectionRows.totalImported,
      rejectionDefectCount: result.rejectionDefects.totalImported,
      dsplStatus: "NO_DATA"
    },
    productions: result.production.records,
    rejectionRows: result.rejectionRows.records,
    rejections: result.rejectionDefects.records
  };

  const outputPath = "./web/september-data.json";
  fs.writeFileSync(outputPath, JSON.stringify(exportPayload, null, 2));
  console.log(`\nImported data saved to: ${outputPath} (${(fs.statSync(outputPath).size / 1024).toFixed(1)} KB)`);

  return result;
}

main().catch(err => {
  console.error("Error executing import:", err);
});
