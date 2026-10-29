import { NextResponse } from 'next/server';
import crypto from 'node:crypto';
import { firebaseDb, firebaseMessaging } from '../../../../lib/firebase-admin';

function uidOf(installation: string) { return 'app:' + crypto.createHash('sha256').update(installation).digest('hex').slice(0, 40); }
function apiUid(senderId: string) { return 'api:' + crypto.createHash('sha256').update(senderId).digest('hex').slice(0, 40); }
function conversationId(a: string, b: string) { return [a, b].sort().join('_'); }
function normalizeNumber(value: unknown) { return String(value ?? '').trim().replace(/[\s()-]/g, ''); }
function authorizedBridge(req: Request) {
  const expected = process.env.MESSAGES_BRIDGE_API_KEY?.trim();
  const header = req.headers.get('authorization') || '';
  const supplied = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!expected || !supplied) return false;
  const a = Buffer.from(expected); const b = Buffer.from(supplied);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function publicProfile(data: any) {
  return { uid: String(data.uid || ''), messagesId: String(data.messagesId || ''), displayName: String(data.displayName || 'Messages SOM user'), phoneNumber: String(data.phoneNumber || ''), photoUrl: String(data.photoUrl || ''), fcmToken: '', enabled: data.enabled !== false, createdAt: Number(data.createdAt || 0), updatedAt: Number(data.updatedAt || 0), allowReplies: data.allowReplies !== false };
}

