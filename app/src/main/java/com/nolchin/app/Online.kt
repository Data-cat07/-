package com.nolchin.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class OnlineProfile(val username:String, val nickname:String, val code:String, val lessons:List<Lesson>, val preferences:Map<String,Int>)
data class OnlinePerson(val id:String, val username:String, val nickname:String, val lessons:List<Lesson>, val preferences:Map<String,Int>)
data class OnlineHang(val id:String, val from:String, val to:String, val date:String, val start:Int, val end:Int, val message:String, val status:String, val place:String, val proposer:String)
data class OnlineState(val profile:OnlineProfile? = null, val friends:List<FriendRequest> = emptyList(), val hangouts:List<OnlineHang> = emptyList(), val people:List<OnlinePerson> = emptyList())
val seoul:ZoneId = ZoneId.of("Asia/Seoul")

class OnlineRepository @Inject constructor() {
    val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val gson = Gson()

    suspend fun call(op:String, args: Map<String,Any> = emptyMap()): Any? =
        functions.getHttpsCallable("nolchin").call(args + ("op" to op)).await().getData()

    suspend fun state(): OnlineState = gson.fromJson(gson.toJson(call("state")), OnlineState::class.java)
    suspend fun search(query:String): OnlinePerson? =
        gson.fromJson(gson.toJson(call("search",mapOf("query" to query))),OnlinePerson::class.java)
            ?.takeIf { (it.id as String?) != null }

    suspend fun signIn(email:String, password:String, create:Boolean) {
        if (create) auth.createUserWithEmailAndPassword(email,password).await()
        else auth.signInWithEmailAndPassword(email,password).await()
    }
}

@HiltViewModel
class OnlineVM @Inject constructor(private val repository:OnlineRepository):ViewModel() {
    val uid = MutableStateFlow(repository.auth.currentUser?.uid)
    val state = MutableStateFlow<OnlineState?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow("")
    val result = MutableStateFlow<OnlinePerson?>(null)
    val searched = MutableStateFlow(false)
    private var revision = 0
    private val listener = FirebaseAuth.AuthStateListener {
        val next = it.currentUser?.uid
        if (next != uid.value) {
            revision++
            state.value = null
            result.value = null
            searched.value = false
            uid.value = next
        }
    }

    init {
        repository.auth.addAuthStateListener(listener)
        viewModelScope.launch {
            var tokenUid:String? = null
            while (true) {
                val user = uid.value
                if (user == null) tokenUid = null
                if (user != null && !busy.value) {
                    val current = revision
                    try {
                        refresh(user, current)
                    } catch (e:CancellationException) { throw e }
                    catch (e:Exception) { if (revision == current) error.value = description(e) }
                    if (tokenUid != user && uid.value == user) {
                        try {
                            val token = FirebaseMessaging.getInstance().token.await()
                            if (uid.value == user) {
                                repository.call("token",mapOf("token" to token))
                                tokenUid = user
                            }
                        } catch (e:CancellationException) { throw e }
                        catch (_:Exception) { /* Retry on the next refresh. */ }
                    }
                }
                delay(5000)
            }
        }
    }

    private suspend fun refresh(user:String, version:Int) {
        val latest = repository.state()
        if (uid.value == user && revision == version) state.value = latest
    }

    private fun description(e:Exception):String = when (e) {
        is FirebaseFunctionsException -> if (e.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION)
            e.message ?: "요청 내용을 확인해 주세요." else "서버 연결에 실패했어요. 설정과 네트워크를 확인해 주세요."
        is com.google.firebase.auth.FirebaseAuthException -> "로그인 정보를 확인해 주세요. 이미 등록된 이메일이나 잘못된 비밀번호일 수 있어요."
        else -> "처리하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요."
    }

    private fun run(block:suspend ()->Unit) {
        if (busy.value) return
        busy.value = true
        error.value = ""
        revision++
        viewModelScope.launch {
            try { block() }
            catch (e:CancellationException) { throw e }
            catch (e:Exception) { error.value = description(e) }
            finally { busy.value = false }
        }
    }

    fun login(email:String,password:String,create:Boolean) = run {
        repository.signIn(email.trim(),password,create)
    }
    fun logout() = run {
        val token = FirebaseMessaging.getInstance().token.await()
        repository.call("logout",mapOf("token" to token))
        repository.auth.signOut()
    }
    fun search(query:String) = run {
        result.value = null
        searched.value = false
        result.value = repository.search(query)
        searched.value = true
    }
    fun action(op:String, vararg args:Pair<String,Any>, done:()->Unit = {}) = run {
        val user = uid.value ?: return@run
        repository.call(op,args.toMap())
        refresh(user, revision)
        if (uid.value == user) done()
    }

    override fun onCleared() {
        repository.auth.removeAuthStateListener(listener)
        super.onCleared()
    }
}

class NolchinMessaging:FirebaseMessagingService() {
    override fun onNewToken(token:String) {
        if (FirebaseAuth.getInstance().currentUser != null) {
            FirebaseFunctions.getInstance().getHttpsCallable("nolchin")
                .call(mapOf("op" to "token","token" to token))
        }
    }

    override fun onMessageReceived(message:RemoteMessage) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.data["recipient"] != currentUid) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = message.data["title"] ?: return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("friends","친구와 약속",NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this,"friends")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("놀친에서 확인해 보세요.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify((message.messageId ?: title).hashCode(),notification)
    }
}


