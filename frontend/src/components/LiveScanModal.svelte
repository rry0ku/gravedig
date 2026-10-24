<script lang="ts">
  import { createEventDispatcher, onDestroy } from 'svelte';
  import { createScanWebSocket, startScan, chooseFolder } from '../api';
  import type { Finding, ScanEvent, ScanRecord } from '../types';

  export let initialPath: string = '.';

  const dispatch = createEventDispatcher<{
    close: void;
    scanCompleted: string; // scanId
  }>();

  let targetPath = initialPath;
  let browsing = false;
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

  async function browseFolder() {
    browsing = true;
    try {
      const res = await chooseFolder(targetPath);
      if (res && res.selected && res.path) {
        targetPath = res.path;
      } else if (res && !res.success && res.message) {
        alert(res.message);
      }
    } catch (e: any) {
      alert('Could not open folder picker: ' + (e?.message || e));
    } finally {
      browsing = false;
    }
  }

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

  $: aliveCount = liveFindings.filter((f) => f.presence === 'ALIVE').length;
  $: buriedCount = liveFindings.filter((f) => f.presence === 'BURIED').length;

  function handleScanEvent(event: ScanEvent) {
    const count = event.findingsCount ?? event.findingsFound ?? 0;
    if (event.type === 'PROGRESS') {
      filesScanned = event.filesScanned ?? filesScanned;
      commitsScanned = event.commitsScanned ?? commitsScanned;
      findingsFound = count;
      if (event.currentFile) currentFile = event.currentFile;
      if (event.riskScore !== undefined && event.riskScore !== null) riskScore = event.riskScore;
    } else if (event.type === 'FINDING' && event.finding) {
      findingsFound = count || (findingsFound + 1);
      liveFindings = [event.finding, ...liveFindings].slice(0, 100);
      if (event.riskScore !== undefined && event.riskScore !== null) {
        riskScore = Math.max(riskScore, event.riskScore);
      }
    } else if (event.type === 'COMPLETED') {
      scanning = false;
      scanCompleted = true;
      filesScanned = event.filesScanned ?? filesScanned;
      commitsScanned = event.commitsScanned ?? commitsScanned;
      findingsFound = count;
      if (event.riskScore !== undefined && event.riskScore !== null) riskScore = event.riskScore;
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
        <button class="close-btn" on:click={() => dispatch('close')} title="Close Modal">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      {/if}
    </div>

    <!-- Body -->
    <div class="modal-body">
      {#if !scanning && !scanCompleted}
        <!-- Configuration Form -->
        <div class="form-group">
          <label for="path-input">Target Repository / Folder Path</label>
          <div class="path-input-group">
            <input
              id="path-input"
              type="text"
              bind:value={targetPath}
              placeholder="e.g. /home/user/project or ."
            />
            <button
              type="button"
              class="browse-btn"
              on:click={browseFolder}
              disabled={browsing}
              title="Select folder via file manager"
            >
              {#if browsing}
                <span class="spinner-inline"></span>
                <span>Selecting...</span>
              {:else}
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                </svg>
                <span>Select Folder</span>
              {/if}
            </button>
          </div>
          <span class="field-hint">Specify a local path or click <strong>Select Folder</strong> to choose via your file manager.</span>
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
          <div class="status-indicator-bar {scanCompleted ? 'completed' : 'running'}">
            <div class="status-left">
              <span class="status-dot {scanning ? 'pulsing' : 'done'}"></span>
              <span class="status-msg">
                {#if scanning}
                  Excavating repository & git commit history...
                {:else if scanCompleted}
                  Excavation completed successfully!
                {/if}
              </span>
            </div>
            {#if scanning && currentFile}
              <div class="current-file-pill" title={currentFile}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
                <span class="file-name">{currentFile}</span>
              </div>
            {/if}
          </div>

          <!-- Counter grid -->
          <div class="live-counters">
            <div class="live-counter">
              <span class="counter-label">Commits Scanned</span>
              <span class="counter-val">{commitsScanned.toLocaleString()}</span>
            </div>

            <div class="live-counter">
              <span class="counter-label">Files Examined</span>
              <span class="counter-val">{filesScanned.toLocaleString()}</span>
            </div>

            <div class="live-counter {findingsFound > 0 ? 'danger' : ''}">
              <span class="counter-label">Secrets Detected</span>
              <div class="counter-val-row">
                <span class="counter-val">{findingsFound}</span>
                {#if findingsFound > 0}
                  <div class="presence-pills">
                    {#if aliveCount > 0}
                      <span class="pill-alive">{aliveCount} ALIVE</span>
                    {/if}
                    {#if buriedCount > 0}
                      <span class="pill-buried">{buriedCount} BURIED</span>
                    {/if}
                  </div>
                {/if}
              </div>
            </div>

            <div class="live-counter {riskScore >= 70 ? 'danger' : riskScore >= 40 ? 'warn' : ''}">
              <span class="counter-label">Max Risk Score</span>
              <span class="counter-val">{Math.round(riskScore)}<span class="scale">/100</span></span>
            </div>
          </div>

          <!-- Live Findings Feed -->
          <div class="live-stream-box">
            <div class="stream-header">
              <div class="stream-header-title">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
                </svg>
                <span>Live Excavation Feed</span>
                <span class="stream-count-tag">{findingsFound} Leaked Secret{findingsFound === 1 ? '' : 's'}</span>
              </div>
              {#if scanning}
                <span class="stream-live-tag">● LIVE STREAM</span>
              {/if}
            </div>

            <div class="stream-list">
              {#if liveFindings.length === 0}
                <div class="stream-empty">
                  {#if scanning}
                    <div class="pulse-ring"></div>
                    <span>Traversing tree and diffing commit revisions...</span>
                  {:else}
                    <div class="clean-check">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    </div>
                    <span>No leaked credentials detected. The graveyard is quiet.</span>
                  {/if}
                </div>
              {:else}
                {#each liveFindings as f (f.id)}
                  <div class="stream-item sev-{f.severity.toLowerCase()}">
                    <div class="stream-col-badges">
                      <span class="badge badge-{f.severity.toLowerCase()}">{f.severity}</span>
                      <span class="badge badge-{f.presence.toLowerCase()}">
                        {#if f.presence === 'ALIVE'}
                          <svg class="badge-icon-svg" width="8" height="8" viewBox="0 0 8 8" fill="currentColor">
                            <circle cx="4" cy="4" r="3"/>
                          </svg>
                          ALIVE
                        {:else}
                          <svg class="badge-icon-svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="12" cy="12" r="4"/>
                            <line x1="2" y1="12" x2="8" y2="12"/>
                            <line x1="16" y1="12" x2="22" y2="12"/>
                          </svg>
                          BURIED
                        {/if}
                      </span>
                    </div>

                    <div class="stream-col-rule">
                      <span class="stream-rule-name">{f.ruleName}</span>
                      {#if f.secretMasked || f.secretRedacted}
                        <code class="stream-secret-preview">{f.secretMasked || f.secretRedacted}</code>
                      {/if}
                    </div>

                    <div class="stream-col-location" title="{f.filePath}:{f.lineNumber}">
                      <span class="stream-file-path">{f.filePath}</span>
                      <span class="stream-line-badge">L{f.lineNumber}</span>
                    </div>
                  </div>
                {/each}
              {/if}
            </div>
          </div>

          {#if scanCompleted}
            <div class="completed-actions">
              <button class="ghost-btn" on:click={() => { scanning = false; scanCompleted = false; }}>
                ← Configure Another Scan
              </button>
              <button class="primary inspect-full-btn" on:click={finishAndInspect}>
                Inspect Scan Results in Dashboard →
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
    max-width: 860px;
    width: 95%;
    padding: 1.75rem;
    background: var(--bg-surface);
    border: 1px solid var(--border-accent);
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.7);
    border-radius: var(--radius-lg);
  }

  .modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1.25rem;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .title-with-icon {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }
  .title-with-icon h3 {
    font-size: 1.2rem;
    font-weight: 700;
    color: var(--text-main);
  }

  .close-btn {
    background: transparent;
    border: none;
    font-size: 1.25rem;
    color: var(--text-dim);
    cursor: pointer;
    padding: 0.25rem 0.5rem;
    border-radius: var(--radius-sm);
    transition: color 0.15s, background 0.15s;
  }
  .close-btn:hover {
    color: var(--text-main);
    background: var(--bg-card);
  }

  /* Form styling */
  .form-group {
    margin-bottom: 1.25rem;
  }
  .form-group label {
    display: block;
    font-size: 0.8rem;
    font-weight: 600;
    margin-bottom: 0.45rem;
    color: var(--text-muted);
  }

  .path-input-group {
    display: flex;
    gap: 0.5rem;
    align-items: center;
  }

  .path-input-group input {
    flex: 1;
    font-family: var(--font-mono);
  }

  .browse-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.45rem;
    background: var(--bg-card);
    border: 1px solid var(--border-accent);
    color: var(--text-main);
    padding: 0.65rem 1rem;
    border-radius: var(--radius-sm);
    font-size: 0.85rem;
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
    transition: all var(--transition-fast);
  }

  .browse-btn:hover:not(:disabled) {
    background: var(--bg-card-hover);
    border-color: var(--accent-green);
    color: var(--accent-green);
  }

  .browse-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .spinner-inline {
    width: 14px;
    height: 14px;
    border: 2px solid var(--border-color);
    border-top-color: var(--accent-green);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
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
    gap: 0.5rem;
    background: #0d121c;
    border: 1px solid var(--border-subtle);
    padding: 0.75rem;
    border-radius: var(--radius-md);
    margin-bottom: 1.25rem;
  }

  .checkbox-label {
    display: flex;
    align-items: flex-start;
    gap: 0.85rem;
    cursor: pointer;
    padding: 0.65rem 0.85rem;
    border-radius: var(--radius-sm);
    border: 1px solid transparent;
    transition: background 0.15s ease, border-color 0.15s ease;
  }
  .checkbox-label:hover {
    background: #131926;
    border-color: #28354f;
  }
  .checkbox-label input {
    margin-top: 0.15rem;
  }
  .checkbox-text {
    display: flex;
    flex-direction: column;
    user-select: none;
  }
  .checkbox-text strong {
    font-size: 0.85rem;
    color: var(--text-main);
    font-weight: 600;
  }
  .checkbox-text span {
    font-size: 0.75rem;
    color: var(--text-dim);
    margin-top: 0.15rem;
    line-height: 1.35;
  }

  .error-banner {
    background: var(--severity-critical-bg);
    border: 1px solid var(--severity-critical);
    color: var(--severity-critical);
    padding: 0.75rem 1rem;
    border-radius: var(--radius-md);
    font-size: 0.82rem;
    margin-bottom: 1.25rem;
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.75rem;
  }

  /* Progress view */
  .status-indicator-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.75rem 1rem;
    border-radius: var(--radius-md);
    margin-bottom: 1.25rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
  }
  .status-indicator-bar.completed {
    border-color: rgba(0, 255, 136, 0.3);
    background: rgba(0, 255, 136, 0.05);
  }

  .status-left {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }

  .status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--accent-green);
    display: inline-block;
  }
  .status-dot.pulsing {
    box-shadow: 0 0 10px var(--accent-green);
    animation: blink 1.2s infinite;
  }
  .status-dot.done {
    background: var(--accent-green);
    box-shadow: 0 0 8px var(--accent-green);
  }

  @keyframes blink {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(0.85); }
  }

  .status-msg {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-main);
  }

  .current-file-pill {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    background: var(--bg-main);
    padding: 0.25rem 0.65rem;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-subtle);
    max-width: 320px;
    font-size: 0.75rem;
    font-family: var(--font-mono);
  }
  .file-name {
    color: var(--accent-cyan);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* Counter Grid */
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
    padding: 0.85rem 1rem;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  .counter-label {
    display: block;
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--text-dim);
    margin-bottom: 0.35rem;
  }

  .counter-val {
    font-family: var(--font-mono);
    font-size: 1.4rem;
    font-weight: 800;
    color: var(--text-main);
  }

  .counter-val-row {
    display: flex;
    align-items: baseline;
    gap: 0.65rem;
    flex-wrap: wrap;
  }

  .live-counter.danger .counter-val {
    color: var(--severity-critical);
  }
  .live-counter.warn .counter-val {
    color: var(--severity-high);
  }

  .scale {
    font-size: 0.8rem;
    color: var(--text-dim);
    font-weight: 500;
  }

  .presence-pills {
    display: flex;
    gap: 0.3rem;
    align-items: center;
  }
  .pill-alive {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 0.1rem 0.35rem;
    border-radius: 3px;
    background: var(--presence-alive-bg);
    color: var(--presence-alive);
    border: 1px solid var(--presence-alive);
  }
  .pill-buried {
    font-size: 0.65rem;
    font-weight: 700;
    padding: 0.1rem 0.35rem;
    border-radius: 3px;
    background: var(--presence-buried-bg);
    color: var(--text-muted);
    border: 1px solid var(--border-subtle);
  }

  /* Live Stream Box */
  .live-stream-box {
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    overflow: hidden;
    margin-bottom: 1.25rem;
  }

  .stream-header {
    background: var(--bg-surface);
    padding: 0.65rem 1rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid var(--border-subtle);
  }

  .stream-header-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--text-main);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .stream-count-tag {
    font-size: 0.7rem;
    font-weight: 600;
    background: var(--bg-main);
    color: var(--text-muted);
    padding: 0.15rem 0.45rem;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-subtle);
  }

  .stream-live-tag {
    font-size: 0.7rem;
    font-weight: 700;
    color: var(--accent-green);
    letter-spacing: 0.05em;
  }

  .stream-list {
    max-height: 280px;
    overflow-y: auto;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .stream-empty {
    font-size: 0.825rem;
    color: var(--text-dim);
    text-align: center;
    padding: 2.5rem 1rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.65rem;
  }
  .clean-check {
    font-size: 1.75rem;
    color: var(--accent-green);
  }

  /* Stream Item Row (Seamless, Unboxed) */
  .stream-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.65rem 1rem;
    background: transparent;
    border-radius: 0;
    border: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    gap: 0.85rem;
    transition: background-color 0.15s ease;
  }
  .stream-item:last-child {
    border-bottom: none;
  }
  .stream-item:hover {
    background: rgba(255, 255, 255, 0.025);
  }

  .stream-col-badges {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    flex-shrink: 0;
  }

  .stream-col-rule {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    flex: 1;
    min-width: 0;
  }

  .stream-rule-name {
    font-weight: 700;
    font-size: 0.825rem;
    color: var(--text-main);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .stream-secret-preview {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    padding: 0.1rem 0.4rem;
    border-radius: 3px;
    color: var(--accent-cyan);
    white-space: nowrap;
  }

  .stream-col-location {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    flex-shrink: 0;
    max-width: 320px;
    justify-content: flex-end;
  }

  .stream-file-path {
    font-family: var(--font-mono);
    color: var(--text-dim);
    font-size: 0.75rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    direction: rtl;
    text-align: left;
  }

  .stream-line-badge {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    color: var(--text-muted);
    background: var(--bg-main);
    padding: 0.1rem 0.35rem;
    border-radius: 3px;
    border: 1px solid var(--border-subtle);
    white-space: nowrap;
  }

  /* Completed Actions */
  .completed-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 1rem;
    margin-top: 0.5rem;
  }

  .ghost-btn {
    background: transparent;
    border: 1px solid var(--border-subtle);
    color: var(--text-muted);
    padding: 0.75rem 1.25rem;
    border-radius: var(--radius-md);
    font-weight: 600;
    font-size: 0.85rem;
    cursor: pointer;
    transition: all var(--transition-fast);
  }
  .ghost-btn:hover {
    background: var(--bg-card);
    border-color: var(--border-accent);
    color: var(--text-main);
  }

  .inspect-full-btn {
    flex: 1;
    max-width: 360px;
    background: linear-gradient(135deg, #00ff88, #00d270);
    color: #07090e;
    font-weight: 700;
    font-size: 0.95rem;
    padding: 0.85rem 1.5rem;
    border-radius: var(--radius-md);
    border: none;
    box-shadow: 0 0 20px rgba(0, 255, 136, 0.35);
    cursor: pointer;
    display: inline-flex;
    justify-content: center;
    align-items: center;
    transition: all 0.2s;
  }
  .inspect-full-btn:hover {
    transform: translateY(-1px);
    box-shadow: 0 0 30px rgba(0, 255, 136, 0.55);
  }
</style>
