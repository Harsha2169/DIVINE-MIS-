/**
 * DIVINE STAMP PVT LTD — MANUFACTURING MIS
 * LIVE FILTER-DRIVEN CHARTS ENGINE (Chart.js Layer)
 * 
 * Strict Invariants:
 * - 0 vs NO DATA preserved (NO DATA = null / gap, not artificial 0)
 * - ALL is filter-only
 * - U180 & MAXR are restricted according to manufacturing contracts
 * - Charts refresh synchronously on applyFilters()
 * - Chart instances destroyed before re-instantiation to prevent leaks
 */

// Active Chart Instances Map
const activeCharts = {};

/**
 * Destroy existing Chart.js instance by canvas ID
 */
export function destroyChart(canvasId) {
  if (activeCharts[canvasId]) {
    try {
      activeCharts[canvasId].destroy();
    } catch (err) {
      console.warn(`[ChartManager] Error destroying chart on canvas ${canvasId}:`, err);
    }
    delete activeCharts[canvasId];
  }
}

/**
 * Destroy all active Chart.js instances
 */
export function destroyAllCharts() {
  Object.keys(activeCharts).forEach(canvasId => {
    destroyChart(canvasId);
  });
}

/**
 * Helper to toggle canvas vs NO DATA overlay
 */
function toggleNoDataOverlay(canvasId, noDataId, hasData) {
  if (typeof document === 'undefined') return;
  const canvas = document.getElementById(canvasId);
  const overlay = document.getElementById(noDataId);
  if (canvas) {
    if (hasData) {
      canvas.classList.remove('hidden');
    } else {
      canvas.classList.add('hidden');
    }
  }
  if (overlay) {
    if (hasData) {
      overlay.classList.add('hidden');
    } else {
      overlay.classList.remove('hidden');
    }
  }
}

/**
 * 1. DAILY PRODUCTION TREND — LINE CHART
 * Location: Executive / CEO Dashboard
 */
