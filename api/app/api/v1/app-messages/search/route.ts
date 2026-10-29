import { NextResponse } from 'next/server';
import { messagesBridge } from '../../../../../lib/messages-bridge';

export async function POST(req: Request) {
  try {
    const body = await req.json().catch(() => ({}));
    const data = await messagesBridge({ op: 'search', installation_id: body.installation_id, query: body.query });
    return NextResponse.json(data);
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message || 'Search failed' }, { status: Number(e?.status) || 500 });
  }
}
