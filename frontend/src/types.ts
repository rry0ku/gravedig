export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type Presence = 'ALIVE' | 'BURIED';
export type FindingStatus = 'OPEN' | 'FALSE_POSITIVE' | 'RESOLVED' | 'ACCEPTED_RISK';
export type ScanStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface Finding {
  id: string;
  scanId: string;
  ruleId: string;
  ruleName: string;
  severity: Severity;
  filePath: string;
  lineNumber: number;
  lineContent?: string;
  secretMasked?: string;
  commitHash?: string;
  commitAuthor?: string;
  commitDate?: string;
  commitMessage?: string;
  presence: Presence;
  status: FindingStatus;
  entropy: number;
  remediation?: string;
  triageComment?: string;
}

export interface ScanSummaryData {
  totalFindings: number;
  aliveCount: number;
  buriedCount: number;
  riskScore: number;
  filesScanned: number;
  commitsScanned: number;
  durationMs: number;
  severityCounts: Record<Severity, number>;
}

export interface ScanRecord {
  id: string;
  targetPath: string;
  status: ScanStatus;
  includeHistory: boolean;
  totalFiles: number;
  totalCommits: number;
  totalFindings: number;
  riskScore: number;
  startedAt: string;
  completedAt?: string;
  summaryJson?: string;
  errorMessage?: string;
}

export interface ScanEvent {
  type: 'STARTED' | 'PROGRESS' | 'FINDING' | 'COMPLETED' | 'ERROR';
  scanId: string;
  filesScanned: number;
  commitsScanned: number;
  findingsFound: number;
  currentFile?: string;
  finding?: Finding;
  message?: string;
  riskScore?: number;
}

export interface Rule {
  id: string;
  name: string;
  severity: Severity;
  description: string;
  remediation?: string;
  pattern?: string;
}

export interface RuleTestResponse {
  matched: boolean;
  matchCount: number;
  maskedSnippet?: string;
  ruleId?: string;
  ruleName?: string;
  severity?: string;
  entropy?: number;
  message?: string;
}

export interface FindingFilter {
  severity?: Severity | '';
  ruleId?: string;
  filePath?: string;
  status?: FindingStatus | '';
  presence?: Presence | '';
  limit?: number;
  offset?: number;
}
