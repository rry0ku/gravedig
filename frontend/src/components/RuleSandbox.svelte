<script lang="ts">
  import { onMount } from 'svelte';
  import { listRules, testRule } from '../api';
  import type { Rule, RuleTestResponse } from '../types';

  let rules: Rule[] = [];
  let loadingRules = true;

  let selectedRuleId = '';
  let customPattern = '';
  let snippet = 'AKIAIOSFODNN7EXAMPLE\nghp_1234567890abcdefghijklmnopqrstuvwxyzAB\nBEGIN RSA PRIVATE KEY';
  let testing = false;
  let result: RuleTestResponse | null = null;
  let testError = '';

  onMount(async () => {
    try {
      rules = await listRules();
      if (rules.length > 0) {
        selectedRuleId = rules[0].id;
      }
    } catch (err: any) {
      console.error('Failed to load rules:', err);
    } finally {
      loadingRules = false;
    }
  });

  async function runTest() {
    if (!snippet.trim()) return;
    testing = true;
    testError = '';
    result = null;

    try {
      result = await testRule({
        ruleId: selectedRuleId === 'custom' ? undefined : selectedRuleId,
        pattern: selectedRuleId === 'custom' ? customPattern : undefined,
        snippet
      });
    } catch (err: any) {
      testError = err.message || 'Rule test failed';
    } finally {
      testing = false;
    }
  }
</script>

