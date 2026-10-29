import { NextResponse } from 'next/server';
import { getAuth } from 'firebase-admin/auth';
import { firebaseApp } from '../../../../lib/firebase-admin';

const BRIDGE_PATH = '/api/internal/messages-bridge';

function bearer(req: Request) {
  const value = req.headers.get('authorization') || '';
  return value.startsWith('Bearer ') ? value.slice(7).trim() : '';
}

export async function POST(req: Request) {
  const token = bearer(req);
  if (!token) return NextResponse.json({ ok: false, error: 'Authentication required' }, { status: 401 });

  try {
    const decoded = await getAuth(firebaseApp()).verifyIdToken(token);
    const body = await req.json().catch(() => ({}));
    const authInstallation = `firebase-auth:${decoded.uid}`;
    const bridgeKey = process.env.MESSAGES_BRIDGE_API_KEY?.trim();
    if (!bridgeKey) return NextResponse.json({ ok: false, error: 'Messaging service is not configured' }, { status: 503 });

    const origin = new URL(req.url).origin;
    const upstream = await fetch(`${origin}${BRIDGE_PATH}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${bridgeKey}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ ...body, installation_id: authInstallation }),
      cache: 'no-store',
    });

    const text = await upstream.text();
    let payload: unknown = { ok: false, error: 'Invalid messaging response' };
    try { payload = text ? JSON.parse(text) : payload; } catch {}
    return NextResponse.json(payload, { status: upstream.status });
  } catch (error: any) {
    console.error('Messages SOM public API auth error', error);
    return NextResponse.json({ ok: false, error: 'Invalid or expired authentication token' }, { status: 401 });
  }
}
