<script lang="ts">
  import RiskGauge from './RiskGauge.svelte';
  import type { ScanRecord } from '../types';

  export let scan: ScanRecord | null = null;
  export let aliveCount: number = 0;
  export let buriedCount: number = 0;
  export let severityCounts: Record<string, number> = {
    CRITICAL: 0,
    HIGH: 0,
    MEDIUM: 0,
    LOW: 0,
    INFO: 0
  };

  $: totalFindings = scan?.totalFindings ?? 0;
  $: riskScore = scan?.riskScore ?? 0;
  $: filesScanned = scan?.totalFiles ?? 0;
  $: commitsScanned = scan?.totalCommits ?? 0;

  function formatDuration(startedAt?: string, completedAt?: string): string {
    if (!startedAt) return '0s';
    const start = new Date(startedAt).getTime();
    const end = completedAt ? new Date(completedAt).getTime() : Date.now();
    const diff = Math.max(0, end - start);
    if (diff < 1000) return `${diff}ms`;
    if (diff < 60000) return `${(diff / 1000).toFixed(1)}s`;
    return `${Math.floor(diff / 60000)}m ${Math.floor((diff % 60000) / 1000)}s`;
  }
</script>

<div class="metrics-grid">
  <!-- Risk Score Card -->
  <div class="metric-card card-risk">
    <div class="card-title">Risk Assessment</div>
    <RiskGauge score={riskScore} size={135} />
  </div>

  <!-- ALIVE Findings Card -->
  <div class="metric-card card-alive">
    <div class="card-header">
      <span class="card-title">ALIVE SECRETS</span>
      <span class="pulse-indicator"></span>
    </div>
    <div class="card-value alive-value">{aliveCount}</div>
    <div class="card-subtext">Active in current files / HEAD branch</div>
    <div class="threat-alert">Immediate rotation needed</div>
  </div>

  <!-- BURIED Findings Card -->
  <div class="metric-card card-buried">
    <div class="card-header">
      <span class="card-title">BURIED SECRETS</span>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="4"/>
        <line x1="2" y1="12" x2="8" y2="12"/>
        <line x1="16" y1="12" x2="22" y2="12"/>
      </svg>
    </div>
    <div class="card-value buried-value">{buriedCount}</div>
    <div class="card-subtext">Hidden in deleted files & past git commits</div>
    <div class="excavation-tag">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <polyline points="12 6 12 12 14 14"/>
      </svg>
      Historical revision recovery
    </div>
  </div>

  <!-- Scan Scope & Stats Card -->
  <div class="metric-card card-stats">
    <div class="card-title">Scan Scope</div>
    <div class="stat-rows">
      <div class="stat-row">
        <span class="stat-name">Total Findings:</span>
        <span class="stat-num">{totalFindings}</span>
      </div>
      <div class="stat-row">
        <span class="stat-name">Commits Scanned:</span>
        <span class="stat-num">{commitsScanned.toLocaleString()}</span>
      </div>
      <div class="stat-row">
        <span class="stat-name">Files Examined:</span>
        <span class="stat-num">{filesScanned.toLocaleString()}</span>
      </div>
      <div class="stat-row">
        <span class="stat-name">Scan Time:</span>
        <span class="stat-num">{formatDuration(scan?.startedAt || scan?.createdAt, scan?.completedAt)}</span>
      </div>
    </div>
  </div>
</div>

