<script lang="ts">
  export let score: number = 0; // 0 to 100
  export let size: number = 160;

  $: clampedScore = Math.max(0, Math.min(100, Math.round(score)));
  
  // Circumference for r = 40 is 2 * PI * 40 = 251.327
  // We make a 240-degree arc or semicircle
  const radius = 45;
  const circumference = 2 * Math.PI * radius;
  // Semicircle arc length = circumference * (260 / 360)
  const arcLength = circumference * 0.72;
  $: strokeDashoffset = arcLength - (arcLength * clampedScore) / 100;

  $: color = clampedScore >= 80 ? 'var(--severity-critical)' :
             clampedScore >= 50 ? 'var(--severity-high)' :
             clampedScore >= 25 ? 'var(--severity-medium)' :
             clampedScore > 0 ? 'var(--severity-low)' :
             'var(--accent-green)';

  $: label = clampedScore >= 80 ? 'CRITICAL RISK' :
             clampedScore >= 50 ? 'HIGH RISK' :
             clampedScore >= 25 ? 'MODERATE' :
             clampedScore > 0 ? 'LOW RISK' :
             'SECURE';
</script>

<div class="risk-gauge" style="width: {size}px; height: {size}px;">
  <svg viewBox="0 0 120 120" class="gauge-svg">
    <!-- Background track -->
    <circle
      cx="60"
      cy="60"
      r={radius}
      fill="none"
      stroke="var(--border-subtle)"
      stroke-width="5"
      stroke-linecap="round"
      stroke-dasharray="{arcLength} {circumference}"
      transform="rotate(140 60 60)"
    />
    <!-- Active score bar -->
    <circle
      cx="60"
      cy="60"
      r={radius}
      fill="none"
      stroke={color}
      stroke-width="5"
      stroke-linecap="round"
      stroke-dasharray="{arcLength} {circumference}"
      stroke-dashoffset={strokeDashoffset}
      transform="rotate(140 60 60)"
      class="score-ring"
    />
    <!-- Text center -->
    <text x="60" y="55" text-anchor="middle" class="score-number" fill={color}>
      {clampedScore}
    </text>
    <text x="60" y="71" text-anchor="middle" class="score-sub" fill="var(--text-dim)">
      / 100
    </text>
    <text x="60" y="87" text-anchor="middle" class="score-label" fill={color}>
      {label}
    </text>
  </svg>
</div>

<style>
  .risk-gauge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    position: relative;
  }
  .gauge-svg {
    width: 100%;
    height: 100%;
  }
  .score-ring {
    transition: stroke-dashoffset 0.8s cubic-bezier(0.4, 0, 0.2, 1), stroke 0.3s ease;
  }
  .score-number {
    font-size: 24px;
    font-weight: 700;
    font-family: var(--font-mono);
  }
  .score-sub {
    font-size: 9px;
    font-family: var(--font-mono);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .score-label {
    font-size: 8px;
    font-weight: 700;
    letter-spacing: 0.1em;
    font-family: var(--font-mono);
  }
</style>
