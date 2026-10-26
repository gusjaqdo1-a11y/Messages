package com.messages.som.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import java.util.UUID

class AppMessagingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class AppProfile(
        val uid: String = "", val messagesId: String = "", val displayName: String = "",
        val phoneNumber: String = "", val photoUrl: String = "", val fcmToken: String = "", val enabled: Boolean = true,
        val createdAt: Long = 0L, val updatedAt: Long = 0L, val allowReplies: Boolean = true
    )
    data class AppMessage(
        val id: String = "", val senderUid: String = "", val senderId: String = "", val senderName: String = "",
        val recipientUid: String = "", val recipientId: String = "", val text: String = "",
        val createdAt: Long = 0L, val deliveredAt: Long = 0L, val readAt: Long = 0L, val deleted: Boolean = false
    )

    private fun withFirestoreNetwork(action: () -> Unit) {
        db.enableNetwork().addOnCompleteListener { action() }
    }

    fun ensureSignedIn(onResult: (Result<AppProfile>) -> Unit) {
        withFirestoreNetwork {
            val current = auth.currentUser
            if (current != null) {
                ensureProfile(current.uid, current.displayName.orEmpty(), onResult)
                return@withFirestoreNetwork
            }
            auth.signInAnonymously()
                .addOnSuccessListener { user ->
                    if (user == null) onResult(Result.failure(IllegalStateException("Firebase authentication failed")))
                    else ensureProfile(user.uid, user.displayName.orEmpty(), onResult)
                }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    private fun ensureProfile(uid: String, currentName: String, onResult: (Result<AppProfile>) -> Unit) {
        val ref = db.collection("profiles").document(uid)
        ref.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val profile = snap.toObject(AppProfile::class.java) ?: AppProfile(uid = uid)
                    updateToken(uid) { onResult(Result.success(profile)) }
                    return@addOnSuccessListener
                }
                val id = "MSOM-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
                val now = System.currentTimeMillis()
                val profile = AppProfile(uid, id, currentName.ifBlank { "Messages SOM user" }, "", "", "", true, now, now, true)
                ref.set(profile)
                    .addOnSuccessListener { onResult(Result.success(profile)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(friendlyFirestoreError(error)))
            }
    }

    private fun friendlyFirestoreError(error: Exception): Exception {
        val message = error.message.orEmpty()
        return if (message.contains("client is offline", true)) {
            IllegalStateException("Messages SOM cannot reach Firebase right now. Check internet access and try again.", error)
        } else error
    }

    fun updatePhoneNumber(phone: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val value = phone.trim().replace(Regex("[\\s()-]"), "")
        if (!Regex("^\\+?[1-9]\\d{6,14}$").matches(value)) {
            onResult(Result.failure(IllegalArgumentException("Use an international number, e.g. +2519XXXXXXXX")))
            return
        }
        withFirestoreNetwork {
            db.collection("profiles").whereEqualTo("phoneNumber", value).limit(1).get()
                .addOnSuccessListener { existing ->
                    if (!existing.isEmpty && existing.documents.first().id != uid) {
                        onResult(Result.failure(IllegalStateException("This number is already linked to Messages SOM")))
                        return@addOnSuccessListener
                    }
                    db.collection("profiles").document(uid)
                        .update("phoneNumber", value, "updatedAt", System.currentTimeMillis())
                        .addOnSuccessListener { onResult(Result.success(Unit)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                }
                .addOnFailureListener { onResult(Result.failure(friendlyFirestoreError(it))) }
        }
    }

    fun updateDisplayName(name: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val value = name.trim().take(60)
        if (value.isBlank()) { onResult(Result.failure(IllegalArgumentException("Name is required"))); return }
        withFirestoreNetwork {
            db.collection("profiles").document(uid).update("displayName", value, "updatedAt", System.currentTimeMillis())
                .addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }

    fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        withFirestoreNetwork { db.collection("profiles").document(uid).update("fcmToken", token, "updatedAt", System.currentTimeMillis()) }
    }

    private fun updateToken(uid: String, done: () -> Unit) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { value ->
                if (value.isNotBlank()) db.collection("profiles").document(uid).update("fcmToken", value, "updatedAt", System.currentTimeMillis())
                done()
            }
            .addOnFailureListener { done() }
    }

    fun searchByMessagesId(value: String, onResult: (Result<List<AppProfile>>) -> Unit) {
        val q = value.trim().uppercase(Locale.US)
        if (q.length < 5) { onResult(Result.success(emptyList())); return }
        withFirestoreNetwork {
            db.collection("profiles").whereEqualTo("messagesId", q).limit(10).get()
                .addOnSuccessListener { onResult(Result.success(it.documents.mapNotNull { d -> d.toObject(AppProfile::class.java) })) }
                .addOnFailureListener { onResult(Result.failure(friendlyFirestoreError(it))) }
        }
    }

    fun searchByPhone(value: String, onResult: (Result<List<AppProfile>>) -> Unit) {
        val q = value.trim().replace(Regex("[\\s()-]"), "")
        if (q.length < 7) { onResult(Result.success(emptyList())); return }
        withFirestoreNetwork {
            db.collection("profiles").whereEqualTo("phoneNumber", q).limit(10).get()
                .addOnSuccessListener { onResult(Result.success(it.documents.mapNotNull { d -> d.toObject(AppProfile::class.java) })) }
                .addOnFailureListener { onResult(Result.failure(friendlyFirestoreError(it))) }
        }
    }

    private fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun send(recipient: AppProfile, text: String, onResult: (Result<String>) -> Unit) {
        val sender = auth.currentUser ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        if (!recipient.allowReplies) { onResult(Result.failure(IllegalStateException("Replies are disabled for this Sender ID"))); return }
        val body = text.trim()
        if (body.isBlank()) { onResult(Result.failure(IllegalArgumentException("Message is empty"))); return }
        if (body.length > 4000) { onResult(Result.failure(IllegalArgumentException("Message is too long"))); return }
        withFirestoreNetwork {
            db.collection("profiles").document(sender.uid).get()
                .addOnSuccessListener { snap ->
                    val me = snap.toObject(AppProfile::class.java)
                    if (me == null || !me.enabled) { onResult(Result.failure(IllegalStateException("Your Messages SOM account is disabled"))); return@addOnSuccessListener }
                    if (!recipient.enabled) { onResult(Result.failure(IllegalStateException("This user is disabled"))); return@addOnSuccessListener }
                    val conversation = conversationId(sender.uid, recipient.uid)
                    val msgRef = db.collection("appConversations").document(conversation).collection("messages").document()
                    val now = System.currentTimeMillis()
                    val message = AppMessage(msgRef.id, sender.uid, me.messagesId, me.displayName, recipient.uid, recipient.messagesId, body, now)
                    val conversationRef = db.collection("appConversations").document(conversation)
                    val updates = hashMapOf<String, Any>("participants" to listOf(sender.uid, recipient.uid), "lastText" to body, "lastAt" to now, "lastSenderUid" to sender.uid, "updatedAt" to now)
                    db.runBatch { batch -> batch.set(msgRef, message); batch.set(conversationRef, updates, SetOptions.merge()) }
                        .addOnSuccessListener { onResult(Result.success(msgRef.id)) }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                }
                .addOnFailureListener { onResult(Result.failure(friendlyFirestoreError(it))) }
        }
    }

    fun observeConversation(otherUid: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration? {
        val me = auth.currentUser?.uid ?: return null
        return observeConversationId(conversationId(me, otherUid), onChange, onError)
    }

    fun observeConversationId(id: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration? {
        withFirestoreNetwork { }
        val listener = db.collection("appConversations").document(id).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(friendlyFirestoreError(error)); return@addSnapshotListener }
                onChange(snapshot?.documents.orEmpty().mapNotNull { it.toObject(AppMessage::class.java) }.filter { !it.deleted })
            }
        return listener
    }

    fun markRead(otherUid: String) { val me = auth.currentUser?.uid ?: return; markReadConversation(conversationId(me, otherUid)) }

    fun markReadConversation(id: String) {
        val me = auth.currentUser?.uid ?: return
        withFirestoreNetwork {
            db.collection("appConversations").document(id).collection("messages")
                .whereEqualTo("recipientUid", me).whereEqualTo("readAt", 0L).limit(100).get()
                .addOnSuccessListener { snap -> val batch = db.batch(); snap.documents.forEach { batch.update(it.reference, "readAt", System.currentTimeMillis()) }; batch.commit() }
        }
    }

    fun deleteMessage(otherUid: String, messageId: String, onResult: (Result<Unit>) -> Unit) {
        val me = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val id = conversationId(me, otherUid)
        withFirestoreNetwork {
            db.collection("appConversations").document(id).collection("messages").document(messageId).update("deleted", true)
                .addOnSuccessListener { onResult(Result.success(Unit)) }
                .addOnFailureListener { onResult(Result.failure(it)) }
        }
    }
}
