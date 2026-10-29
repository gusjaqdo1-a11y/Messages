import { NextResponse } from 'next/server';
import { messagesBridge } from '../../../../../lib/messages-bridge';

export async function POST(req: Request) {
  try {
    const body = await req.json().catch(() => ({}));
    return NextResponse.json(await messagesBridge({ op: 'read', installation_id: body.installation_id, other_uid: body.other_uid }));
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message || 'Read update failed' }, { status: Number(e?.status) || 500 });
  }
}
