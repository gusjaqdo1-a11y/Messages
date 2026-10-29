import { NextResponse } from 'next/server';
import { authorizedKey, hasScope, readData, countryFromNumber, normalizeNumber, writeData, newId } from '../../../../../lib/sms';
import { messagesBridge } from '../../../../../lib/messages-bridge';

export async function POST(req: Request) {
  try {
    const b = await req.json().catch(() => ({}));
    const installation = String(b.installation_id || '').trim();
    const text = String(b.message ?? b.text ?? '').trim();

    if (installation) {
      const recipientUid = String(b.recipient_uid || '').trim();
      if (installation.length < 32 || !recipientUid || !text) return NextResponse.json({ ok: false, error: 'Invalid message request' }, { status: 400 });
      if (text.length > 4000) return NextResponse.json({ ok: false, error: 'Message is too long' }, { status: 400 });
      return NextResponse.json(await messagesBridge({ op: 'send', installation_id: installation, recipient_uid: recipientUid, message: text }));
    }

    const key = authorizedKey(req);
    const data = await readData();
    const internal = Boolean(process.env.APP_API_KEY && key === process.env.APP_API_KEY);
    const apiKey = data.apiKeys.find((k: any) => k.token === key && k.enabled !== false);
    if (!internal && (!apiKey || !(hasScope(apiKey, 'app_messages:send') || hasScope(apiKey, 'messages:send')))) return NextResponse.json({ ok: false, error: 'Unauthorized' }, { status: 401 });
    if (data.settings?.appMessaging === false) return NextResponse.json({ ok: false, error: 'Messages SOM app messaging is disabled' }, { status: 503 });

    const senderValue = String(b.sender_id || '').trim();
    const to = normalizeNumber(b.to);
    if (!senderValue || !to || !text) return NextResponse.json({ ok: false, error: 'sender_id, to and message are required' }, { status: 400 });
    if (!/^\+?[1-9]\d{6,14}$/.test(to)) return NextResponse.json({ ok: false, error: 'Invalid international recipient number' }, { status: 400 });
    if (text.length > 4000) return NextResponse.json({ ok: false, error: 'Message is too long (maximum 4000 characters)' }, { status: 400 });

    const sender = data.senderIds.find((s: any) => s.enabled !== false && (s.id === senderValue || s.providerSenderId === senderValue || s.name === senderValue));
    if (!sender) return NextResponse.json({ ok: false, error: 'Sender ID not found or disabled' }, { status: 400 });
    if (sender.appMessagingEnabled === false) return NextResponse.json({ ok: false, error: 'Sender ID is not enabled for Messages SOM' }, { status: 403 });
    const country = countryFromNumber(to);
    const allowed = Array.isArray(sender.countries) ? sender.countries.map((x: string) => x.toUpperCase()).filter(Boolean) : [];
    if (allowed.length && country && !allowed.includes(country)) return NextResponse.json({ ok: false, error: `Sender ID is not enabled for ${country}` }, { status: 403 });

    const result = await messagesBridge({ op: 'external_send', sender_id: String(sender.providerSenderId || sender.name || senderValue), sender_name: String(sender.name || senderValue), to, message: text, allow_replies: sender.allowReplies === true });
    try {
      const fresh = await readData();
      fresh.messages.unshift({ id: newId('appmsg'), senderId: sender.id, senderName: sender.name || senderValue, to, text, status: result?.data?.status || 'sent', source: 'messages_som_network', recipient: result?.data?.recipient || null, messageId: result?.data?.id || null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
      if (fresh.messages.length > 5000) fresh.messages = fresh.messages.slice(0, 5000);
      await writeData(fresh);
    } catch {}
    return NextResponse.json(result);
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message || 'App message failed' }, { status: Number(e?.status) || 500 });
  }
}