<!-- Severity Breakdown Bar -->
<div class="severity-breakdown-card">
  <div class="breakdown-header">
    <span class="breakdown-title">Severity Distribution</span>
    <span class="breakdown-total">{totalFindings} Total Detected</span>
  </div>

  <!-- Visual segmented bar -->
  <div class="segment-bar">
    {#if totalFindings > 0}
      <div
        class="seg seg-critical"
        style="width: {(severityCounts.CRITICAL / totalFindings) * 100}%"
        title="Critical: {severityCounts.CRITICAL}"
      ></div>
      <div
        class="seg seg-high"
        style="width: {(severityCounts.HIGH / totalFindings) * 100}%"
        title="High: {severityCounts.HIGH}"
      ></div>
      <div
        class="seg seg-medium"
        style="width: {(severityCounts.MEDIUM / totalFindings) * 100}%"
        title="Medium: {severityCounts.MEDIUM}"
      ></div>
      <div
        class="seg seg-low"
        style="width: {(severityCounts.LOW / totalFindings) * 100}%"
        title="Low: {severityCounts.LOW}"
      ></div>
      <div
        class="seg seg-info"
        style="width: {(severityCounts.INFO / totalFindings) * 100}%"
        title="Info: {severityCounts.INFO}"
      ></div>
    {:else}
      <div class="seg seg-empty" style="width: 100%"></div>
    {/if}
  </div>

  <!-- Counters badges -->
  <div class="severity-pills">
    <div class="pill pill-critical">
      <span class="dot"></span>
      <span class="label">Critical</span>
      <span class="count">{severityCounts.CRITICAL || 0}</span>
    </div>
    <div class="pill pill-high">
      <span class="dot"></span>
      <span class="label">High</span>
      <span class="count">{severityCounts.HIGH || 0}</span>
    </div>
    <div class="pill pill-medium">
      <span class="dot"></span>
      <span class="label">Medium</span>
      <span class="count">{severityCounts.MEDIUM || 0}</span>
    </div>
    <div class="pill pill-low">
      <span class="dot"></span>
      <span class="label">Low</span>
      <span class="count">{severityCounts.LOW || 0}</span>
    </div>
    <div class="pill pill-info">
      <span class="dot"></span>
      <span class="label">Info</span>
      <span class="count">{severityCounts.INFO || 0}</span>
    </div>
  </div>
</div>

<style>
  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
    gap: 1rem;
    margin-bottom: 1.25rem;
  }

  .metric-card {
    background-color: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    transition: transform 0.2s ease, border-color 0.2s ease;
  }
  .metric-card:hover {
    border-color: var(--border-accent);
  }

  .card-risk {
    align-items: center;
    justify-content: center;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .card-title {
    font-size: 0.75rem;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: var(--text-dim);
    text-transform: uppercase;
  }

  .card-value {
    font-size: 2.25rem;
    font-weight: 800;
    font-family: var(--font-mono);
    margin: 0.5rem 0;
  }

  .card-subtext {
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .card-alive {
    border-left: 3px solid var(--accent-green);
  }
  .alive-value {
    color: var(--accent-green);
    text-shadow: 0 0 12px rgba(0, 255, 136, 0.4);
  }
  .pulse-indicator {
    width: 10px;
    height: 10px;
    background-color: var(--accent-green);
    border-radius: 50%;
    box-shadow: 0 0 8px var(--accent-green);
    animation: blink 1.5s infinite;
  }

  @keyframes blink {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(0.8); }
  }

  .threat-alert {
    margin-top: 0.5rem;
    font-size: 0.7rem;
    color: var(--accent-green);
    font-weight: 600;
  }

  .card-buried {
    border-left: 3px solid var(--text-dim);
  }
  .buried-value {
    color: var(--text-muted);
  }
  .excavation-tag {
    margin-top: 0.5rem;
    font-size: 0.7rem;
    color: var(--text-dim);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
  }

  .stat-rows {
    display: flex;
    flex-direction: column;
    gap: 0.45rem;
    margin-top: 0.75rem;
  }
  .stat-row {
    display: flex;
    justify-content: space-between;
    font-size: 0.8rem;
  }
  .stat-name {
    color: var(--text-muted);
  }
  .stat-num {
    font-family: var(--font-mono);
    font-weight: 600;
    color: var(--text-main);
  }

  /* Severity Breakdown */
  .severity-breakdown-card {
    background-color: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    padding: 1.25rem;
    margin-bottom: 1.5rem;
  }

  .breakdown-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.85rem;
  }
  .breakdown-title {
    font-size: 0.85rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-dim);
  }
  .breakdown-total {
    font-family: var(--font-mono);
    font-size: 0.8rem;
    color: var(--text-muted);
  }

  .segment-bar {
    height: 10px;
    width: 100%;
    background-color: var(--bg-subtle);
    border-radius: var(--radius-full);
    overflow: hidden;
    display: flex;
    margin-bottom: 1rem;
  }
  .seg {
    height: 100%;
    transition: width 0.4s ease;
  }
  .seg-critical { background-color: var(--severity-critical); }
  .seg-high { background-color: var(--severity-high); }
  .seg-medium { background-color: var(--severity-medium); }
  .seg-low { background-color: var(--severity-low); }
  .seg-info { background-color: var(--severity-info); }
  .seg-empty { background-color: var(--bg-subtle); }

  .severity-pills {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .pill {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    padding: 0.3rem 0.65rem;
    border-radius: var(--radius-sm);
    font-size: 0.75rem;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .pill-critical .dot { background: var(--severity-critical); }
  .pill-high .dot { background: var(--severity-high); }
  .pill-medium .dot { background: var(--severity-medium); }
  .pill-low .dot { background: var(--severity-low); }
  .pill-info .dot { background: var(--severity-info); }

  .pill .label {
    color: var(--text-muted);
  }
  .pill .count {
    font-family: var(--font-mono);
    font-weight: 700;
    color: var(--text-main);
  }
</style>
