<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { patchFinding, revealFinding } from '../api';
  import type { Finding, FindingStatus } from '../types';

  export let finding: Finding;

  const dispatch = createEventDispatcher<{
    close: void;
    updated: Finding;
  }>();

  let revealSecret = false;
  let revealing = false;
  let copiedSecret = false;
  let copiedSnippet = false;
  let triageComment = finding.triageComment || '';
  let saving = false;

  async function handleToggleReveal() {
    if (!revealSecret) {
      const needsFetch = !finding.secretRaw || 
                         finding.secretRaw.includes('*') || 
                         !finding.lineContent || 
                         finding.lineContent.includes('***');
      if (needsFetch) {
        revealing = true;
        try {
          const res = await revealFinding(finding.id);
          if (res && res.secretRaw) {
            finding.secretRaw = res.secretRaw;
            finding.lineContent = res.lineContent;
            finding = { ...finding };
          }
        } catch (err: any) {
          console.error('Failed to reveal real secret:', err);
        } finally {
          revealing = false;
        }
      }
      revealSecret = true;
    } else {
      revealSecret = false;
    }
  }

  async function updateStatus(newStatus: FindingStatus) {
    saving = true;
    try {
      const updated = await patchFinding(finding.id, newStatus, triageComment);
      dispatch('updated', updated);
    } catch (err: any) {
      alert(`Failed to update status: ${err.message}`);
    } finally {
      saving = false;
    }
  }

  function copySecret(text?: string) {
    if (!text) return;
    navigator.clipboard.writeText(text);
    copiedSecret = true;
    setTimeout(() => copiedSecret = false, 2000);
  }

  function copySnippet(text?: string) {
    if (!text) return;
    navigator.clipboard.writeText(text);
    copiedSnippet = true;
    setTimeout(() => copiedSnippet = false, 2000);
  }
</script>

