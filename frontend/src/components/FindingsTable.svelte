<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import type { Finding, FindingFilter, FindingStatus, Presence, Severity } from '../types';

  export let findings: Finding[] = [];
  export let loading: boolean = false;

  const dispatch = createEventDispatcher<{
    selectFinding: Finding;
    filterChange: FindingFilter;
  }>();

  let searchQuery = '';
  let selectedSeverity: Severity | '' = '';
  let selectedPresence: Presence | '' = '';
  let selectedStatus: FindingStatus | '' = '';

  let currentPage = 1;
  let pageSize = 20;

  $: filteredFindings = findings.filter((f) => {
    if (selectedSeverity && f.severity !== selectedSeverity) return false;
    if (selectedPresence && f.presence !== selectedPresence) return false;
    if (selectedStatus && f.status !== selectedStatus) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      const matchFile = f.filePath.toLowerCase().includes(q);
      const matchRule = f.ruleName.toLowerCase().includes(q) || f.ruleId.toLowerCase().includes(q);
      const matchCommit = f.commitHash?.toLowerCase().includes(q) || f.commitAuthor?.toLowerCase().includes(q);
      if (!matchFile && !matchRule && !matchCommit) return false;
    }
    return true;
  });

  $: totalPages = Math.max(1, Math.ceil(filteredFindings.length / pageSize));
  $: pagedFindings = filteredFindings.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  function resetPage() {
    currentPage = 1;
  }

  function handleFilter() {
    resetPage();
    dispatch('filterChange', {
      severity: selectedSeverity,
      presence: selectedPresence,
      status: selectedStatus,
      filePath: searchQuery
    });
  }

  function clearFilters() {
    searchQuery = '';
    selectedSeverity = '';
    selectedPresence = '';
    selectedStatus = '';
    resetPage();
    dispatch('filterChange', {});
  }
</script>

