<script lang="ts">
  import { createEventDispatcher, onDestroy } from 'svelte';
  import { createScanWebSocket, startScan } from '../api';
  import type { Finding, ScanEvent, ScanRecord } from '../types';

  export let initialPath: string = '.';

  const dispatch = createEventDispatcher<{
    close: void;
    scanCompleted: string; // scanId
  }>();

  let targetPath = initialPath;
  let includeHistory = true;
  let entropyCheck = true;
  let offlineValidation = true;

  let activeScanId = '';
  let scanning = false;
  let scanCompleted = false;
  let scanError = '';

  let filesScanned = 0;
  let commitsScanned = 0;
  let findingsFound = 0;
  let riskScore = 0;
  let currentFile = '';
  let liveFindings: Finding[] = [];

  let ws: WebSocket | null = null;

  onDestroy(() => {
    if (ws) {
      ws.close();
      ws = null;
    }
  });

  async function launchScan() {
    scanning = true;
    scanCompleted = false;
    scanError = '';
    liveFindings = [];
    filesScanned = 0;
    commitsScanned = 0;
    findingsFound = 0;
    riskScore = 0;

    try {
      const record = await startScan({
        targetPath: targetPath.trim() || '.',
        includeHistory,
        entropyCheck,
        offlineValidation
      });

      activeScanId = record.id;

      // Connect WebSocket for real-time progress
      ws = createScanWebSocket(
        record.id,
        (event: ScanEvent) => {
          handleScanEvent(event);
        },
        () => {
          // onClose
        },
        (err) => {
          console.error('WS Error:', err);
        }
      );
    } catch (err: any) {
      scanning = false;
      scanError = err.message || 'Failed to start scan';
    }
  }

  function handleScanEvent(event: ScanEvent) {
    if (event.type === 'PROGRESS') {
      filesScanned = event.filesScanned;
      commitsScanned = event.commitsScanned;
      findingsFound = event.findingsFound;
      if (event.currentFile) currentFile = event.currentFile;
      if (event.riskScore !== undefined) riskScore = event.riskScore;
    } else if (event.type === 'FINDING' && event.finding) {
      findingsFound = event.findingsFound;
      liveFindings = [event.finding, ...liveFindings].slice(0, 50);
      if (event.riskScore !== undefined) riskScore = event.riskScore;
    } else if (event.type === 'COMPLETED') {
      scanning = false;
      scanCompleted = true;
      filesScanned = event.filesScanned;
      commitsScanned = event.commitsScanned;
      findingsFound = event.findingsFound;
      if (event.riskScore !== undefined) riskScore = event.riskScore;
    } else if (event.type === 'ERROR') {
      scanning = false;
      scanError = event.message || 'Scan failed';
    }
  }

  function finishAndInspect() {
    if (ws) {
      ws.close();
      ws = null;
    }
    dispatch('scanCompleted', activeScanId);
  }
</script>

