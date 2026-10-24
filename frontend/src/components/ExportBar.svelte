<script lang="ts">
  import { getExportUrl } from '../api';

  export let scanId: string = '';
  export let disabled: boolean = false;

  function download(format: 'json' | 'sarif' | 'html' | 'csv') {
    if (!scanId || disabled) return;
    const url = getExportUrl(scanId, format);
    window.open(url, '_blank');
  }
</script>

<div class="export-bar">
  <span class="export-label">Export Scan:</span>
  <div class="export-buttons">
    <button class="export-btn" on:click={() => download('sarif')} {disabled} title="OASIS SARIF 2.1.0 standard for GitHub / GitLab code scanning">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
      </svg>
      SARIF 2.1.0
    </button>

    <button class="export-btn" on:click={() => download('html')} {disabled} title="Self-contained interactive HTML security audit report">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polygon points="12 2 2 7 12 12 22 7 12 22"/>
      </svg>
      HTML Report
    </button>

    <button class="export-btn" on:click={() => download('json')} {disabled} title="Raw JSON findings data">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="16 18 22 12 16 6"/>
        <polyline points="8 6 2 12 8 18"/>
      </svg>
      JSON
    </button>

    <button class="export-btn" on:click={() => download('csv')} {disabled} title="Spreadsheet compatible CSV export">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M3 3h18v18H3z"/>
        <path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>
      </svg>
      CSV
    </button>
  </div>
</div>

<style>
  .export-bar {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    padding: 0.5rem 0.85rem;
    border-radius: var(--radius-md);
  }

  .export-label {
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-dim);
  }

  .export-buttons {
    display: flex;
    gap: 0.4rem;
  }

  .export-btn {
    font-size: 0.75rem;
    padding: 0.3rem 0.6rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
  }

  .export-btn:hover:not(:disabled) {
    background: var(--bg-card-hover);
    border-color: var(--accent-cyan);
    color: var(--accent-cyan);
  }
</style>
