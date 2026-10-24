<script lang="ts">
  import { saveExport, openFileOnSystem, type ExportSaveResult } from '../api';

  export let scanId: string = '';
  export let disabled: boolean = false;

  type ExportFormat = 'sarif' | 'html' | 'json' | 'csv';

  let exportingFormat: ExportFormat | null = null;
  let exportResult: ExportSaveResult | null = null;
  let errorMessage: string | null = null;
  let systemStatusMessage: string | null = null;
  let copiedType: 'path' | 'content' | null = null;
  let isOpening: boolean = false;
  let dismissTimer: any = null;

  const FORMAT_LABELS: Record<ExportFormat, string> = {
    sarif: 'SARIF 2.1.0',
    html: 'HTML Report',
    json: 'JSON',
    csv: 'CSV'
  };

  const MIME_TYPES: Record<ExportFormat, string> = {
    sarif: 'application/sarif+json',
    html: 'text/html; charset=utf-8',
    json: 'application/json; charset=utf-8',
    csv: 'text/csv; charset=utf-8'
  };

  function formatBytes(bytes: number): string {
    if (!bytes || bytes <= 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    const kb = bytes / 1024;
    if (kb < 1024) return kb.toFixed(1) + ' KB';
    return (kb / 1024).toFixed(2) + ' MB';
  }

  function scheduleAutoDismiss() {
    if (dismissTimer) clearTimeout(dismissTimer);
    dismissTimer = setTimeout(() => {
      exportResult = null;
      errorMessage = null;
    }, 12000);
  }

  function cancelAutoDismiss() {
    if (dismissTimer) clearTimeout(dismissTimer);
  }

  async function handleExport(format: ExportFormat) {
    if (!scanId || disabled || exportingFormat) return;

    exportingFormat = format;
    errorMessage = null;
    systemStatusMessage = null;

    try {
      const res = await saveExport(scanId, format);
      exportResult = res;

      // Also trigger browser file download fallback
      try {
        const mime = MIME_TYPES[format] || 'text/plain';
        const blob = new Blob([res.content], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = res.filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } catch (dlErr) {
        // Ignored if headless/embedded environment restricts blob downloads
      }

      scheduleAutoDismiss();
    } catch (err: any) {
      errorMessage = err?.message || 'Failed to generate export report';
      scheduleAutoDismiss();
    } finally {
      exportingFormat = null;
    }
  }

  async function handleOpenFile() {
    if (!exportResult?.filePath || isOpening) return;
    isOpening = true;
    systemStatusMessage = null;
    try {
      const res = await openFileOnSystem(exportResult.filePath);
      if (res.success) {
        systemStatusMessage = 'Opened file with default application';
      } else {
        systemStatusMessage = res.message || 'System viewer unavailable';
      }
    } catch (err: any) {
      systemStatusMessage = 'Could not open file: ' + (err?.message || 'Unknown error');
    } finally {
      isOpening = false;
      setTimeout(() => { systemStatusMessage = null; }, 4000);
    }
  }

  async function handleCopyPath() {
    if (!exportResult?.filePath) return;
    try {
      await navigator.clipboard.writeText(exportResult.filePath);
      copiedType = 'path';
      setTimeout(() => { copiedType = null; }, 2000);
    } catch (e) {
      systemStatusMessage = 'Clipboard access denied';
    }
  }

  async function handleCopyContent() {
    if (!exportResult?.content) return;
    try {
      await navigator.clipboard.writeText(exportResult.content);
      copiedType = 'content';
      setTimeout(() => { copiedType = null; }, 2000);
    } catch (e) {
      systemStatusMessage = 'Clipboard access denied';
    }
  }

  function dismiss() {
    if (dismissTimer) clearTimeout(dismissTimer);
    exportResult = null;
    errorMessage = null;
    systemStatusMessage = null;
  }
</script>

<div class="export-container">
  <div class="export-bar">
    <span class="export-label">Export Scan:</span>
    <div class="export-buttons">
      <button
        class="export-btn"
        class:is-loading={exportingFormat === 'sarif'}
        on:click={() => handleExport('sarif')}
        disabled={disabled || exportingFormat !== null}
        title="OASIS SARIF 2.1.0 standard for GitHub / GitLab code scanning"
      >
        {#if exportingFormat === 'sarif'}
          <span class="btn-spinner"></span>
        {:else}
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
        {/if}
        SARIF 2.1.0
      </button>

      <button
        class="export-btn"
        class:is-loading={exportingFormat === 'html'}
        on:click={() => handleExport('html')}
        disabled={disabled || exportingFormat !== null}
        title="Self-contained interactive HTML security audit report"
      >
        {#if exportingFormat === 'html'}
          <span class="btn-spinner"></span>
        {:else}
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="12 2 2 7 12 12 22 7 12 22"/>
          </svg>
        {/if}
        HTML Report
      </button>

      <button
        class="export-btn"
        class:is-loading={exportingFormat === 'json'}
        on:click={() => handleExport('json')}
        disabled={disabled || exportingFormat !== null}
        title="Raw JSON findings data"
      >
        {#if exportingFormat === 'json'}
          <span class="btn-spinner"></span>
        {:else}
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="16 18 22 12 16 6"/>
            <polyline points="8 6 2 12 8 18"/>
          </svg>
        {/if}
        JSON
      </button>

      <button
        class="export-btn"
        class:is-loading={exportingFormat === 'csv'}
        on:click={() => handleExport('csv')}
        disabled={disabled || exportingFormat !== null}
        title="Spreadsheet compatible CSV export"
      >
        {#if exportingFormat === 'csv'}
          <span class="btn-spinner"></span>
        {:else}
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3h18v18H3z"/>
            <path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>
          </svg>
        {/if}
        CSV
      </button>
    </div>
  </div>

  {#if exportResult}
    <div
      class="export-toast"
      on:mouseenter={cancelAutoDismiss}
      on:mouseleave={scheduleAutoDismiss}
      role="status"
    >
      <div class="toast-header">
        <div class="toast-title-group">
          <span class="toast-icon-success">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </span>
          <span class="toast-title">Export Generated</span>
          <span class="format-badge">{FORMAT_LABELS[exportResult.format as ExportFormat] || exportResult.format.toUpperCase()}</span>
          <span class="size-badge">{formatBytes(exportResult.size)}</span>
        </div>
        <button class="toast-close-btn" on:click={dismiss} title="Dismiss">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="toast-body">
        <div class="path-display" title={exportResult.filePath}>
          <svg class="file-path-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
            <polyline points="13 2 13 9 20 9"/>
          </svg>
          <span class="path-text">{exportResult.filePath}</span>
        </div>
      </div>

      {#if systemStatusMessage}
        <div class="toast-system-msg">
          {systemStatusMessage}
        </div>
      {/if}

      <div class="toast-actions">
        <button class="action-btn primary-action" on:click={handleOpenFile} disabled={isOpening}>
          {#if isOpening}
            <span class="btn-spinner-small"></span>
            Opening...
          {:else}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
              <polyline points="15 3 21 3 21 9"/>
              <line x1="10" y1="14" x2="21" y2="3"/>
            </svg>
            Open File
          {/if}
        </button>

        <button class="action-btn" on:click={handleCopyPath}>
          {#if copiedType === 'path'}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            Copied!
          {:else}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            Copy Path
          {/if}
        </button>

        <button class="action-btn" on:click={handleCopyContent}>
          {#if copiedType === 'content'}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            Copied!
          {:else}
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="16 18 22 12 16 6"/>
              <polyline points="8 6 2 12 8 18"/>
            </svg>
            Copy Content
          {/if}
        </button>
      </div>
    </div>
  {:else if errorMessage}
    <div class="export-toast toast-error" role="alert">
      <div class="toast-header">
        <div class="toast-title-group">
          <span class="toast-icon-error">✕</span>
          <span class="toast-title">Export Failed</span>
        </div>
        <button class="toast-close-btn" on:click={dismiss} title="Dismiss">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="toast-body">
        <p class="error-text">{errorMessage}</p>
      </div>
    </div>
  {/if}
</div>

<style>
  .export-container {
    position: relative;
    display: inline-block;
  }

  .export-bar {
    display: inline-flex;
    align-items: center;
    gap: 0.75rem;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    padding: 0.38rem 0.75rem;
    border-radius: var(--radius-sm);
  }

  .export-label {
    font-size: 0.72rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-dim);
    white-space: nowrap;
  }

  .export-buttons {
    display: flex;
    align-items: center;
    gap: 0.35rem;
  }

  .export-btn {
    font-size: 0.74rem;
    font-weight: 500;
    padding: 0.3rem 0.58rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-muted);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    cursor: pointer;
    transition: all 0.15s ease;
    white-space: nowrap;
  }

  .export-btn:hover:not(:disabled) {
    background: var(--bg-card-hover);
    border-color: var(--accent-cyan);
    color: var(--accent-cyan);
  }

  .export-btn:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .export-btn.is-loading {
    border-color: var(--accent-cyan);
    color: var(--accent-cyan);
  }

  .btn-spinner {
    width: 12px;
    height: 12px;
    border: 2px solid rgba(6, 182, 212, 0.25);
    border-top-color: var(--accent-cyan);
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  .btn-spinner-small {
    width: 10px;
    height: 10px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top-color: #fff;
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  /* Floating Toast / Notification Card */
  .export-toast {
    position: fixed;
    bottom: 1.5rem;
    right: 1.5rem;
    z-index: 9999;
    width: 440px;
    max-width: calc(100vw - 2.5rem);
    background: var(--bg-surface);
    border: 1px solid var(--border-accent);
    border-radius: var(--radius-md);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.65), 0 0 0 1px rgba(6, 182, 212, 0.15);
    padding: 0.85rem 1rem;
    animation: slideUp 0.22s ease-out;
  }

  @keyframes slideUp {
    from {
      opacity: 0;
      transform: translateY(8px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .toast-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0.6rem;
  }

  .toast-title-group {
    display: flex;
    align-items: center;
    gap: 0.45rem;
  }

  .toast-icon-success {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: var(--radius-sm);
    background: var(--accent-green-bg);
    color: var(--accent-green);
  }

  .toast-icon-error {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: var(--radius-sm);
    background: var(--severity-critical-bg);
    color: var(--severity-critical);
    font-weight: 700;
    font-size: 0.75rem;
  }

  .toast-title {
    font-size: 0.825rem;
    font-weight: 600;
    color: var(--text-main);
  }

  .format-badge {
    font-size: 0.68rem;
    font-weight: 600;
    padding: 0.15rem 0.45rem;
    background: var(--accent-cyan-bg);
    color: var(--accent-cyan);
    border-radius: var(--radius-sm);
    letter-spacing: 0.03em;
  }

  .size-badge {
    font-size: 0.68rem;
    font-family: var(--font-mono);
    color: var(--text-dim);
  }

  .toast-close-btn {
    background: transparent;
    border: none;
    color: var(--text-dim);
    cursor: pointer;
    padding: 0.2rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-sm);
    transition: color 0.15s ease;
  }

  .toast-close-btn:hover {
    color: var(--text-main);
  }

  .toast-body {
    margin-bottom: 0.75rem;
  }

  .path-display {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    padding: 0.4rem 0.6rem;
    border-radius: var(--radius-sm);
    overflow: hidden;
  }

  .file-path-icon {
    color: var(--accent-cyan);
    flex-shrink: 0;
  }

  .path-text {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .toast-system-msg {
    font-size: 0.72rem;
    color: var(--accent-cyan);
    margin-bottom: 0.5rem;
    padding-left: 0.2rem;
  }

  .error-text {
    font-size: 0.78rem;
    color: var(--severity-critical);
  }

  .toast-actions {
    display: flex;
    align-items: center;
    gap: 0.45rem;
  }

  .action-btn {
    font-size: 0.72rem;
    font-weight: 500;
    padding: 0.3rem 0.65rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    color: var(--text-main);
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .action-btn:hover:not(:disabled) {
    background: var(--bg-card-hover);
    border-color: var(--border-accent);
  }

  .action-btn.primary-action {
    background: var(--accent-cyan-bg);
    border-color: rgba(6, 182, 212, 0.4);
    color: var(--accent-cyan);
    font-weight: 600;
  }

  .action-btn.primary-action:hover:not(:disabled) {
    background: rgba(6, 182, 212, 0.22);
    border-color: var(--accent-cyan);
  }

  .toast-error {
    border-color: rgba(244, 63, 94, 0.4);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.65), 0 0 0 1px rgba(244, 63, 94, 0.15);
  }
</style>
