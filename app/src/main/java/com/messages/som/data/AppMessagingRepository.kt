package com.messages.som.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class AppMessage(val conversationId:String="", val senderId:String="", val recipientId:String="", val text:String="", val createdAt:Long=System.currentTimeMillis())

class AppMessagingRepository(private val auth:FirebaseAuth=FirebaseAuth.getInstance(), private val db:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    fun send(message:AppMessage, onDone:(Boolean)->Unit={}) { val ref=db.collection("conversations").document(message.conversationId).collection("messages"); ref.add(message).addOnCompleteListener{onDone(it.isSuccessful)} }
    fun observe(conversationId:String, onMessage:(List<AppMessage>)->Unit) = db.collection("conversations").document(conversationId).collection("messages").orderBy("createdAt").addSnapshotListener { snap,error -> if(error==null) onMessage(snap?.documents?.mapNotNull{it.toObject(AppMessage::class.java)}?:emptyList()) }
    fun uid():String?=auth.currentUser?.uid
}
