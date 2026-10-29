const BRIDGE_URL = (process.env.MESSAGES_ADMIN_BRIDGE_URL || 'https://messages-som-admin.vercel.app/api/internal/messages-bridge').replace(/\/$/, '');

export async function messagesBridge(body: Record<string, unknown>) {
  const key = process.env.MESSAGES_BRIDGE_API_KEY?.trim();
  if (!key) throw new Error('Messages SOM backend bridge is not configured');
  const response = await fetch(BRIDGE_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json', authorization: `Bearer ${key}` },
    body: JSON.stringify(body),
    cache: 'no-store',
  });
  const raw = await response.text();
  let json: any;
  try { json = raw ? JSON.parse(raw) : {}; } catch { json = { ok: false, error: raw.slice(0, 500) }; }
  if (!response.ok || json?.ok === false) {
    const error = new Error(String(json?.error || `Messages SOM bridge HTTP ${response.status}`));
    (error as any).status = response.status;
    (error as any).payload = json;
    throw error;
  }
  return json;
}
