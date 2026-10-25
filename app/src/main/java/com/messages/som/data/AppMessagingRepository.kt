package com.messages.som.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale
import java.util.UUID

/**
 * Native Messages SOM app-to-app messaging.
 *
 * This is NOT carrier SMS and never uses an SMS provider. Messages are exchanged
 * only between authenticated Messages SOM installations through Firestore.
 * Firebase Cloud Messaging is used separately for delivery notifications.
 */
class AppMessagingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class AppProfile(
        val uid: String = "",
        val messagesId: String = "",
        val displayName: String = "",
        val photoUrl: String = "",
        val fcmToken: String = "",
        val enabled: Boolean = true,
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L
    )

    data class AppMessage(
        val id: String = "",
        val senderUid: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val recipientUid: String = "",
        val recipientId: String = "",
        val text: String = "",
        val createdAt: Long = 0L,
        val deliveredAt: Long = 0L,
        val readAt: Long = 0L,
        val deleted: Boolean = false
    )

    data class Conversation(
        val id: String = "",
        val otherUid: String = "",
        val otherId: String = "",
        val otherName: String = "",
        val lastText: String = "",
        val lastAt: Long = 0L,
        val unread: Long = 0L
    )

    fun ensureSignedIn(onResult: (Result<AppProfile>) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            ensureProfile(current.uid, current.displayName.orEmpty(), onResult)
            return
        }
        auth.signInAnonymously().addOnSuccessListener {
            val user = auth.currentUser
            if (user == null) onResult(Result.failure(IllegalStateException("Firebase authentication failed")))
            else ensureProfile(user.uid, user.displayName.orEmpty(), onResult)
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun ensureProfile(uid: String, currentName: String, onResult: (Result<AppProfile>) -> Unit) {
        val ref = db.collection("profiles").document(uid)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                val profile = snap.toObject(AppProfile::class.java) ?: AppProfile(uid = uid)
                updateToken(uid) { onResult(Result.success(profile)) }
                return@addOnSuccessListener
            }
            val id = "MSOM-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
            val now = System.currentTimeMillis()
            val profile = AppProfile(uid, id, currentName.ifBlank { "Messages SOM user" }, "", "", true, now, now)
            ref.set(profile).addOnSuccessListener { onResult(Result.success(profile)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun updateDisplayName(name: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val value = name.trim().take(60)
        if (value.isBlank()) { onResult(Result.failure(IllegalArgumentException("Name is required"))); return }
        db.collection("profiles").document(uid).update(
            mapOf("displayName" to value, "updatedAt" to FieldValue.serverTimestamp())
        ).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        db.collection("profiles").document(uid).update(
            mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp())
        )
    }

    private fun updateToken(uid: String, done: () -> Unit) {
        val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
        token.addOnSuccessListener { value ->
            if (value.isNotBlank()) db.collection("profiles").document(uid).update("fcmToken", value, "updatedAt", FieldValue.serverTimestamp())
            done()
        }.addOnFailureListener { done() }
    }

    fun searchByMessagesId(value: String, onResult: (Result<List<AppProfile>>) -> Unit) {
        val q = value.trim().uppercase(Locale.US)
        if (q.length < 5) { onResult(Result.success(emptyList())); return }
        db.collection("profiles").whereEqualTo("messagesId", q).limit(10).get()
            .addOnSuccessListener { onResult(Result.success(it.documents.mapNotNull { d -> d.toObject(AppProfile::class.java) })) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun send(recipient: AppProfile, text: String, onResult: (Result<String>) -> Unit) {
        val sender = auth.currentUser
        if (sender == null) { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val body = text.trim()
        if (body.isBlank()) { onResult(Result.failure(IllegalArgumentException("Message is empty"))); return }
        if (body.length > 4000) { onResult(Result.failure(IllegalArgumentException("Message is too long"))); return }

        db.collection("profiles").document(sender.uid).get().addOnSuccessListener { snap ->
            val me = snap.toObject(AppProfile::class.java)
            if (me == null || !me.enabled) { onResult(Result.failure(IllegalStateException("Your Messages SOM account is disabled"))); return@addOnSuccessListener }
            if (!recipient.enabled) { onResult(Result.failure(IllegalStateException("This user is disabled"))); return@addOnSuccessListener }

            val conversation = conversationId(sender.uid, recipient.uid)
            val msgRef = db.collection("appConversations").document(conversation).collection("messages").document()
            val now = System.currentTimeMillis()
            val message = AppMessage(
                id = msgRef.id,
                senderUid = sender.uid,
                senderId = me.messagesId,
                senderName = me.displayName,
                recipientUid = recipient.uid,
                recipientId = recipient.messagesId,
                text = body,
                createdAt = now
            )
            val conversationRef = db.collection("appConversations").document(conversation)
            val updates = hashMapOf<String, Any>(
                "participants" to listOf(sender.uid, recipient.uid),
                "lastText" to body,
                "lastAt" to now,
                "lastSenderUid" to sender.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.runBatch { batch ->
                batch.set(msgRef, message)
                batch.set(conversationRef, updates, com.google.firebase.firestore.SetOptions.merge())
            }.addOnSuccessListener { onResult(Result.success(msgRef.id)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun observeConversation(otherUid: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration? {
        val me = auth.currentUser?.uid ?: return null
        val id = conversationId(me, otherUid)
        return db.collection("appConversations").document(id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                onChange(snapshot?.documents.orEmpty().mapNotNull { it.toObject(AppMessage::class.java) }.filter { !it.deleted })
            }
    }

    fun markRead(otherUid: String) {
        val me = auth.currentUser?.uid ?: return
        val id = conversationId(me, otherUid)
        db.collection("appConversations").document(id).collection("messages")
            .whereEqualTo("recipientUid", me).whereEqualTo("readAt", 0L).limit(100)
            .get().addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { batch.update(it.reference, "readAt", System.currentTimeMillis()) }
                batch.commit()
            }
    }

    fun deleteMessage(otherUid: String, messageId: String, onResult: (Result<Unit>) -> Unit) {
        val me = auth.currentUser?.uid
        if (me == null) { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val id = conversationId(me, otherUid)
        db.collection("appConversations").document(id).collection("messages").document(messageId)
            .update("deleted", true).addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
