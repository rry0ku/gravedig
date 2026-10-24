import type { Finding, FindingFilter, FindingStatus, Rule, RuleTestResponse, ScanEvent, ScanRecord } from './types';

// Retrieve token from query params or localStorage
const urlParams = new URLSearchParams(window.location.search);
const paramToken = urlParams.get('token');
if (paramToken) {
  localStorage.setItem('gravedig_token', paramToken);
}

let activeToken = localStorage.getItem('gravedig_token') || '';

export function getToken(): string {
  return activeToken;
}

export function setToken(token: string) {
  activeToken = token;
  localStorage.setItem('gravedig_token', token);
}

function getHeaders(): HeadersInit {
  const headers: HeadersInit = {
    'Content-Type': 'application/json'
  };
  if (activeToken) {
    headers['X-Auth-Token'] = activeToken;
  }
  return headers;
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = { ...getHeaders(), ...(options.headers || {}) };
  const res = await fetch(endpoint, { ...options, headers });
  if (!res.ok) {
    const errorBody = await res.text();
    let msg = `HTTP error ${res.status}: ${res.statusText}`;
    try {
      const parsed = JSON.parse(errorBody);
      if (parsed.message) msg = parsed.message;
      else if (parsed.error) msg = parsed.error;
    } catch {
      if (errorBody) msg = errorBody;
    }
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}

export async function validateToken(): Promise<boolean> {
  try {
    await request('/api/auth/validate');
    return true;
  } catch {
    return false;
  }
}

export async function startScan(params: {
  targetPath?: string;
  includeHistory?: boolean;
  entropyCheck?: boolean;
  offlineValidation?: boolean;
}): Promise<ScanRecord> {
  return request<ScanRecord>('/api/scans', {
    method: 'POST',
    body: JSON.stringify(params)
  });
}

export async function listScans(): Promise<ScanRecord[]> {
  return request<ScanRecord[]>('/api/scans');
}

export async function getScan(id: string): Promise<ScanRecord> {
  return request<ScanRecord>(`/api/scans/${id}`);
}

export async function getFindings(scanId: string, filter?: FindingFilter): Promise<Finding[]> {
  const query = new URLSearchParams();
  if (filter?.severity) query.set('severity', filter.severity);
  if (filter?.ruleId) query.set('ruleId', filter.ruleId);
  if (filter?.filePath) query.set('filePath', filter.filePath);
  if (filter?.status) query.set('status', filter.status);
  if (filter?.presence) query.set('presence', filter.presence);
  if (filter?.limit) query.set('limit', filter.limit.toString());
  if (filter?.offset) query.set('offset', filter.offset.toString());

  const qs = query.toString();
  return request<Finding[]>(`/api/scans/${scanId}/findings${qs ? '?' + qs : ''}`);
}

export async function patchFinding(id: string, status: FindingStatus, comment?: string): Promise<Finding> {
  return request<Finding>(`/api/findings/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status, comment })
  });
}

export function getExportUrl(scanId: string, format: 'json' | 'sarif' | 'html' | 'csv'): string {
  const token = getToken();
  return `/api/scans/${scanId}/export?format=${format}${token ? '&token=' + encodeURIComponent(token) : ''}`;
}

export async function listRules(): Promise<Rule[]> {
  return request<Rule[]>('/api/rules');
}

export async function testRule(payload: {
  ruleId?: string;
  pattern?: string;
  snippet: string;
}): Promise<RuleTestResponse> {
  return request<RuleTestResponse>('/api/rules/test', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function createScanWebSocket(
  scanId: string,
  onEvent: (event: ScanEvent) => void,
  onClose?: () => void,
  onError?: (err: Event) => void
): WebSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const token = getToken();
  const url = `${protocol}//${window.location.host}/ws/scan/${scanId}${token ? '?token=' + encodeURIComponent(token) : ''}`;
  
  const ws = new WebSocket(url);
  ws.onmessage = (msg) => {
    try {
      const data = JSON.parse(msg.data) as ScanEvent;
      onEvent(data);
    } catch (err) {
      console.error('Failed to parse WebSocket message:', err);
    }
  };
  if (onClose) ws.onclose = onClose;
  if (onError) ws.onerror = onError;
  return ws;
}
