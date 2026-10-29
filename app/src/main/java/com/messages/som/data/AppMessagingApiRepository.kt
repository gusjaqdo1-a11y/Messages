package com.messages.som.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppMessagingApiRepository(private val context: Context) {
    data class AppProfile(val uid:String="",val messagesId:String="",val displayName:String="",val phoneNumber:String="",val photoUrl:String="",val fcmToken:String="",val enabled:Boolean=true,val createdAt:Long=0L,val updatedAt:Long=0L,val allowReplies:Boolean=true)
    data class AppMessage(val id:String="",val senderUid:String="",val senderId:String="",val senderName:String="",val recipientUid:String="",val recipientId:String="",val text:String="",val createdAt:Long=0L,val deliveredAt:Long=0L,val readAt:Long=0L,val deleted:Boolean=false,val mine:Boolean=false)
    companion object { private const val BASE_URL="https://messages-som-api.vercel.app/api/v1/app-messages"; private const val PREFS="messages_som_network"; private const val INSTALL_ID="installation_id"; private const val FCM_TOKEN="fcm_token" }
    private val executor=Executors.newFixedThreadPool(3)
    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)

    private fun installationId():String {
        val old=prefs.getString(INSTALL_ID,null)
        if(!old.isNullOrBlank()) return old
        val value=UUID.randomUUID().toString()+UUID.randomUUID().toString()
        prefs.edit().putString(INSTALL_ID,value).apply()
        return value
    }

    private fun common()=JSONObject().put("installation_id",installationId())

    private fun request(path:String,body:JSONObject):JSONObject {
        val connection=(URL(BASE_URL+path).openConnection() as HttpURLConnection).apply {
            requestMethod="POST";connectTimeout=15000;readTimeout=20000;doOutput=true
            setRequestProperty("Content-Type","application/json");setRequestProperty("Accept","application/json")
        }
        return try {
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=connection.responseCode
            val stream=if(code in 200..299)connection.inputStream else connection.errorStream
            val text=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
            val json=if(text.isBlank())JSONObject()else JSONObject(text)
            if(code !in 200..299||!json.optBoolean("ok",false)) throw IllegalStateException(json.optString("error").ifBlank{"Messages SOM server error ($code)"})
            json
        } finally { connection.disconnect() }
    }

    fun ensureRegistered(onResult:(Result<AppProfile>)->Unit){
        executor.execute {
            try {
                val token=runCatching{Tasks.await(FirebaseMessaging.getInstance().token,15,TimeUnit.SECONDS)}.getOrElse{prefs.getString(FCM_TOKEN,"").orEmpty()}
                if(token.isNotBlank()) prefs.edit().putString(FCM_TOKEN,token).apply()
                registerWithToken(token,onResult)
            } catch(e:Throwable){onResult(Result.failure(e))}
        }
    }

    fun refreshRegistration(fcmToken:String,onResult:(Result<AppProfile>)->Unit={}){
        executor.execute {
            val token=fcmToken.trim()
            if(token.isNotBlank()) prefs.edit().putString(FCM_TOKEN,token).apply()
            registerWithToken(token,onResult)
        }
    }

    fun linkPhone(phone:String,onResult:(Result<AppProfile>)->Unit){
        executor.execute {
            try {
                val value=phone.trim().replace(Regex("[\\s()-]"),"")
                if(!Regex("^\\+?[1-9]\\d{6,14}$").matches(value)) throw IllegalArgumentException("Use an international number, e.g. +2519XXXXXXXX")
                val token=prefs.getString(FCM_TOKEN,"").orEmpty().ifBlank{runCatching{Tasks.await(FirebaseMessaging.getInstance().token,15,TimeUnit.SECONDS)}.getOrDefault("")}
                if(token.isNotBlank()) prefs.edit().putString(FCM_TOKEN,token).apply()
                val json=request("/register",common().put("phone_number",value).put("fcm_token",token).put("display_name","Messages SOM user"))
                onResult(Result.success(profile(json.getJSONObject("data"))))
            } catch(e:Throwable){onResult(Result.failure(e))}
        }
    }

    private fun registerWithToken(token:String,onResult:(Result<AppProfile>)->Unit){
        try {
            val phone=DevicePhoneNumber.read(context).orEmpty()
            val json=request("/register",common().put("phone_number",phone).put("fcm_token",token.trim()).put("display_name","Messages SOM user"))
            onResult(Result.success(profile(json.getJSONObject("data"))))
        } catch(e:Throwable){onResult(Result.failure(e))}
    }

    fun search(value:String,onResult:(Result<List<AppProfile>>)->Unit){executor.execute{try{val json=request("/search",common().put("query",value.trim()));val a=json.optJSONArray("data")?:JSONArray();onResult(Result.success(buildList{for(i in 0 until a.length())add(profile(a.getJSONObject(i)))}))}catch(e:Throwable){onResult(Result.failure(e))}}}
    fun send(recipient:AppProfile,text:String,onResult:(Result<String>)->Unit){executor.execute{try{val json=request("/send",common().put("recipient_uid",recipient.uid).put("message",text.trim()));onResult(Result.success(json.getJSONObject("data").optString("id")))}catch(e:Throwable){onResult(Result.failure(e))}}}
    fun conversation(otherUid:String,onResult:(Result<List<AppMessage>>)->Unit){executor.execute{try{val json=request("/conversation",common().put("other_uid",otherUid));val a=json.optJSONArray("data")?:JSONArray();onResult(Result.success(buildList{for(i in 0 until a.length())add(message(a.getJSONObject(i)))}))}catch(e:Throwable){onResult(Result.failure(e))}}}
    fun markRead(otherUid:String,onResult:(Result<Unit>)->Unit={}){executor.execute{try{request("/read",common().put("other_uid",otherUid));onResult(Result.success(Unit))}catch(e:Throwable){onResult(Result.failure(e))}}}
    private fun profile(o:JSONObject)=AppProfile(o.optString("uid"),o.optString("messagesId"),o.optString("displayName"),o.optString("phoneNumber"),o.optString("photoUrl"),o.optString("fcmToken"),o.optBoolean("enabled",true),o.optLong("createdAt"),o.optLong("updatedAt"),o.optBoolean("allowReplies",true))
    private fun message(o:JSONObject)=AppMessage(o.optString("id"),o.optString("senderUid"),o.optString("senderId"),o.optString("senderName"),o.optString("recipientUid"),o.optString("recipientId"),o.optString("text"),o.optLong("createdAt"),o.optLong("deliveredAt"),o.optLong("readAt"),o.optBoolean("deleted",false),o.optBoolean("mine",false))
}
