const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
initializeApp();
const db = getFirestore();
exports.notifyNewAppMessage = onDocumentCreated('appConversations/{conversationId}/messages/{messageId}', async (event) => {
  const message = event.data?.data();
  if (!message || message.source === 'external_app' || !message.recipientUid || !message.senderUid || message.senderUid === message.recipientUid) return;
  const profile = await db.collection('profiles').doc(message.recipientUid).get();
  if (!profile.exists) return;
  const token = profile.data()?.fcmToken;
  if (!token) return;
  const title = message.senderName || message.senderId || 'Messages SOM';
  const body = String(message.text || '').slice(0, 180);
  try {
    await getMessaging().send({
      token,
      notification: { title, body },
      data: {
        type: 'app_message', conversationId: String(event.params.conversationId), messageId: String(event.params.messageId),
        senderUid: String(message.senderUid), senderId: String(message.senderId || ''), senderName: String(message.senderName || ''),
        allowReplies: String(message.allowReplies !== false)
      },
      android: { priority: 'high', notification: { channelId: 'chat' } }
    });
    await event.data.ref.update({ deliveredAt: Date.now() });
  } catch (error) {
    const code = error?.code || '';
    if (code.includes('registration-token-not-registered') || code.includes('invalid-registration-token')) await profile.ref.update({ fcmToken: '' });
    else console.error('Messages SOM FCM delivery failed', error);
  }
});