<div class="table-container">
  <!-- Filter Toolbar -->
  <div class="filter-toolbar">
    <div class="search-box">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8"/>
        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
      </svg>
      <input
        type="text"
        bind:value={searchQuery}
        on:input={resetPage}
        placeholder="Filter by file path, rule, commit hash or author..."
      />
    </div>

    <div class="select-group">
      <select bind:value={selectedSeverity} on:change={resetPage}>
        <option value="">All Severities</option>
        <option value="CRITICAL">Critical</option>
        <option value="HIGH">High</option>
        <option value="MEDIUM">Medium</option>
        <option value="LOW">Low</option>
        <option value="INFO">Info</option>
      </select>

      <select bind:value={selectedPresence} on:change={resetPage}>
        <option value="">All Presences</option>
        <option value="ALIVE">Alive (HEAD)</option>
        <option value="BURIED">Buried (Git History)</option>
      </select>

      <select bind:value={selectedStatus} on:change={resetPage}>
        <option value="">All Statuses</option>
        <option value="OPEN">Open</option>
        <option value="FALSE_POSITIVE">False Positive</option>
        <option value="RESOLVED">Resolved</option>
        <option value="ACCEPTED_RISK">Accepted Risk</option>
      </select>

      {#if searchQuery || selectedSeverity || selectedPresence || selectedStatus}
        <button class="clear-btn" on:click={clearFilters}>Reset</button>
      {/if}
    </div>
  </div>

  <!-- Findings Table -->
  <div class="table-scroll">
    <table class="findings-table">
      <thead>
        <tr>
          <th>Severity</th>
          <th>Presence</th>
          <th>Status</th>
          <th>Rule</th>
          <th>File & Line</th>
          <th>Commit Context</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>
        {#if loading}
          <tr>
            <td colspan="7" class="empty-cell">
              <div class="loading-spinner"></div>
              <span>Loading findings...</span>
            </td>
          </tr>
        {:else if pagedFindings.length === 0}
          <tr>
            <td colspan="7" class="empty-cell">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" stroke-width="1.5">
                <circle cx="12" cy="12" r="10"/>
                <path d="m9 12 2 2 4-4"/>
              </svg>
              <div class="empty-title">No matching secrets found</div>
              <div class="empty-sub">Your repository is clean of detected credentials matching this filter.</div>
            </td>
          </tr>
        {:else}
          {#each pagedFindings as finding (finding.id)}
            <tr on:click={() => dispatch('selectFinding', finding)} class="finding-row">
              <td>
                <span class="badge badge-{finding.severity.toLowerCase()}">{finding.severity}</span>
              </td>
              <td>
                <span class="badge badge-{finding.presence.toLowerCase()}">
                  {#if finding.presence === 'ALIVE'}
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
              </td>
              <td>
                <span class="status-tag status-{finding.status.toLowerCase()}">
                  {finding.status.replace('_', ' ')}
                </span>
              </td>
              <td>
                <div class="rule-name">{finding.ruleName}</div>
                <div class="rule-id">{finding.ruleId}</div>
              </td>
              <td>
                <div class="file-path" title={finding.filePath}>{finding.filePath}</div>
                <div class="line-num">Line {finding.lineNumber}</div>
              </td>
              <td>
                {#if finding.commitHash}
                  <div class="commit-hash" title={finding.commitHash}>
                    {finding.commitHash.substring(0, 7)}
                  </div>
                  <div class="commit-author">{finding.commitAuthor || 'Unknown'}</div>
                {:else}
                  <div class="worktree-label">Working Tree</div>
                {/if}
              </td>
              <td>
                <button
                  class="inspect-btn"
                  on:click|stopPropagation={() => dispatch('selectFinding', finding)}
                >
                  Inspect
                </button>
              </td>
            </tr>
          {/each}
        {/if}
      </tbody>
    </table>
  </div>

  <!-- Pagination Bar -->
  <div class="pagination-bar">
    <div class="pagination-info">
      Showing {filteredFindings.length === 0 ? 0 : (currentPage - 1) * pageSize + 1}
      to {Math.min(currentPage * pageSize, filteredFindings.length)} of {filteredFindings.length} secrets
    </div>
    <div class="pagination-controls">
      <button
        disabled={currentPage <= 1}
        on:click={() => currentPage--}
        class="page-btn"
      >
        Previous
      </button>
      <span class="page-number">Page {currentPage} of {totalPages}</span>
      <button
        disabled={currentPage >= totalPages}
        on:click={() => currentPage++}
        class="page-btn"
      >
        Next
      </button>
    </div>
  </div>
</div>

<style>
  .table-container {
    background-color: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    overflow: hidden;
  }

  .filter-toolbar {
    padding: 1rem 1.25rem;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 0.85rem;
    border-bottom: 1px solid var(--border-subtle);
    background-color: var(--bg-surface);
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 0.25rem 0.75rem;
    flex: 1;
    min-width: 280px;
    color: var(--text-muted);
  }

  .search-box input {
    background: transparent;
    border: none;
    box-shadow: none;
    padding: 0.35rem 0;
    width: 100%;
  }

  .select-group {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .clear-btn {
    font-size: 0.75rem;
    padding: 0.4rem 0.75rem;
  }

  .table-scroll {
    overflow-x: auto;
  }

  .findings-table {
    width: 100%;
    border-collapse: collapse;
    text-align: left;
    font-size: 0.825rem;
  }

  .findings-table th {
    background-color: var(--bg-surface);
    color: var(--text-dim);
    font-weight: 700;
    text-transform: uppercase;
    font-size: 0.7rem;
    letter-spacing: 0.05em;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .findings-table td {
    padding: 0.85rem 1rem;
    border-bottom: 1px solid var(--border-subtle);
    vertical-align: middle;
  }

  .finding-row {
    cursor: pointer;
    transition: background-color 0.15s ease;
  }

  .finding-row:hover {
    background-color: var(--bg-card-hover);
  }

  .status-tag {
    font-size: 0.72rem;
    font-weight: 600;
    padding: 0;
    background: transparent;
    text-transform: uppercase;
    font-family: var(--font-mono);
  }
  .status-open { color: #f97316; }
  .status-false_positive { color: #94a3b8; }
  .status-resolved { color: #10b981; }
  .status-accepted_risk { color: #eab308; }

  .rule-name {
    font-weight: 600;
    color: var(--text-main);
  }
  .rule-id {
    font-size: 0.7rem;
    color: var(--text-dim);
    font-family: var(--font-mono);
  }

  .file-path {
    max-width: 240px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
  }
  .line-num {
    font-size: 0.7rem;
    color: var(--text-dim);
    font-family: var(--font-mono);
  }

  .commit-hash {
    font-family: var(--font-mono);
    font-weight: 600;
    color: var(--text-main);
  }
  .commit-author {
    font-size: 0.7rem;
    color: var(--text-dim);
  }
  .worktree-label {
    font-size: 0.75rem;
    color: var(--accent-green);
    font-family: var(--font-mono);
  }

  .inspect-btn {
    font-size: 0.75rem;
    padding: 0.3rem 0.7rem;
  }

  .empty-cell {
    text-align: center;
    padding: 3rem 1rem !important;
  }
  .empty-title {
    font-size: 1rem;
    font-weight: 600;
    margin: 0.75rem 0 0.25rem;
    color: var(--text-main);
  }
  .empty-sub {
    font-size: 0.8rem;
    color: var(--text-dim);
  }

  .loading-spinner {
    width: 24px;
    height: 24px;
    border: 3px solid var(--border-subtle);
    border-top-color: var(--accent-green);
    border-radius: 50%;
    margin: 0 auto 0.75rem;
    animation: spin 0.8s linear infinite;
  }

  .pagination-bar {
    padding: 0.75rem 1.25rem;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background-color: var(--bg-surface);
    font-size: 0.8rem;
  }

  .pagination-info {
    color: var(--text-muted);
  }

  .pagination-controls {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }

  .page-number {
    color: var(--text-main);
    font-family: var(--font-mono);
  }

  .page-btn {
    font-size: 0.75rem;
    padding: 0.35rem 0.75rem;
  }
</style>