<div class="modal-backdrop" on:click|self={() => !scanning && dispatch('close')} on:keydown|self={(e) => e.key === 'Escape' && !scanning && dispatch('close')} role="presentation">
  <div class="modal-content scan-modal">
    <!-- Header -->
    <div class="modal-header">
      <div class="title-with-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--accent-green)" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <h3>{scanning ? 'Excavating Git History...' : scanCompleted ? 'Scan Finished' : 'Configure New Scan'}</h3>
      </div>
      {#if !scanning}
        <button class="close-btn" on:click={() => dispatch('close')}>✕</button>
      {/if}
    </div>

    <!-- Body -->
    <div class="modal-body">
      {#if !scanning && !scanCompleted}
        <!-- Configuration Form -->
        <div class="form-group">
          <label for="path-input">Target Repository / Folder Path</label>
          <input
            id="path-input"
            type="text"
            bind:value={targetPath}
            placeholder="e.g. /home/user/project or ."
          />
          <span class="field-hint">Specify an absolute or relative path to a local folder or git repository.</span>
        </div>

        <div class="options-group">
          <label class="checkbox-label">
            <input type="checkbox" bind:checked={includeHistory} />
            <div class="checkbox-text">
              <strong>Scan Full Git History (Recommended)</strong>
              <span>Traverses all commit history to dig up buried credentials that were previously deleted.</span>
            </div>
          </label>

          <label class="checkbox-label">
            <input type="checkbox" bind:checked={entropyCheck} />
            <div class="checkbox-text">
              <strong>Shannon Entropy Filtering</strong>
              <span>Applies entropy thresholds to verify high-randomness tokens and minimize false positives.</span>
            </div>
          </label>

          <label class="checkbox-label">
            <input type="checkbox" bind:checked={offlineValidation} />
            <div class="checkbox-text">
              <strong>Air-Gapped / Offline Verification</strong>
              <span>Zero external network queries; completely safe for local auditing.</span>
            </div>
          </label>
        </div>

        {#if scanError}
          <div class="error-banner">{scanError}</div>
        {/if}

        <div class="form-actions">
          <button on:click={() => dispatch('close')}>Cancel</button>
          <button class="primary" on:click={launchScan}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
            Dig For Secrets
          </button>
        </div>
      {:else}
        <!-- Real-time Progress View -->
        <div class="progress-section">
          <div class="status-indicator-bar">
            <div class="pulse-ring"></div>
            <span class="status-msg">
              {#if scanning}
                Active Scan in progress...
              {:else if scanCompleted}
                Excavation completed successfully!
              {/if}
            </span>
          </div>

          <!-- Counter grid -->
          <div class="live-counters">
            <div class="live-counter">
              <span class="counter-label">Commits</span>
              <span class="counter-val">{commitsScanned.toLocaleString()}</span>
            </div>
            <div class="live-counter">
              <span class="counter-label">Files</span>
              <span class="counter-val">{filesScanned.toLocaleString()}</span>
            </div>
            <div class="live-counter danger">
              <span class="counter-label">Secrets Buried / Alive</span>
              <span class="counter-val">{findingsFound}</span>
            </div>
            <div class="live-counter">
              <span class="counter-label">Risk Score</span>
              <span class="counter-val">{Math.round(riskScore)}/100</span>
            </div>
          </div>

          <!-- Current file ticker -->
          {#if scanning && currentFile}
            <div class="ticker-box">
              <span class="ticker-label">Scanning:</span>
              <span class="ticker-file">{currentFile}</span>
            </div>
          {/if}

          <!-- Live Findings Feed -->
          <div class="live-stream-box">
            <div class="stream-header">Live Findings Feed ({liveFindings.length} recent)</div>
            <div class="stream-list">
              {#if liveFindings.length === 0}
                <div class="stream-empty">No secrets detected yet...</div>
              {:else}
                {#each liveFindings as f (f.id)}
                  <div class="stream-item">
                    <span class="badge badge-{f.severity.toLowerCase()}">{f.severity}</span>
                    <span class="badge badge-{f.presence.toLowerCase()}">{f.presence}</span>
                    <span class="stream-rule">{f.ruleName}</span>
                    <span class="stream-path">{f.filePath}:{f.lineNumber}</span>
                  </div>
                {/each}
              {/if}
            </div>
          </div>

          {#if scanCompleted}
            <div class="completed-actions">
              <button class="primary inspect-full-btn" on:click={finishAndInspect}>
                Inspect Scan Results →
              </button>
            </div>
          {/if}
        </div>
      {/if}
    </div>
  </div>
</div>

<style>
  .scan-modal {
    max-width: 640px;
    padding: 1.75rem;
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.25rem;
  }

  .title-with-icon {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }
  .title-with-icon h3 {
    font-size: 1.15rem;
    font-weight: 700;
  }

  .close-btn {
    background: transparent;
    border: none;
    font-size: 1.2rem;
    color: var(--text-dim);
  }

  .form-group {
    margin-bottom: 1.25rem;
  }
  .form-group label {
    display: block;
    font-size: 0.8rem;
    font-weight: 600;
    margin-bottom: 0.4rem;
    color: var(--text-muted);
  }
  .form-group input {
    width: 100%;
    font-family: var(--font-mono);
  }
  .field-hint {
    display: block;
    font-size: 0.75rem;
    color: var(--text-dim);
    margin-top: 0.35rem;
  }

  .options-group {
    display: flex;
    flex-direction: column;
    gap: 0.85rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    padding: 1rem;
    border-radius: var(--radius-md);
    margin-bottom: 1.25rem;
  }

  .checkbox-label {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    cursor: pointer;
  }
  .checkbox-label input {
    margin-top: 0.2rem;
  }
  .checkbox-text {
    display: flex;
    flex-direction: column;
  }
  .checkbox-text strong {
    font-size: 0.825rem;
    color: var(--text-main);
  }
  .checkbox-text span {
    font-size: 0.75rem;
    color: var(--text-dim);
  }

  .error-banner {
    background: var(--severity-critical-bg);
    border: 1px solid var(--severity-critical);
    color: var(--severity-critical);
    padding: 0.65rem 1rem;
    border-radius: var(--radius-md);
    font-size: 0.8rem;
    margin-bottom: 1.25rem;
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.75rem;
  }

  /* Real-time Progress */
  .status-indicator-bar {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    margin-bottom: 1.25rem;
  }
  .pulse-ring {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--accent-green);
    box-shadow: 0 0 10px var(--accent-green);
    animation: blink 1.2s infinite;
  }
  .status-msg {
    font-size: 0.85rem;
    font-weight: 600;
    color: var(--accent-green);
  }

  .live-counters {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 0.75rem;
    margin-bottom: 1.25rem;
  }
  .live-counter {
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 0.75rem;
    text-align: center;
  }
  .counter-label {
    display: block;
    font-size: 0.65rem;
    text-transform: uppercase;
    color: var(--text-dim);
    margin-bottom: 0.25rem;
  }
  .counter-val {
    font-family: var(--font-mono);
    font-size: 1.25rem;
    font-weight: 800;
  }
  .live-counter.danger .counter-val {
    color: var(--severity-critical);
  }

  .ticker-box {
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    padding: 0.45rem 0.75rem;
    font-size: 0.75rem;
    font-family: var(--font-mono);
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 1.25rem;
    overflow: hidden;
  }
  .ticker-label {
    color: var(--text-dim);
  }
  .ticker-file {
    color: var(--accent-cyan);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .live-stream-box {
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    overflow: hidden;
    margin-bottom: 1.25rem;
  }
  .stream-header {
    background: var(--bg-surface);
    padding: 0.5rem 0.85rem;
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    color: var(--text-dim);
    border-bottom: 1px solid var(--border-subtle);
  }
  .stream-list {
    max-height: 180px;
    overflow-y: auto;
    padding: 0.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }
  .stream-empty {
    font-size: 0.75rem;
    color: var(--text-dim);
    text-align: center;
    padding: 1rem;
  }
  .stream-item {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    font-size: 0.75rem;
    padding: 0.25rem 0.5rem;
    border-radius: var(--radius-sm);
    background: var(--bg-main);
  }
  .stream-rule {
    font-weight: 600;
    color: var(--text-main);
  }
  .stream-path {
    font-family: var(--font-mono);
    color: var(--text-dim);
    margin-left: auto;
    font-size: 0.7rem;
  }

  .completed-actions {
    display: flex;
    justify-content: flex-end;
  }
  .inspect-full-btn {
    width: 100%;
    justify-content: center;
    font-size: 0.9rem;
    padding: 0.65rem;
  }
</style>