export function renderDailyProductionTrendChart(canvasId, prodTrend, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = prodTrend && prodTrend.hasData && prodTrend.points && prodTrend.points.length > 0;
  toggleNoDataOverlay(canvasId, 'prodTrendChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('prodTrendSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model: ${filter.model} • Dept: ${filter.department} • Range: ${filter.fromDate} to ${filter.toDate}`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  const labels = prodTrend.points.map(p => p.date);
  // Missing dates remain NO DATA/gap (null), NOT automatically zero
  const data = prodTrend.points.map(p => (typeof p.value === 'number' ? p.value : null));

  const chart = new ChartClass(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Production Volume (pcs)',
        data,
        borderColor: '#4F46E5', // Indigo 600
        backgroundColor: 'rgba(79, 70, 229, 0.08)',
        fill: true,
        tension: 0.2,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: '#4F46E5',
        spanGaps: false // CRITICAL: Preserves gap for missing dates
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const val = context.raw;
              if (val === null || val === undefined) return ' Production: NO DATA';
              return ` Production: ${Number(val).toLocaleString()} pcs`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Date (Chronological Sequence)', font: { size: 11 } }
        },
        y: {
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Production Units (pcs)', font: { size: 11 } }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}

/**
 * 2. DAILY REJECTION TREND — LINE CHART
 * Location: Executive / CEO Dashboard
 */
export function renderDailyRejectionTrendChart(canvasId, rejTrend, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = rejTrend && rejTrend.hasData && rejTrend.points && rejTrend.points.length > 0;
  toggleNoDataOverlay(canvasId, 'rejTrendChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('rejTrendSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model: ${filter.model} • Dept: ${filter.department} • Range: ${filter.fromDate} to ${filter.toDate}`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  const labels = rejTrend.points.map(p => p.date);
  const data = rejTrend.points.map(p => (typeof p.value === 'number' ? p.value : null));

  const chart = new ChartClass(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Rejection Volume (pcs)',
        data,
        borderColor: '#E11D48', // Rose 600
        backgroundColor: 'rgba(225, 29, 72, 0.08)',
        fill: true,
        tension: 0.2,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: '#E11D48',
        spanGaps: false // CRITICAL: Preserves gap for missing dates
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const val = context.raw;
              if (val === null || val === undefined) return ' Rejection: NO DATA';
              return ` Rejection: ${Number(val).toLocaleString()} pcs`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Date (Chronological Sequence)', font: { size: 11 } }
        },
        y: {
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Rejection Units (pcs)', font: { size: 11 } }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}

/**
 * 3. MODEL-WISE PRODUCTION VS REJECTION — GROUPED BAR CHART
 * Location: Executive / CEO Dashboard
 */
export function renderModelWiseChart(canvasId, modelProd, modelRej, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = (modelProd && modelProd.hasData) || (modelRej && modelRej.hasData);
  toggleNoDataOverlay(canvasId, 'modelWiseChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('modelWiseSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model Filter: ${filter.model} • Dept: ${filter.department}`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  const labels = modelProd.points.map(p => p.model);
  const prodData = modelProd.points.map(p => (typeof p.quantity === 'number' ? p.quantity : null));
  const rejData = modelProd.points.map(mp => {
    const mr = modelRej && modelRej.points ? modelRej.points.find(r => r.model === mp.model) : null;
    return (mr && typeof mr.quantity === 'number') ? mr.quantity : null;
  });

  const chart = new ChartClass(canvas, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Production (pcs)',
          data: prodData,
          backgroundColor: '#4F46E5', // Indigo
          borderRadius: 4,
          maxBarThickness: 32
        },
        {
          label: 'Rejection (pcs)',
          data: rejData,
          backgroundColor: '#E11D48', // Rose
          borderRadius: 4,
          maxBarThickness: 32
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const val = context.raw;
              if (val === null || val === undefined) return ` ${context.dataset.label}: NO DATA`;
              return ` ${context.dataset.label}: ${Number(val).toLocaleString()} pcs`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10, weight: 'bold' } }
        },
        y: {
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Units (pcs)', font: { size: 11 } }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}

/**
 * 4. DEPARTMENT-WISE PRODUCTION VS REJECTION — GROUPED BAR CHART
 * Location: Executive / CEO Dashboard
 */
export function renderDepartmentWiseChart(canvasId, deptProd, deptRej, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = (deptProd && deptProd.hasData) || (deptRej && deptRej.hasData);
  toggleNoDataOverlay(canvasId, 'deptWiseChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('deptWiseSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model: ${filter.model} • Active Flow Steps`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  const labels = deptProd.points.map(p => p.department);
  const prodData = deptProd.points.map(p => (typeof p.quantity === 'number' ? p.quantity : null));
  const rejData = deptProd.points.map(dp => {
    const dr = deptRej && deptRej.points ? deptRej.points.find(r => r.department === dp.department) : null;
    return (dr && typeof dr.quantity === 'number') ? dr.quantity : null;
  });

  const chart = new ChartClass(canvas, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Production (pcs)',
          data: prodData,
          backgroundColor: '#4338CA', // Indigo 700
          borderRadius: 4,
          maxBarThickness: 28
        },
        {
          label: 'Rejection (pcs)',
          data: rejData,
          backgroundColor: '#BE123C', // Rose 700
          borderRadius: 4,
          maxBarThickness: 28
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const val = context.raw;
              if (val === null || val === undefined) return ` ${context.dataset.label}: NO DATA`;
              return ` ${context.dataset.label}: ${Number(val).toLocaleString()} pcs`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10, weight: 'bold' } }
        },
        y: {
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Units (pcs)', font: { size: 11 } }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}

/**
 * 80% Reference Line Plugin for Pareto Chart
 */
export const pareto80LinePlugin = {
  id: 'pareto80Line',
  afterDraw(chart) {
    const y1 = chart.scales['y1'];
    if (!y1) return;
    const { ctx, chartArea } = chart;
    if (!chartArea) return;
    const { left, right } = chartArea;
    const yVal = y1.getPixelForValue(80);

    ctx.save();
    ctx.beginPath();
    ctx.setLineDash([6, 4]);
    ctx.strokeStyle = '#DC2626'; // Red 600
    ctx.lineWidth = 2;
    ctx.moveTo(left, yVal);
    ctx.lineTo(right, yVal);
    ctx.stroke();

    // 80% Cut-off badge label
    ctx.fillStyle = '#DC2626';
    ctx.font = 'bold 11px sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText('80% Pareto Cut-off', right - 6, yVal - 5);
    ctx.restore();
  }
};

/**
 * 5. DEFECT PARETO — COMBINED BAR + CUMULATIVE LINE CHART
 * Location: Rejection & Quality Tab
 */
export function renderDefectParetoChart(canvasId, paretoData, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = paretoData && paretoData.hasData && paretoData.points && paretoData.points.length > 0;
  toggleNoDataOverlay(canvasId, 'paretoChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('paretoChartSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model: ${filter.model} • Dept: ${filter.department} • Total Rejections: ${paretoData.totalRejection || 0} pcs`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  // Defects sorted exactly descending by quantity
  const labels = paretoData.points.map(p => p.defectName);
  const quantities = paretoData.points.map(p => p.quantity);
  const cumulativePcts = paretoData.points.map(p => Number(p.cumulativePercentage.toFixed(1)));

  const chart = new ChartClass(canvas, {
    data: {
      labels,
      datasets: [
        {
          type: 'line',
          label: 'Cumulative %',
          data: cumulativePcts,
          borderColor: '#D97706', // Amber 600
          backgroundColor: '#D97706',
          borderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 6,
          yAxisID: 'y1',
          tension: 0.2
        },
        {
          type: 'bar',
          label: 'Rejection Count (pcs)',
          data: quantities,
          backgroundColor: 'rgba(225, 29, 72, 0.85)', // Rose 600
          borderColor: '#E11D48',
          borderWidth: 1,
          borderRadius: 4,
          maxBarThickness: 36,
          yAxisID: 'y'
        }
      ]
    },
    plugins: [pareto80LinePlugin],
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            title: function(items) {
              return `Defect: ${items[0].label}`;
            },
            label: function(context) {
              const idx = context.dataIndex;
              const pt = paretoData.points[idx];
              if (!pt) return '';
              if (context.dataset.type === 'bar') {
                return ` Rejection: ${pt.quantity} pcs (${pt.percentage.toFixed(1)}%)`;
              } else {
                return ` Cumulative: ${pt.cumulativePercentage.toFixed(1)}%`;
              }
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10, weight: 'bold' } },
          title: { display: true, text: 'Defects (Sorted by Frequency Descending)', font: { size: 11 } }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          title: { display: true, text: 'Rejection Quantity (pcs)', font: { size: 11 } },
          ticks: { font: { size: 10 } }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          beginAtZero: true,
          min: 0,
          max: 100,
          grid: { drawOnChartArea: false },
          title: { display: true, text: 'Cumulative Percentage (%)', font: { size: 11 } },
          ticks: {
            font: { size: 10 },
            callback: value => `${value}%`
          }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}

/**
 * 6. REJECTION BY MODEL — BAR CHART
 * Location: Rejection & Quality Tab
 */
export function renderRejectionByModelChart(canvasId, rejByModel, filter, ChartClass = (typeof Chart !== 'undefined' ? Chart : null)) {
  destroyChart(canvasId);
  if (!ChartClass) return null;

  const hasData = rejByModel && rejByModel.hasData && rejByModel.points && rejByModel.points.length > 0;
  toggleNoDataOverlay(canvasId, 'rejByModelChartNoData', hasData);

  const subtitle = typeof document !== 'undefined' ? document.getElementById('rejByModelSubtitle') : null;
  if (subtitle && filter) {
    subtitle.textContent = `Model Filter: ${filter.model} • Dept: ${filter.department}`;
  }

  if (!hasData) return null;

  const canvas = typeof document !== 'undefined' ? document.getElementById(canvasId) : null;
  if (!canvas) return null;

  const labels = rejByModel.points.map(p => p.model);
  const data = rejByModel.points.map(p => (typeof p.quantity === 'number' ? p.quantity : null));

  const chart = new ChartClass(canvas, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Rejection Units (pcs)',
        data,
        backgroundColor: '#E11D48', // Rose 600
        borderRadius: 4,
        maxBarThickness: 36
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { boxWidth: 12, font: { size: 11, weight: '600' } }
        },
        tooltip: {
          callbacks: {
            label: function(context) {
              const val = context.raw;
              if (val === null || val === undefined) return ' Rejection: NO DATA';
              return ` Rejection: ${Number(val).toLocaleString()} pcs`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { font: { size: 10, weight: 'bold' } },
          title: { display: true, text: 'Canonical Models', font: { size: 11 } }
        },
        y: {
          beginAtZero: true,
          grid: { color: '#F1F5F9' },
          ticks: { font: { size: 10 } },
          title: { display: true, text: 'Rejection Units (pcs)', font: { size: 11 } }
        }
      }
    }
  });

  activeCharts[canvasId] = chart;
  return chart;
}
