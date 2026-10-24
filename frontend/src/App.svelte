<script lang="ts">
  import { onMount } from 'svelte';
  import Header from './components/Header.svelte';
  import MetricsOverview from './components/MetricsOverview.svelte';
  import FindingsTable from './components/FindingsTable.svelte';
  import FindingDetailModal from './components/FindingDetailModal.svelte';
  import LiveScanModal from './components/LiveScanModal.svelte';
  import RuleSandbox from './components/RuleSandbox.svelte';
  import ExportBar from './components/ExportBar.svelte';
  import { clearAllScans, deleteScan, getFindings, getScan, listScans } from './api';
  import type { Finding, FindingFilter, ScanRecord } from './types';

  let activeTab: 'dashboard' | 'findings' | 'rules' = 'dashboard';
  let scans: ScanRecord[] = [];
  let selectedScan: ScanRecord | null = null;
  let findings: Finding[] = [];
  let loadingFindings = false;

  let showScanModal = false;
  let inspectingFinding: Finding | null = null;

  let aliveCount = 0;
  let buriedCount = 0;
  let severityCounts: Record<string, number> = {
    CRITICAL: 0,
    HIGH: 0,
    MEDIUM: 0,
    LOW: 0,
    INFO: 0
  };

  onMount(async () => {
    await refreshScans();
  });

  async function refreshScans(selectScanId?: string) {
    try {
      scans = await listScans();
      if (scans.length > 0) {
        if (selectScanId) {
          const found = scans.find((s) => s.id === selectScanId);
          selectedScan = found || scans[0];
        } else if (!selectedScan) {
          selectedScan = scans[0];
        } else {
          const found = scans.find((s) => s.id === selectedScan!.id);
          selectedScan = found || scans[0];
        }
        await loadFindingsForScan(selectedScan.id);
      } else {
        selectedScan = null;
        findings = [];
      }
    } catch (err: any) {
      console.error('Error listing scans:', err);
    }
  }

  async function loadFindingsForScan(scanId: string, filter?: FindingFilter) {
    loadingFindings = true;
    try {
      findings = await getFindings(scanId, filter);
      calculateStats(findings);
    } catch (err: any) {
      console.error('Failed to load findings:', err);
    } finally {
      loadingFindings = false;
    }
  }

  function calculateStats(items: Finding[]) {
    aliveCount = items.filter((f) => f.presence === 'ALIVE').length;
    buriedCount = items.filter((f) => f.presence === 'BURIED').length;
    const counts: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
    for (const f of items) {
      if (counts[f.severity] !== undefined) {
        counts[f.severity]++;
      }
    }
    severityCounts = counts;
  }

  async function onScanSelect(scanId: string) {
    const s = scans.find((sc) => sc.id === scanId);
    if (s) {
      selectedScan = s;
      await loadFindingsForScan(s.id);
    }
  }

  function handleScanCompleted(event: CustomEvent<string>) {
    showScanModal = false;
    refreshScans(event.detail);
    activeTab = 'dashboard';
  }

  function handleFindingUpdated(event: CustomEvent<Finding>) {
    const updated = event.detail;
    inspectingFinding = updated;
    findings = findings.map((f) => (f.id === updated.id ? updated : f));
  }

  function formatScanDate(d?: string): string {
    if (!d) return 'Recent Scan';
    const date = new Date(d);
    return isNaN(date.getTime()) ? 'Recent Scan' : date.toLocaleString();
  }

  async function handleClearCurrentScan() {
    if (!selectedScan) return;
    const confirmClear = window.confirm(`Clear scan for "${selectedScan.targetPath}" and remove its findings from the dashboard?`);
    if (!confirmClear) return;
    try {
      await deleteScan(selectedScan.id);
      selectedScan = null;
      await refreshScans();
    } catch (err: any) {
      alert(`Failed to clear scan: ${err.message}`);
    }
  }

  async function handleClearAllScans() {
    const confirmClear = window.confirm('Clear all scans and findings from the dashboard?');
    if (!confirmClear) return;
    try {
      await clearAllScans();
      selectedScan = null;
      await refreshScans();
    } catch (err: any) {
      alert(`Failed to clear all scans: ${err.message}`);
    }
  }
</script>

