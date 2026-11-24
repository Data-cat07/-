package com.nolchin.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp class NolchinApp : Application()
private val Context.store by preferencesDataStore("nolchin")
private fun id() = UUID.randomUUID().toString()
data class Person(val id:String, val username:String, val nickname:String, val code:String)
data class Lesson(val id:String = id(), val owner:String = "me", val day:Int = 1, val start:Int = 960, val end:Int = 1080, val title:String = "학원", val type:String = "학원")
data class FriendRequest(val id:String = id(), val from:String, val to:String, val status:String = "pending")
data class Hangout(val id:String = id(), val from:String, val to:String, val date:String, val start:Int, val end:Int, val message:String = "", val status:String = "pending", val placeId:String = "", val proposer:String = "")
data class Place(val id:String, val name:String, val category:String, val address:String, val distance:String, val rating:String)
data class AppData(val loggedIn:Boolean = false, val nickname:String = "나", val username:String = "my_id", val code:String = "NOL123", val friends:List<String> = listOf("jiwoo"), val lessons:List<Lesson> = seedLessons(), val friendRequests:List<FriendRequest> = listOf(FriendRequest(from="minseo",to="me")), val hangouts:List<Hangout> = emptyList(), val preferences:Map<String,Int> = emptyMap())
fun seedLessons() = listOf(Lesson(owner="me",day=1,start=960,end=1080,title="수학학원"), Lesson(owner="me",day=3,start=1020,end=1140,title="영어학원"), Lesson(owner="jiwoo",day=1,start=1020,end=1140,title="과학학원"), Lesson(owner="jiwoo",day=4,start=960,end=1080,title="영어학원"), Lesson(owner="minseo",day=2,start=960,end=1080,title="미술학원"))
val people = listOf(Person("jiwoo","jiwoo","지우","JIW123"),Person("minseo","minseo","민서","MIN123"),Person("haneul","haneul","하늘","HAN123"))
val places = listOf(Place("cafe","몽글 카페","카페","서울시 중구 예시로 12","약 1.2km","4.7"),Place("park","초록 공원","공원","서울시 중구 예시길 8","약 1.5km","4.6"),Place("board","다이스 놀이터","보드게임카페","서울시 중구 예시로 32","약 1.8km","4.8"),Place("movie","별빛 시네마","영화관","서울시 중구 예시길 20","약 2.1km","4.5"),Place("dessert","달콤 구름","디저트","서울시 중구 예시로 5","약 0.9km","4.7"))
fun person(id:String, d:AppData) = if(id=="me") d.nickname else people.find{it.id==id}?.nickname ?: "친구"
fun hm(m:Int) = "%02d:%02d".format(m/60,m%60)
fun dayName(day:Int) = listOf("월","화","수","목","금","토","일").getOrElse(day-1){"?"}
data class Slot(val date:LocalDate,val start:Int,val end:Int)
fun commonSlots(lessons:List<Lesson>, friend:String, today:LocalDate=LocalDate.now(), now:LocalTime=LocalTime.now()):List<Slot> = (0L..6L).flatMap { offset ->
 val date=today.plusDays(offset); val day=date.dayOfWeek.value
 val busy=lessons.filter{it.day==day && (it.owner=="me" || it.owner==friend)}.sortedBy{it.start}
 val result=mutableListOf<Slot>(); var cursor=if(offset==0L) maxOf(9*60, ((now.hour*60+now.minute+30)/30)*30) else 9*60
 for(l in busy) { val start=l.start.coerceIn(540,1320); val end=l.end.coerceIn(540,1320); if(start-cursor>=60) result.add(Slot(date,cursor,start)); cursor=maxOf(cursor,end) }
 if(1320-cursor>=60) result.add(Slot(date,cursor,1320)); result
}
@Singleton class DemoRepository @Inject constructor(@ApplicationContext private val context:Context) {
 private val key=stringPreferencesKey("state_v1"); private val gson=Gson()
 val data:Flow<AppData> = context.store.data.map { p -> try { p[key]?.let { gson.fromJson(it,AppData::class.java) } ?: AppData() } catch (_:Exception) { AppData() } }
 suspend fun change(f:(AppData)->AppData) { context.store.edit { p -> val current=try { p[key]?.let { gson.fromJson(it,AppData::class.java) } ?: AppData() } catch (_:Exception) { AppData() }; p[key]=gson.toJson(f(current)) } }
}
@HiltViewModel class MainVM @Inject constructor(private val repo:DemoRepository):ViewModel() {
 val data=repo.data.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppData())
 fun update(f:(AppData)->AppData) { viewModelScope.launch { repo.change(f) } }
 fun login(n:String,u:String) = update { it.copy(loggedIn=true,nickname=n.trim(),username=u.trim()) }
 fun saveLesson(l:Lesson)=update { it.copy(lessons=it.lessons.filterNot { old->old.id==l.id }+l) }
 fun deleteLesson(id:String)=update { it.copy(lessons=it.lessons.filterNot { l->l.id==id }) }
 fun friendRequest(target:String)=update { d -> if(target in d.friends || d.friendRequests.any{it.from=="me" && it.to==target && it.status=="pending"}) d else d.copy(friendRequests=d.friendRequests+FriendRequest(from="me",to=target)) }
 fun answerFriend(id:String,accept:Boolean)=update { d -> val req=d.friendRequests.find{it.id==id}; if(req==null || req.status!="pending") d else d.copy(friendRequests=d.friendRequests.map{if(it.id==id) it.copy(status=if(accept) "accepted" else "rejected") else it},friends=if(accept) (d.friends+if(req.from=="me") req.to else req.from).distinct() else d.friends) }
 fun send(friend:String,slot:Slot,msg:String)=update{it.copy(hangouts=it.hangouts+Hangout(from="me",to=friend,date=slot.date.toString(),start=slot.start,end=slot.end,message=msg))}
 fun status(id:String,status:String)=update{d->d.copy(hangouts=d.hangouts.map{if(it.id==id) it.copy(status=status) else it})}
 fun propose(id:String,place:String)=update{d->d.copy(hangouts=d.hangouts.map{if(it.id==id && it.status!="confirmed") it.copy(status="place_selecting",placeId=place,proposer="me") else it})}
 fun confirm(id:String)=update{d-> val h=d.hangouts.find{it.id==id}; val p=places.find{it.id==h?.placeId}; if(h==null || p==null || h.status=="confirmed") d else d.copy(hangouts=d.hangouts.map{if(it.id==id) it.copy(status="confirmed") else it},preferences=d.preferences+(p.category to ((d.preferences[p.category]?:0)+1))) }
}

