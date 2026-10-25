import { NextResponse } from 'next/server';
import { authorizedKey, hasScope, readData } from '../../../../../lib/sms';
import { firebaseDb, firebaseMessaging } from '../../../../../lib/firebase-admin';

function normalizePhone(value: string) { return String(value || '').trim().replace(/[\s()-]/g, ''); }
function conversationId(a: string, b: string) { return [a, b].sort().join('_'); }

export async function POST(req: Request) {
  try {
    const key = authorizedKey(req);
    const data = await readData();
    const apiKey = data.apiKeys.find((k: any) => k.token === key && k.enabled !== false);
    if (!apiKey || !(hasScope(apiKey, 'app_messages:send') || hasScope(apiKey, 'messages:send'))) return NextResponse.json({ ok: false, error: 'Unauthorized' }, { status: 401 });
    const body = await req.json().catch(() => ({}));
    const senderId = String(body.sender_id || '').trim();
    const to = normalizePhone(body.to);
    const text = String(body.message ?? body.text ?? '').trim();
    if (!senderId || !to || !text) return NextResponse.json({ ok: false, error: 'sender_id, to and message are required' }, { status: 400 });
    if (!/^\+?[1-9]\d{6,14}$/.test(to)) return NextResponse.json({ ok: false, error: 'Invalid international recipient number' }, { status: 400 });
    if (text.length > 4000) return NextResponse.json({ ok: false, error: 'Message is too long (maximum 4000 characters)' }, { status: 400 });

    const sender = data.senderIds.find((s: any) => s.enabled !== false && (s.id === senderId || s.providerSenderId === senderId || s.name === senderId));
    if (!sender) return NextResponse.json({ ok: false, error: 'Sender ID not found or disabled' }, { status: 400 });
    const db = firebaseDb();
    const users = await db.collection('profiles').where('phoneNumber', '==', to).limit(2).get();
    if (users.empty) return NextResponse.json({ ok: false, error: 'Recipient does not have Messages SOM' }, { status: 404 });
    if (users.size > 1) return NextResponse.json({ ok: false, error: 'Recipient number is linked to multiple Messages SOM accounts' }, { status: 409 });
    const recipientDoc = users.docs[0];
    const recipient = recipientDoc.data();
    if (recipient.enabled === false) return NextResponse.json({ ok: false, error: 'Recipient Messages SOM account is disabled' }, { status: 403 });

    const senderUid = `api:${apiKey.id || 'app'}`;
    const recipientUid = recipientDoc.id;
    const externalSenderId = String(sender.providerSenderId || sender.name || senderId);
    const externalSenderName = String(sender.name || senderId);
    const conversation = conversationId(senderUid, recipientUid);
    const messageRef = db.collection('appConversations').doc(conversation).collection('messages').doc();
    const now = Date.now();
    const message = { id: messageRef.id, senderUid, senderId: externalSenderId, senderName: externalSenderName, recipientUid, recipientId: recipient.messagesId || '', text, createdAt: now, deliveredAt: 0, readAt: 0, deleted: false, source: 'api' };
    const conversationRef = db.collection('appConversations').doc(conversation);
    const senderProfileRef = db.collection('profiles').doc(senderUid);
    await db.runTransaction(async tx => {
      tx.set(senderProfileRef, { uid: senderUid, messagesId: externalSenderId, displayName: externalSenderName, phoneNumber: '', photoUrl: '', fcmToken: '', enabled: true, allowReplies: sender.allowReplies !== false, updatedAt: now }, { merge: true });
      tx.set(messageRef, message);
      tx.set(conversationRef, { participants: [senderUid, recipientUid], externalSenderId, lastText: text, lastAt: now, lastSenderUid: senderUid, updatedAt: now }, { merge: true });
    });

    let notification = 'not_sent';
    if (recipient.fcmToken) {
      try {
        await firebaseMessaging().send({ token: String(recipient.fcmToken), notification: { title: externalSenderName, body: text }, data: { type: 'app_message', conversationId: conversation, messageId: messageRef.id, senderId: externalSenderId, senderName: externalSenderName, allowReplies: String(sender.allowReplies !== false) }, android: { priority: 'high', notification: { channelId: 'chat' } } });
        notification = 'sent';
      } catch (e: any) {
        notification = 'failed';
        if (String(e?.code || '').includes('registration-token-not-registered')) await recipientDoc.ref.update({ fcmToken: '' });
      }
    }
    return NextResponse.json({ ok: true, data: { id: messageRef.id, status: 'sent', notification, recipient: recipient.messagesId || null, sender_id: externalSenderId } });
  } catch (e: any) {
    return NextResponse.json({ ok: false, error: e?.message || 'App message failed' }, { status: 500 });
  }
}
