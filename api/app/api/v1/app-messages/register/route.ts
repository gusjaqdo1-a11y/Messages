import { NextResponse } from 'next/server';
import { messagesBridge } from '../../../../../lib/messages-bridge';

export async function POST(req: Request) {
  try {
    const body = await req.json().catch(() => ({}));
    const data = await messagesBridge({ op: 'register', installation_id: body.installation_id, phone_number: body.phone_number, fcm_token: body.fcm_token, display_name: body.display_name });
    return NextResponse.json(data);
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message || 'Registration failed' }, { status: Number(e?.status) || 500 });
  }
}