<div class="sandbox-container">
  <!-- Sandbox Tester Box -->
  <div class="card tester-card">
    <div class="card-header">
      <div class="header-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent-cyan)" stroke-width="2">
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
        </svg>
        <h3>Rule Sandbox & Regex Tester</h3>
      </div>
      <span class="subtext">Verify regex detection and Shannon entropy against raw text snippets</span>
    </div>

    <div class="tester-grid">
      <!-- Input Column -->
      <div class="input-col">
        <div class="form-row">
          <label for="rule-select">Active Detection Rule</label>
          <select id="rule-select" bind:value={selectedRuleId}>
            <option value="custom">-- Custom Regex Pattern --</option>
            {#each rules as r (r.id)}
              <option value={r.id}>[{r.severity}] {r.name} ({r.id})</option>
            {/each}
          </select>
        </div>

        {#if selectedRuleId === 'custom'}
          <div class="form-row">
            <label for="custom-pattern">Custom Regex Pattern</label>
            <input
              id="custom-pattern"
              type="text"
              bind:value={customPattern}
              placeholder={'e.g. (?i)(password|secret)\\s*[:=]\\s*[\'"][^\'"]{6,}[\'"]'}
              class="mono-input"
            />
          </div>
        {/if}

        <div class="form-row">
          <label for="test-snippet">Test Payload / Snippet</label>
          <textarea
            id="test-snippet"
            rows="6"
            bind:value={snippet}
            placeholder="Paste code snippet containing credentials..."
            class="mono-textarea"
          ></textarea>
        </div>

        <button class="primary test-btn" on:click={runTest} disabled={testing}>
          {#if testing}
            Testing...
          {:else}
            Run Detection Test
          {/if}
        </button>

        {#if testError}
          <div class="error-box">{testError}</div>
        {/if}
      </div>

      <!-- Result Column -->
      <div class="result-col">
        <div class="result-header">Detection Result</div>
        {#if !result}
          <div class="empty-result">
            <span>Click "Run Detection Test" to analyze the snippet.</span>
          </div>
        {:else}
          <div class="result-box {result.matched ? 'matched' : 'not-matched'}">
            <div class="status-banner">
              {#if result.matched}
                <span class="matched-badge">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  MATCH DETECTED ({result.matchCount || result.matches?.length || 1} secret{(result.matchCount || result.matches?.length || 1) > 1 ? 's' : ''})
                </span>
              {:else}
                <span class="unmatched-badge">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                  NO SECRET MATCHED
                </span>
              {/if}
            </div>

            <div class="result-details">
              {#if result.ruleName}
                <div class="res-row">
                  <span class="res-label">Rule:</span>
                  <span class="res-val">{result.ruleName}</span>
                </div>
              {/if}
              {#if result.severity}
                <div class="res-row">
                  <span class="res-label">Severity:</span>
                  <span class="badge badge-{result.severity.toLowerCase()}">{result.severity}</span>
                </div>
              {/if}
              {#if result.entropy !== undefined && result.entropy > 0}
                <div class="res-row">
                  <span class="res-label">Avg Entropy:</span>
                  <span class="res-val mono">{result.entropy.toFixed(2)} bits/char</span>
                </div>
              {/if}
              {#if result.matches && result.matches.length > 0}
                <div class="res-row-full">
                  <span class="res-label">Detected Secrets ({result.matches.length}):</span>
                  <div class="matches-list">
                    {#each result.matches as m}
                      <div class="match-item">
                        <code class="match-redacted">{m.redacted}</code>
                        <span class="match-entropy">{m.entropy.toFixed(2)} bits/char</span>
                      </div>
                    {/each}
                  </div>
                </div>
              {/if}
              {#if result.maskedSnippet}
                <div class="res-row-full">
                  <span class="res-label">Redacted Snippet:</span>
                  <pre class="masked-preview">{result.maskedSnippet}</pre>
                </div>
              {/if}
            </div>
          </div>
        {/if}
      </div>
    </div>
  </div>

  <!-- Loaded Rules Library Table -->
  <div class="card rules-library-card">
    <div class="library-header">
      <h4>Loaded Ruleset ({rules.length} Rules Active)</h4>
      <span class="subtext">Standard built-in signatures defined in YAML configuration</span>
    </div>

    <div class="table-scroll">
      <table class="rules-table">
        <thead>
          <tr>
            <th>Severity</th>
            <th>Rule ID</th>
            <th>Name</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          {#each rules as r (r.id)}
            <tr>
              <td>
                <span class="badge badge-{r.severity.toLowerCase()}">{r.severity}</span>
              </td>
              <td class="mono id-col">{r.id}</td>
              <td class="name-col">{r.name}</td>
              <td class="desc-col">{r.description}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </div>
</div>

<style>
  .sandbox-container {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }

  .card {
    background: var(--bg-card);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
  }

  .card-header {
    margin-bottom: 1.25rem;
  }
  .header-title {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }
  .header-title h3 {
    font-size: 1.15rem;
    font-weight: 700;
  }
  .subtext {
    font-size: 0.75rem;
    color: var(--text-dim);
    margin-top: 0.25rem;
    display: block;
  }

  .tester-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1.5rem;
  }

  .form-row {
    margin-bottom: 1rem;
  }
  .form-row label {
    display: block;
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--text-muted);
    margin-bottom: 0.35rem;
  }
  .form-row select, .form-row input, .form-row textarea {
    width: 100%;
  }

  .mono-input, .mono-textarea {
    font-family: var(--font-mono);
    font-size: 0.8rem;
  }

  .test-btn {
    width: 100%;
    justify-content: center;
    padding: 0.6rem;
  }

  .error-box {
    margin-top: 0.75rem;
    background: var(--severity-critical-bg);
    border: 1px solid var(--severity-critical);
    color: var(--severity-critical);
    padding: 0.5rem 0.75rem;
    border-radius: var(--radius-md);
    font-size: 0.8rem;
  }

  .result-col {
    display: flex;
    flex-direction: column;
  }

  .result-header {
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-dim);
    margin-bottom: 0.5rem;
  }

  .empty-result {
    flex: 1;
    background: var(--bg-surface);
    border: 1px dashed var(--border-subtle);
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0.8rem;
    color: var(--text-dim);
    padding: 2rem;
  }

  .result-box {
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    padding: 1rem;
    flex: 1;
  }
  .result-box.matched {
    border-color: var(--severity-critical);
  }
  .result-box.not-matched {
    border-color: var(--accent-green);
  }

  .status-banner {
    margin-bottom: 1rem;
  }
  .matched-badge {
    color: var(--severity-critical);
    font-weight: 700;
    font-size: 0.85rem;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
  }
  .unmatched-badge {
    color: var(--accent-green);
    font-weight: 700;
    font-size: 0.85rem;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
  }

  .result-details {
    display: flex;
    flex-direction: column;
    gap: 0.65rem;
  }
  .res-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.825rem;
  }
  .res-label {
    color: var(--text-dim);
    font-weight: 600;
  }
  .res-val {
    color: var(--text-main);
  }
  .res-val.mono {
    font-family: var(--font-mono);
  }

  .res-row-full {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
    margin-top: 0.5rem;
  }
  .matches-list {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }
  .match-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    padding: 0.35rem 0.65rem;
    border-radius: var(--radius-sm);
  }
  .match-redacted {
    color: var(--accent-green);
    font-size: 0.8rem;
  }
  .match-entropy {
    color: var(--text-dim);
    font-size: 0.725rem;
    font-family: var(--font-mono);
  }
  .masked-preview {
    background: var(--bg-main);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-sm);
    padding: 0.65rem;
    font-family: var(--font-mono);
    font-size: 0.75rem;
    white-space: pre-wrap;
    word-break: break-all;
    color: var(--accent-green);
  }

  /* Rules Library */
  .rules-library-card {
    padding: 1.25rem;
  }
  .library-header {
    margin-bottom: 1rem;
  }
  .library-header h4 {
    font-size: 1rem;
    font-weight: 700;
  }

  .table-scroll {
    overflow-x: auto;
  }

  .rules-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.8rem;
    text-align: left;
  }
  .rules-table th {
    background: var(--bg-surface);
    color: var(--text-dim);
    font-size: 0.7rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    padding: 0.6rem 0.85rem;
    border-bottom: 1px solid var(--border-subtle);
  }
  .rules-table td {
    padding: 0.65rem 0.85rem;
    border-bottom: 1px solid var(--border-subtle);
  }
  .rules-table tr:hover {
    background: var(--bg-surface);
  }
  .id-col {
    color: var(--accent-cyan);
    font-size: 0.75rem;
  }
  .name-col {
    font-weight: 600;
    color: var(--text-main);
  }
  .desc-col {
    color: var(--text-muted);
  }
</style>