export async function POST(req: Request) {
  if (!authorizedBridge(req)) return NextResponse.json({ ok: false, error: 'Unauthorized bridge request' }, { status: 401 });
  try {
    const body = await req.json().catch(() => ({}));
    const op = String(body.op || '').trim();
    const db = firebaseDb();

    if (op === 'health') {
      await db.collection('profiles').limit(1).get();
      return NextResponse.json({ ok: true, data: { firebase: 'ready', project: process.env.FIREBASE_PROJECT_ID || '' } });
    }

    if (op === 'register') {
      const installation = String(body.installation_id || '').trim();
      if (installation.length < 32) return NextResponse.json({ ok: false, error: 'Invalid installation identity' }, { status: 400 });
      const uid = uidOf(installation); const ref = db.collection('profiles').doc(uid); const currentSnap = await ref.get(); const current = currentSnap.exists ? currentSnap.data() || {} : {};
      const phone = normalizeNumber(body.phone_number);
      if (phone && !/^\+?[1-9]\d{6,14}$/.test(phone)) return NextResponse.json({ ok: false, error: 'Invalid phone number' }, { status: 400 });
      if (phone) { const dup = await db.collection('profiles').where('phoneNumber', '==', phone).limit(2).get(); if (dup.docs.some(d => d.id !== uid)) return NextResponse.json({ ok: false, error: 'This phone number is already linked to another Messages SOM account' }, { status: 409 }); }
      const now = Date.now(); const messagesId = String(current.messagesId || ('MSOM-' + crypto.randomBytes(4).toString('hex').toUpperCase()));
      const data = { uid, messagesId, displayName: String(body.display_name || current.displayName || 'Messages SOM user').trim().slice(0, 60) || 'Messages SOM user', phoneNumber: phone || String(current.phoneNumber || ''), photoUrl: String(current.photoUrl || ''), fcmToken: String(body.fcm_token || current.fcmToken || '').trim(), enabled: current.enabled !== false, createdAt: Number(current.createdAt || now), updatedAt: now, allowReplies: current.allowReplies !== false, installationIdHash: crypto.createHash('sha256').update(installation).digest('hex') };
      await ref.set(data, { merge: true });
      return NextResponse.json({ ok: true, data: publicProfile(data) });
    }

    if (op === 'link_phone') {
      const installation = String(body.installation_id || '').trim(); const phone = normalizeNumber(body.phone_number);
      if (installation.length < 32 || !/^\+?[1-9]\d{6,14}$/.test(phone)) return NextResponse.json({ ok: false, error: 'Use a valid international number, e.g. +2519XXXXXXXX' }, { status: 400 });
      const uid = uidOf(installation); const ref = db.collection('profiles').doc(uid); const snap = await ref.get();
      if (!snap.exists) return NextResponse.json({ ok: false, error: 'Messages SOM account is not registered' }, { status: 401 });
      const dup = await db.collection('profiles').where('phoneNumber', '==', phone).limit(2).get();
      if (dup.docs.some(d => d.id !== uid)) return NextResponse.json({ ok: false, error: 'This phone number is already linked to another Messages SOM account' }, { status: 409 });
      await ref.update({ phoneNumber: phone, updatedAt: Date.now() });
      return NextResponse.json({ ok: true, data: publicProfile((await ref.get()).data()) });
    }

    if (op === 'search') {
      const installation = String(body.installation_id || '').trim(); const query = String(body.query || '').trim();
      if (installation.length < 32 || query.length < 5) return NextResponse.json({ ok: true, data: [] });
      const me = uidOf(installation); const normalized = normalizeNumber(query); const looksPhone = /^\+?[0-9]{5,15}$/.test(normalized); let docs: any[] = [];
      if (looksPhone) docs = (await db.collection('profiles').where('phoneNumber', '==', normalized).limit(10).get()).docs;
      else docs = (await db.collection('profiles').where('messagesId', '==', query.toUpperCase()).limit(10).get()).docs;
      return NextResponse.json({ ok: true, data: docs.filter(d => d.id !== me && d.data().enabled !== false).map(d => publicProfile(d.data())) });
    }

    if (op === 'send') {
      const installation = String(body.installation_id || '').trim(); const recipientUid = String(body.recipient_uid || '').trim(); const text = String(body.message ?? body.text ?? '').trim();
      if (installation.length < 32 || !recipientUid || !text) return NextResponse.json({ ok: false, error: 'Invalid message request' }, { status: 400 });
      if (text.length > 4000) return NextResponse.json({ ok: false, error: 'Message is too long' }, { status: 400 });
      const senderUid = uidOf(installation); const [ss, rs] = await Promise.all([db.collection('profiles').doc(senderUid).get(), db.collection('profiles').doc(recipientUid).get()]);
      if (!ss.exists) return NextResponse.json({ ok: false, error: 'Messages SOM account is not registered' }, { status: 401 });
      if (!rs.exists || rs.data()?.enabled === false) return NextResponse.json({ ok: false, error: 'Messages SOM user not found' }, { status: 404 });
      const sender = ss.data()!; const recipient = rs.data()!;
      if (sender.enabled === false) return NextResponse.json({ ok: false, error: 'Your Messages SOM account is disabled' }, { status: 403 });
      const token = String(recipient.fcmToken || '').trim(); if (!token) return NextResponse.json({ ok: false, error: 'Recipient is not currently reachable' }, { status: 503 });
      const conversation = conversationId(senderUid, recipientUid); const conversationRef = db.collection('appConversations').doc(conversation); const messageRef = conversationRef.collection('messages').doc(); const now = Date.now();
      const message = { id: messageRef.id, senderUid, senderId: sender.messagesId || '', senderName: sender.displayName || 'Messages SOM user', recipientUid, recipientId: recipient.messagesId || '', text, createdAt: now, deliveredAt: 0, readAt: 0, deleted: false, source: 'messages_som', allowReplies: true };
      await db.runTransaction(async tx => { tx.set(messageRef, message); tx.set(conversationRef, { participants: [senderUid, recipientUid], lastText: text, lastAt: now, lastSenderUid: senderUid, updatedAt: now }, { merge: true }); });
      try {
        await firebaseMessaging().send({ token, data: { type: 'app_message', title: sender.displayName || sender.messagesId || 'Messages SOM', body: text.slice(0, 180), conversationId: conversation, messageId: messageRef.id, senderUid, senderId: sender.messagesId || '', senderName: sender.displayName || '', allowReplies: 'true' }, android: { priority: 'high' } });
        await messageRef.update({ deliveredAt: Date.now() }); return NextResponse.json({ ok: true, data: { id: messageRef.id, status: 'delivered' } });
      } catch (e: any) { const code = String(e?.code || ''); if (code.includes('registration-token-not-registered') || code.includes('invalid-registration-token')) await ss.ref.update({ fcmToken: '' }); return NextResponse.json({ ok: false, error: 'Message notification delivery failed', messageId: messageRef.id }, { status: 502 }); }
    }

    if (op === 'external_send') {
      const senderId = String(body.sender_id || '').trim(); const senderName = String(body.sender_name || senderId).trim().slice(0, 60) || senderId; const to = normalizeNumber(body.to); const text = String(body.message ?? '').trim(); const allowReplies = body.allow_replies === true;
      if (!senderId || !/^\+?[1-9]\d{6,14}$/.test(to) || !text) return NextResponse.json({ ok: false, error: 'sender_id, to and message are required' }, { status: 400 });
      if (text.length > 4000) return NextResponse.json({ ok: false, error: 'Message is too long' }, { status: 400 });
      const users = await db.collection('profiles').where('phoneNumber', '==', to).limit(2).get(); if (users.empty) return NextResponse.json({ ok: false, error: 'User does not have Messages SOM' }, { status: 404 }); if (users.size > 1) return NextResponse.json({ ok: false, error: 'Recipient number is linked to multiple Messages SOM accounts' }, { status: 409 });
      const recipientDoc = users.docs[0]; const recipient = recipientDoc.data(); if (recipient.enabled === false) return NextResponse.json({ ok: false, error: 'Recipient Messages SOM account is disabled' }, { status: 403 }); const token = String(recipient.fcmToken || '').trim(); if (!token) return NextResponse.json({ ok: false, error: 'Recipient is not currently reachable' }, { status: 503 });
      const senderUid = apiUid(senderId); const conversation = conversationId(senderUid, recipientDoc.id); const conversationRef = db.collection('appConversations').doc(conversation); const messageRef = conversationRef.collection('messages').doc(); const senderProfileRef = db.collection('profiles').doc(senderUid); const now = Date.now();
      const message = { id: messageRef.id, senderUid, senderId, senderName, recipientUid: recipientDoc.id, recipientId: recipient.messagesId || '', text, createdAt: now, deliveredAt: 0, readAt: 0, deleted: false, source: 'external_app', allowReplies };
      await db.runTransaction(async tx => { tx.set(senderProfileRef, { uid: senderUid, messagesId: senderId, displayName: senderName, phoneNumber: '', photoUrl: '', fcmToken: '', enabled: true, allowReplies, updatedAt: now }, { merge: true }); tx.set(messageRef, message); tx.set(conversationRef, { participants: [senderUid, recipientDoc.id], externalSenderId: senderId, lastText: text, lastAt: now, lastSenderUid: senderUid, updatedAt: now }, { merge: true }); });
      try { await firebaseMessaging().send({ token, data: { type: 'app_message', title: senderName || senderId, body: text.slice(0, 180), conversationId: conversation, messageId: messageRef.id, senderUid, senderId, senderName, allowReplies: String(allowReplies) }, android: { priority: 'high' } }); await messageRef.update({ deliveredAt: Date.now() }); return NextResponse.json({ ok: true, data: { id: messageRef.id, status: 'delivered', recipient: recipient.messagesId || null, sender_id: senderId, allow_replies: allowReplies } }); }
      catch { return NextResponse.json({ ok: false, error: 'Message notification delivery failed', messageId: messageRef.id }, { status: 502 }); }
    }

    if (op === 'conversation') {
      const installation = String(body.installation_id || '').trim(); const other = String(body.other_uid || '').trim(); if (installation.length < 32 || !other) return NextResponse.json({ ok: false, error: 'Invalid conversation request' }, { status: 400 });
      const id = conversationId(uidOf(installation), other); const snap = await db.collection('appConversations').doc(id).collection('messages').orderBy('createdAt', 'asc').limitToLast(500).get(); return NextResponse.json({ ok: true, data: snap.docs.map(d => d.data()).filter((x: any) => x.deleted !== true) });
    }

    if (op === 'read') {
      const installation = String(body.installation_id || '').trim(); const other = String(body.other_uid || '').trim(); if (installation.length < 32 || !other) return NextResponse.json({ ok: false, error: 'Invalid read request' }, { status: 400 });
      const me = uidOf(installation); const id = conversationId(me, other); const snap = await db.collection('appConversations').doc(id).collection('messages').where('recipientUid', '==', me).where('readAt', '==', 0).limit(100).get();
      if (!snap.empty) { const batch = db.batch(); snap.docs.forEach(d => batch.update(d.ref, 'readAt', Date.now())); await batch.commit(); }
      return NextResponse.json({ ok: true, data: { updated: snap.size } });
    }

    return NextResponse.json({ ok: false, error: 'Unknown bridge operation' }, { status: 400 });
  } catch (error: any) { console.error('Messages SOM bridge error', error); return NextResponse.json({ ok: false, error: error?.message || 'Messaging bridge failed' }, { status: 500 }); }
}