<div class="modal-backdrop" on:click|self={() => dispatch('close')} on:keydown|self={(e) => e.key === 'Escape' && dispatch('close')} role="presentation">
  <div class="modal-content drawer">
    <!-- Header -->
    <div class="drawer-header">
      <div class="title-group">
        <div class="badges-row">
          <span class="badge badge-{finding.severity.toLowerCase()}">{finding.severity}</span>
          <span class="badge badge-{finding.presence.toLowerCase()}">
            {#if finding.presence === 'ALIVE'}
              <svg class="badge-icon-svg" width="8" height="8" viewBox="0 0 8 8" fill="currentColor">
                <circle cx="4" cy="4" r="3"/>
              </svg>
              ALIVE IN WORKTREE / HEAD
            {:else}
              <svg class="badge-icon-svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="4"/>
                <line x1="2" y1="12" x2="8" y2="12"/>
                <line x1="16" y1="12" x2="22" y2="12"/>
              </svg>
              BURIED IN GIT HISTORY
            {/if}
          </span>
          <span class="entropy-tag">Entropy: {finding.entropy.toFixed(2)} bits/char</span>
        </div>
        <h2 class="finding-rule">{finding.ruleName}</h2>
        <span class="rule-id-text">{finding.ruleId}</span>
      </div>

      <button class="close-btn" on:click={() => dispatch('close')} title="Close Drawer">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>

    <!-- Body -->
    <div class="drawer-body">
      <!-- Location & Path -->
      <section class="section">
        <div class="section-title">Location</div>
        <div class="location-box">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          <span class="file-link">{finding.filePath}</span>
          <span class="line-badge">Line {finding.lineNumber}</span>
        </div>
      </section>

      <!-- Detected Secret -->
      <section class="section">
        <div class="snippet-header">
          <span class="section-title">Detected Secret</span>
          <div class="snippet-actions">
            <button
              class="toggle-reveal-btn"
              on:click={handleToggleReveal}
              disabled={revealing}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                {#if revealSecret}
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                {:else}
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                {/if}
              </svg>
              <span>{revealing ? 'Unmasking...' : (revealSecret ? 'Mask Secret' : 'Reveal Secret')}</span>
            </button>
            <button
              class="copy-btn"
              on:click={() => copySecret(revealSecret ? (finding.secretRaw || finding.lineContent) : (finding.secretMasked || finding.secretRedacted))}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
              <span>{copiedSecret ? 'Copied Secret!' : 'Copy Secret'}</span>
            </button>
          </div>
        </div>

        <div class="secret-box">
          {#if revealing}
            <div class="revealing-status">
              <span class="spinner-small"></span>
              <span>Unmasking real credential from source...</span>
            </div>
          {:else if revealSecret}
            <span class="secret-revealed-value">{finding.secretRaw || finding.lineContent || finding.secretMasked || finding.secretRedacted}</span>
          {:else}
            <span class="secret-masked-value">{finding.secretMasked || finding.secretRedacted || '***REDACTED***'}</span>
          {/if}
        </div>
      </section>

      <!-- Code Snippet -->
      <section class="section">
        <div class="snippet-header">
          <span class="section-title">Code Snippet (Line {finding.lineNumber})</span>
          <button
            class="copy-btn"
            on:click={() => copySnippet(revealSecret ? (finding.lineContent || finding.snippetRedacted) : (finding.snippetRedacted || finding.lineContent))}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            <span>{copiedSnippet ? 'Copied Snippet!' : 'Copy Snippet'}</span>
          </button>
        </div>

        <div class="code-viewer">
          <div class="line-gutter">{finding.lineNumber}</div>
          <div class="code-line">
            {#if revealing}
              <div class="revealing-status">
                <span class="spinner-small"></span>
                <span>Loading unmasked line...</span>
              </div>
            {:else if revealSecret}
              <span class="snippet-revealed">{finding.lineContent || finding.snippetRedacted || finding.secretRaw || finding.secretMasked || finding.secretRedacted}</span>
            {:else}
              <span class="snippet-masked">{finding.snippetRedacted || finding.lineContent || finding.secretMasked || finding.secretRedacted || '***REDACTED***'}</span>
            {/if}
          </div>
        </div>
      </section>

      <!-- Commit Context (if buried or committed) -->
      {#if finding.commitHash}
        <section class="section">
          <div class="section-title">Git Commit Context</div>
          <div class="commit-card">
            <div class="commit-header">
              <span class="commit-full-hash" title="Full SHA-1">
                {finding.commitHash}
              </span>
              <span class="commit-date">{finding.commitDate || 'Historical commit'}</span>
            </div>
            <div class="commit-author-row">
              <span class="author-label">Author:</span>
              <span class="author-name">{finding.commitAuthor || 'Unknown'}</span>
            </div>
            {#if finding.commitMessage}
              <div class="commit-message">
                "{finding.commitMessage}"
              </div>
            {/if}
          </div>
        </section>
      {/if}

      <!-- Remediation -->
      <section class="section">
        <div class="section-title">Remediation Guidance</div>
        <div class="remediation-box">
          {#if finding.remediation}
            <p>{finding.remediation}</p>
          {:else}
            <p>1. <strong>Revoke and rotate</strong> this credential immediately in the issuing provider console.</p>
            <p>2. Verify access logs for unauthorized use of the exposed credential.</p>
            <p>3. If buried in git history, use tools like <code>git-filter-repo</code> or BFG Repo-Cleaner to purge the sensitive blob across all branches and force push.</p>
          {/if}
        </div>
      </section>

      <!-- Triage & Workflow Status -->
      <section class="section">
        <div class="section-title">Triage & Resolution</div>
        <div class="triage-status-current">
          Current Status: <span class="badge badge-{finding.status.toLowerCase()}">{finding.status.replace('_', ' ')}</span>
        </div>

        <div class="comment-field">
          <input
            type="text"
            bind:value={triageComment}
            placeholder="Add optional triage reason or comment..."
          />
        </div>

        <div class="triage-actions">
          <button
            class="triage-btn fp-btn"
            disabled={saving || finding.status === 'FALSE_POSITIVE'}
            on:click={() => updateStatus('FALSE_POSITIVE')}
          >
            Mark False Positive
          </button>
          <button
            class="triage-btn accept-btn"
            disabled={saving || finding.status === 'ACCEPTED_RISK'}
            on:click={() => updateStatus('ACCEPTED_RISK')}
          >
            Accept Risk
          </button>
          <button
            class="triage-btn resolve-btn"
            disabled={saving || finding.status === 'RESOLVED'}
            on:click={() => updateStatus('RESOLVED')}
          >
            Mark Resolved
          </button>
          {#if finding.status !== 'OPEN'}
            <button
              class="triage-btn reopen-btn"
              disabled={saving}
              on:click={() => updateStatus('OPEN')}
            >
              Reopen
            </button>
          {/if}
        </div>
      </section>
    </div>
  </div>
</div>

<style>
  .drawer {
    max-width: 720px;
    width: 95%;
    padding: 2rem;
  }

  .drawer-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding-bottom: 1.25rem;
    border-bottom: 1px solid var(--border-subtle);
  }

  .badges-row {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    margin-bottom: 0.65rem;
  }

  .entropy-tag {
    font-size: 0.7rem;
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    background: var(--accent-cyan-bg);
    padding: 0.15rem 0.5rem;
    border-radius: var(--radius-sm);
  }

  .finding-rule {
    font-size: 1.35rem;
    font-weight: 700;
    color: var(--text-main);
  }

  .rule-id-text {
    font-size: 0.8rem;
    font-family: var(--font-mono);
    color: var(--text-dim);
  }

  .close-btn {
    background: transparent;
    border: none;
    font-size: 1.25rem;
    color: var(--text-dim);
    padding: 0.25rem 0.5rem;
  }
  .close-btn:hover {
    color: var(--text-main);
  }

  .drawer-body {
    margin-top: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  .section-title {
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text-dim);
    margin-bottom: 0.5rem;
  }

  .location-box {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    padding: 0.65rem 1rem;
    border-radius: var(--radius-md);
  }

  .file-link {
    font-family: var(--font-mono);
    color: var(--accent-cyan);
    font-size: 0.85rem;
    word-break: break-all;
  }

  .line-badge {
    margin-left: auto;
    font-family: var(--font-mono);
    font-size: 0.75rem;
    background: var(--bg-subtle);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-sm);
    color: var(--text-muted);
  }

  .snippet-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.5rem;
  }

  .snippet-actions {
    display: flex;
    gap: 0.5rem;
  }

  .toggle-reveal-btn, .copy-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    font-size: 0.75rem;
    padding: 0.3rem 0.65rem;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-subtle);
    background: var(--bg-surface);
    color: var(--text-main);
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .toggle-reveal-btn:hover, .copy-btn:hover {
    background: var(--bg-card);
    border-color: var(--border-focus);
    color: var(--accent-cyan);
  }

  .secret-box {
    display: flex;
    align-items: center;
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 0.75rem 1rem;
    font-family: var(--font-mono);
    font-size: 0.85rem;
    word-break: break-all;
    min-height: 2.75rem;
  }

  .revealing-status {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--accent-cyan);
    font-size: 0.8rem;
    font-style: italic;
  }

  .spinner-small {
    width: 13px;
    height: 13px;
    border: 2px solid var(--border-subtle);
    border-top-color: var(--accent-cyan);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .secret-masked-value {
    color: var(--severity-high);
    font-weight: 600;
    letter-spacing: 0.04em;
  }

  .secret-revealed-value {
    color: var(--severity-critical);
    background: var(--severity-critical-bg);
    padding: 0.2rem 0.5rem;
    border-radius: var(--radius-sm);
    font-weight: 700;
  }

  .code-viewer {
    display: flex;
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    overflow: hidden;
    font-family: var(--font-mono);
    font-size: 0.85rem;
  }

  .line-gutter {
    background: var(--bg-surface);
    color: var(--text-dim);
    padding: 0.75rem 1rem;
    border-right: 1px solid var(--border-subtle);
    user-select: none;
  }

  .code-line {
    padding: 0.75rem 1rem;
    overflow-x: auto;
    white-space: pre-wrap;
    word-break: break-all;
    flex: 1;
  }

  .snippet-masked {
    color: var(--text-main);
  }

  .snippet-revealed {
    color: var(--text-main);
  }

  .commit-card {
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 0.85rem 1rem;
  }

  .commit-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 0.5rem;
  }

  .commit-full-hash {
    font-family: var(--font-mono);
    font-size: 0.8rem;
    color: var(--accent-green);
  }

  .commit-date {
    font-size: 0.75rem;
    color: var(--text-dim);
  }

  .commit-author-row {
    font-size: 0.8rem;
    margin-bottom: 0.4rem;
  }
  .author-label { color: var(--text-dim); }
  .author-name { font-weight: 600; color: var(--text-main); }

  .commit-message {
    font-size: 0.8rem;
    color: var(--text-muted);
    font-style: italic;
    background: var(--bg-surface);
    padding: 0.5rem 0.75rem;
    border-radius: var(--radius-sm);
  }

  .remediation-box {
    background: var(--bg-card);
    border: 1px solid var(--border-accent);
    border-left: 3px solid var(--accent-cyan);
    border-radius: var(--radius-md);
    padding: 1rem;
    font-size: 0.825rem;
    line-height: 1.6;
    color: var(--text-muted);
  }
  .remediation-box code {
    font-family: var(--font-mono);
    background: var(--bg-main);
    padding: 0.15rem 0.4rem;
    border-radius: var(--radius-sm);
    color: var(--accent-cyan);
  }

  .triage-status-current {
    font-size: 0.85rem;
    color: var(--text-muted);
    margin-bottom: 0.75rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .comment-field input {
    width: 100%;
    margin-bottom: 0.85rem;
  }

  .triage-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .triage-btn {
    font-size: 0.8rem;
    padding: 0.4rem 0.85rem;
  }

  .fp-btn:hover:not(:disabled) {
    border-color: var(--text-dim);
  }
  .accept-btn:hover:not(:disabled) {
    border-color: var(--severity-medium);
    color: var(--severity-medium);
  }
  .resolve-btn:hover:not(:disabled) {
    border-color: var(--accent-green);
    color: var(--accent-green);
  }
</style>