<div class="app-layout">
  <Header
    {activeTab}
    targetPath={selectedScan?.targetPath || ''}
    on:tabChange={(e) => activeTab = e.detail}
    on:openScan={() => showScanModal = true}
  />

  <main class="main-content">
    <!-- Scan selector bar if multiple scans exist -->
    {#if scans.length > 0}
      <div class="scan-bar">
        <div class="scan-selector">
          <label for="scan-select">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            Active Scan:
          </label>
          <select
            id="scan-select"
            value={selectedScan?.id}
            on:change={(e) => onScanSelect(e.currentTarget.value)}
          >
            {#each scans as s (s.id)}
              <option value={s.id}>
                [{s.status}] {s.targetPath} — {formatScanDate(s.startedAt || s.createdAt || s.completedAt)} ({s.totalFindings} findings)
              </option>
            {/each}
          </select>
        </div>

        {#if selectedScan}
          <div class="scan-actions-group">
            <button
              class="clear-scan-btn"
              on:click={handleClearCurrentScan}
              title="Clear this scanned directory and its findings from the dashboard"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                <line x1="10" y1="11" x2="10" y2="17"/>
                <line x1="14" y1="11" x2="14" y2="17"/>
              </svg>
              Clear Current Scan
            </button>
            {#if scans.length > 1}
              <button
                class="clear-all-btn"
                on:click={handleClearAllScans}
                title="Clear all scans and findings from the dashboard"
              >
                Clear All
              </button>
            {/if}
            <ExportBar scanId={selectedScan.id} />
          </div>
        {/if}
      </div>
    {/if}

    <!-- Content Tabs -->
    {#if activeTab === 'dashboard'}
      {#if !selectedScan}
        <div class="empty-state-hero">
          <div class="hero-icon">
            <svg width="64" height="64" viewBox="0 0 32 32" fill="none">
              <path d="M7 28H25V13C25 8.02944 20.9706 4 16 4C11.0294 4 7 8.02944 7 13V28Z" fill="#181d2c" stroke="#00ff88" stroke-width="2"/>
              <path d="M16 10V18M12 14H20" stroke="#00ff88" stroke-width="2" stroke-linecap="round"/>
              <path d="M4 28H28" stroke="#64748b" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <h2>Ready To Dig For Leaked Secrets</h2>
          <p class="hero-tagline">"Dig up the secrets your git history forgot to bury."</p>
          <p class="hero-desc">Launch a scan on your local workspace or git repository to detect API keys, tokens, and private keys.</p>
          <button class="primary hero-btn" on:click={() => showScanModal = true}>
            Start New Scan
          </button>
        </div>
      {:else}
        <MetricsOverview
          scan={selectedScan}
          {aliveCount}
          {buriedCount}
          {severityCounts}
        />

        <div class="section-heading">
          <h3>Detected Secrets Preview</h3>
          <button class="link-btn" on:click={() => activeTab = 'findings'}>
            View All ({findings.length}) Findings →
          </button>
        </div>

        <FindingsTable
          {findings}
          loading={loadingFindings}
          on:selectFinding={(e) => inspectingFinding = e.detail}
        />
      {/if}

    {:else if activeTab === 'findings'}
      {#if selectedScan}
        <FindingsTable
          {findings}
          loading={loadingFindings}
          on:selectFinding={(e) => inspectingFinding = e.detail}
        />
      {:else}
        <div class="empty-state-hero">
          <p>No scan available. Please run a scan first.</p>
          <button class="primary" on:click={() => showScanModal = true}>New Scan</button>
        </div>
      {/if}

    {:else if activeTab === 'rules'}
      <RuleSandbox />
    {/if}
  </main>

  <!-- Modals -->
  {#if showScanModal}
    <LiveScanModal
      initialPath={selectedScan?.targetPath || '.'}
      on:close={() => showScanModal = false}
      on:scanCompleted={handleScanCompleted}
    />
  {/if}

  {#if inspectingFinding}
    <FindingDetailModal
      finding={inspectingFinding}
      on:close={() => inspectingFinding = null}
      on:updated={handleFindingUpdated}
    />
  {/if}
</div>

<style>
  .app-layout {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  .main-content {
    flex: 1;
    max-width: 1300px;
    width: 100%;
    margin: 0 auto;
    padding: 1.5rem;
  }

  .scan-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.5rem;
  }

  .scan-selector {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    font-size: 0.825rem;
  }
  .scan-selector label {
    font-weight: 600;
    color: var(--text-dim);
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
  }
  .scan-selector select {
    font-family: var(--font-mono);
    max-width: 520px;
    font-size: 0.825rem;
  }

  .scan-actions-group {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    flex-wrap: wrap;
  }

  .clear-scan-btn {
    font-size: 0.75rem;
    padding: 0.42rem 0.75rem;
    background: transparent;
    border: 1px solid rgba(244, 63, 94, 0.35);
    color: var(--severity-critical);
    border-radius: var(--radius-sm);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    cursor: pointer;
    font-weight: 500;
    transition: all 0.15s ease;
  }
  .clear-scan-btn:hover {
    background: rgba(244, 63, 94, 0.12);
    border-color: var(--severity-critical);
    color: #fff;
  }

  .clear-all-btn {
    font-size: 0.75rem;
    padding: 0.42rem 0.65rem;
    background: transparent;
    border: 1px solid var(--border-subtle);
    color: var(--text-dim);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .clear-all-btn:hover {
    border-color: var(--severity-critical);
    color: var(--severity-critical);
  }

  .section-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 1.5rem 0 0.85rem;
  }
  .section-heading h3 {
    font-size: 1rem;
    font-weight: 700;
    color: var(--text-main);
  }
  .link-btn {
    background: transparent;
    border: none;
    color: var(--accent-cyan);
    font-size: 0.8rem;
    cursor: pointer;
  }
  .link-btn:hover {
    text-decoration: underline;
  }

  .empty-state-hero {
    text-align: center;
    padding: 4rem 1.5rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    margin-top: 1rem;
  }
  .hero-icon {
    margin-bottom: 1rem;
  }
  .empty-state-hero h2 {
    font-size: 1.5rem;
    font-weight: 800;
    margin-bottom: 0.35rem;
  }
  .hero-tagline {
    font-size: 0.95rem;
    color: var(--accent-green);
    font-style: italic;
    margin-bottom: 0.75rem;
  }
  .hero-desc {
    font-size: 0.85rem;
    color: var(--text-muted);
    max-width: 500px;
    margin: 0 auto 1.5rem;
  }
  .hero-btn {
    font-size: 0.95rem;
    padding: 0.65rem 1.5rem;
  }
</style>
