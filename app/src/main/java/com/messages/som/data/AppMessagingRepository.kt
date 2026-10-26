package com.messages.som.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun withFirestoreNetwork(action: () -> Unit, attempt: Int = 0) {
        db.enableNetwork()
            .addOnSuccessListener { action() }
            .addOnFailureListener {
                if (attempt < 3) {
                    mainHandler.postDelayed({ withFirestoreNetwork(action = action, attempt = attempt + 1) }, 750L * (attempt + 1))
                } else {
                    action()
                }
            }
    }

    private fun isTransient(error: Throwable): Boolean =
        (error is FirebaseFirestoreException && error.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED
        )) || error.message?.contains("offline", true) == true

    private fun <T> runFirestore(
        operation: () -> com.google.android.gms.tasks.Task<T>,
        onResult: (Result<T>) -> Unit,
        attempt: Int = 0
    ) {
        withFirestoreNetwork(action = {
            operation().addOnSuccessListener { onResult(Result.success(it)) }.addOnFailureListener { error ->
                if (attempt < 3 && isTransient(error)) {
                    mainHandler.postDelayed({ runFirestore(operation, onResult, attempt + 1) }, 1000L * (attempt + 1))
                } else {
                    onResult(Result.failure(error))
                }
            }
        })
    }

    fun ensureSignedIn(onResult: (Result<AppProfile>) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            ensureProfile(current.uid, current.displayName.orEmpty(), onResult)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) ensureProfile(user.uid, user.displayName.orEmpty(), onResult)
                else onResult(Result.failure(IllegalStateException("Firebase sign-in returned no user")))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun ensureProfile(uid: String, currentName: String, onResult: (Result<AppProfile>) -> Unit) {
        val ref = db.collection("profiles").document(uid)
        runFirestore(operation = { ref.get() }, onResult = { result ->
            result.onSuccess { snap ->
                if (snap.exists()) {
                    val profile = snap.toObject(AppProfile::class.java) ?: AppProfile(uid = uid)
                    updateToken(uid) { onResult(Result.success(profile)) }
                    return@onSuccess
                }
                val id = "MSOM-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.US)
                val now = System.currentTimeMillis()
                val profile = AppProfile(uid, id, currentName.ifBlank { "Messages SOM user" }, "", "", "", true, now, now, true)
                runFirestore(operation = { ref.set(profile) }, onResult = { write -> onResult(write.map { profile }) })
            }.onFailure { onResult(Result.failure(friendlyFirestoreError(it))) }
        })
    }

    private fun friendlyFirestoreError(error: Throwable): Exception {
        val message = error.message.orEmpty()
        if (message.contains("client is offline", true)) {
            return IllegalStateException("Messages SOM cannot reach Firebase right now. Check internet access and try again.", error)
        }
        return if (error is Exception) error else IllegalStateException(message.ifBlank { "Firebase operation failed" }, error)
    }

    fun updatePhoneNumber(phone: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val value = phone.trim().replace(Regex("[\\s()-]"), "")
        if (!Regex("^\\+?[1-9]\\d{6,14}$").matches(value)) { onResult(Result.failure(IllegalArgumentException("Use an international number, e.g. +2519XXXXXXXX"))); return }
        runFirestore(operation = { db.collection("profiles").whereEqualTo("phoneNumber", value).limit(1).get() }, onResult = { result ->
            result.onSuccess { existing ->
                if (!existing.isEmpty && existing.documents.first().id != uid) { onResult(Result.failure(IllegalStateException("This number is already linked to Messages SOM"))); return@onSuccess }
                runFirestore(operation = { db.collection("profiles").document(uid).update("phoneNumber", value, "updatedAt", System.currentTimeMillis()) }, onResult = { onResult(it.map { Unit }) })
            }.onFailure { onResult(Result.failure(friendlyFirestoreError(it))) }
        })
    }

    fun updateDisplayName(name: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val value = name.trim().take(60); if (value.isBlank()) { onResult(Result.failure(IllegalArgumentException("Name is required"))); return }
        runFirestore(operation = { db.collection("profiles").document(uid).update("displayName", value, "updatedAt", System.currentTimeMillis()) }, onResult = { onResult(it.map { Unit }) })
    }

    fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        runFirestore(operation = { db.collection("profiles").document(uid).update("fcmToken", token, "updatedAt", System.currentTimeMillis()) }, onResult = { })
    }

    private fun updateToken(uid: String, done: () -> Unit) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { value ->
                if (value.isNotBlank()) runFirestore(operation = { db.collection("profiles").document(uid).update("fcmToken", value, "updatedAt", System.currentTimeMillis()) }, onResult = { done() }) else done()
            }
            .addOnFailureListener { done() }
    }

    fun searchByMessagesId(value: String, onResult: (Result<List<AppProfile>>) -> Unit) {
        val q = value.trim().uppercase(Locale.US)
        if (q.length < 5) { onResult(Result.success(emptyList())); return }
        runFirestore(operation = { db.collection("profiles").whereEqualTo("messagesId", q).limit(10).get() }, onResult = { result ->
            onResult(result.map { it.documents.mapNotNull { d -> d.toObject(AppProfile::class.java) } })
        })
    }

    fun searchByPhone(value: String, onResult: (Result<List<AppProfile>>) -> Unit) {
        val q = value.trim().replace(Regex("[\\s()-]"), "")
        if (q.length < 7) { onResult(Result.success(emptyList())); return }
        runFirestore(operation = { db.collection("profiles").whereEqualTo("phoneNumber", q).limit(10).get() }, onResult = { result ->
            onResult(result.map { it.documents.mapNotNull { d -> d.toObject(AppProfile::class.java) } })
        })
    }

    private fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun send(recipient: AppProfile, text: String, onResult: (Result<String>) -> Unit) {
        val sender = auth.currentUser ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        if (!recipient.allowReplies) { onResult(Result.failure(IllegalStateException("Replies are disabled for this Sender ID"))); return }
        val body = text.trim()
        if (body.isBlank()) { onResult(Result.failure(IllegalArgumentException("Message is empty"))); return }
        if (body.length > 4000) { onResult(Result.failure(IllegalArgumentException("Message is too long"))); return }
        runFirestore(operation = { db.collection("profiles").document(sender.uid).get() }, onResult = { result ->
            result.onSuccess { snap ->
                val me = snap.toObject(AppProfile::class.java)
                if (me == null || !me.enabled) { onResult(Result.failure(IllegalStateException("Your Messages SOM account is disabled"))); return@onSuccess }
                if (!recipient.enabled) { onResult(Result.failure(IllegalStateException("This user is disabled"))); return@onSuccess }
                val conversation = conversationId(sender.uid, recipient.uid)
                val msgRef = db.collection("appConversations").document(conversation).collection("messages").document()
                val now = System.currentTimeMillis()
                val message = AppMessage(msgRef.id, sender.uid, me.messagesId, me.displayName, recipient.uid, recipient.messagesId, body, now)
                val conversationRef = db.collection("appConversations").document(conversation)
                val updates = hashMapOf<String, Any>("participants" to listOf(sender.uid, recipient.uid), "lastText" to body, "lastAt" to now, "lastSenderUid" to sender.uid, "updatedAt" to now)
                runFirestore(operation = { db.runBatch { batch -> batch.set(msgRef, message); batch.set(conversationRef, updates, SetOptions.merge()) } }, onResult = { onResult(it.map { msgRef.id }) })
            }.onFailure { onResult(Result.failure(friendlyFirestoreError(it))) }
        })
    }

    fun observeConversation(otherUid: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration? {
        val me = auth.currentUser?.uid ?: return null
        return observeConversationId(conversationId(me, otherUid), onChange, onError)
    }

    fun observeConversationId(id: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration? {
        var listener: ListenerRegistration? = null
        withFirestoreNetwork(action = {
            listener = db.collection("appConversations").document(id).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(500)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { onError(friendlyFirestoreError(error)); return@addSnapshotListener }
                    onChange(snapshot?.documents.orEmpty().mapNotNull { it.toObject(AppMessage::class.java) }.filter { !it.deleted })
                }
        })
        return listener
    }

    fun markRead(otherUid: String) { val me = auth.currentUser?.uid ?: return; markReadConversation(conversationId(me, otherUid)) }

    fun markReadConversation(id: String) {
        val me = auth.currentUser?.uid ?: return
        runFirestore(operation = { db.collection("appConversations").document(id).collection("messages").whereEqualTo("recipientUid", me).whereEqualTo("readAt", 0L).limit(100).get() }, onResult = { result ->
            result.onSuccess { snap ->
                if (snap.isEmpty) return@onSuccess
                val batch = db.batch()
                snap.documents.forEach { batch.update(it.reference, "readAt", System.currentTimeMillis()) }
                runFirestore(operation = { batch.commit() }, onResult = { })
            }
        })
    }

    fun deleteMessage(otherUid: String, messageId: String, onResult: (Result<Unit>) -> Unit) {
        val me = auth.currentUser?.uid ?: run { onResult(Result.failure(IllegalStateException("Not signed in"))); return }
        val id = conversationId(me, otherUid)
        runFirestore(operation = { db.collection("appConversations").document(id).collection("messages").document(messageId).update("deleted", true) }, onResult = { onResult(it.map { Unit }) })
    }
}
