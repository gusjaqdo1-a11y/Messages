package com.messages.som.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Firebase-ready app-to-app messaging layer. Configure Auth + Firestore in your Firebase project. */
class AppMessagingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    data class AppMessage(
        val id: String = "",
        val senderId: String = "",
        val recipientId: String = "",
        val text: String = "",
        val createdAt: Long = System.currentTimeMillis(),
        val delivered: Boolean = false,
        val read: Boolean = false
    )

    fun send(recipientId: String, text: String, onResult: (Result<String>) -> Unit) {
        val sender = auth.currentUser?.uid
        if (sender.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Firebase user is not signed in")))
            return
        }
        val ref = db.collection("appMessages").document()
        val message = AppMessage(ref.id, sender, recipientId, text)
        ref.set(message).addOnSuccessListener { onResult(Result.success(ref.id)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun observeConversation(otherUserId: String, onChange: (List<AppMessage>) -> Unit, onError: (Exception) -> Unit) =
        db.collection("appMessages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                val me = auth.currentUser?.uid ?: return@addSnapshotListener
                val messages = snapshot?.documents.orEmpty().mapNotNull { it.toObject(AppMessage::class.java) }
                    .filter { (it.senderId == me && it.recipientId == otherUserId) || (it.senderId == otherUserId && it.recipientId == me) }
                onChange(messages)
            }
}
