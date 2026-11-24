package com.nolchin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

private val Purple=Color(0xFF7656D6)
private val Ink=Color(0xFF29243C)
private val Bg=Color(0xFFFAF8FF)
@AndroidEntryPoint class MainActivity:ComponentActivity() { override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Purple, secondary=Color(0xFFFF83A6), background=Bg, surface=Color.White, onSurface=Ink), typography=Typography()) { if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) Nolchin() else OnlineApp() } } } }
@Composable fun Nolchin(vm:MainVM = hiltViewModel()) {
 val d by vm.data.collectAsStateWithLifecycle(); val nav= rememberNavController(); var selectedFriend by remember { mutableStateOf("jiwoo") }; var selectedSlot by remember { mutableStateOf<Slot?>(null) }; var selectedHangout by remember { mutableStateOf("") }; var editLesson by remember { mutableStateOf<Lesson?>(null) }
 val back:()->Unit = { nav.popBackStack() }
 NavHost(navController=nav,startDestination="start") {
  composable("start") { LaunchedEffect(d.loggedIn) { nav.navigate(if(d.loggedIn) "home" else "login") { popUpTo("start") { inclusive=true } } }; Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("놀친 💜",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Purple)} }
  composable("login") { var nick by remember{mutableStateOf("")}; var username by remember{mutableStateOf("")}; Page("반가워, 놀친! 👋","친구와 비는 시간을 찾아 즐겁게 만나자") {
    Hero("우리 언제 놀까?", "서로의 시간표를 비교하고\n딱 좋은 약속을 만들어 봐요 ✨")
    Info("데모 모드", "이 기기에만 저장돼요. 실제 계정이나 다른 기기와 연결되지는 않아요.")
    OutlinedTextField(nick,{nick=it},label={Text("닉네임")},singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(username,{username=it},label={Text("아이디 (영문·숫자)")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.login(nick,username);nav.navigate("home"){popUpTo("login"){inclusive=true}}},enabled=nick.isNotBlank() && username.matches(Regex("[A-Za-z0-9_]{3,20}")),modifier=Modifier.fillMaxWidth()){Text("데모 시작하기")}
  } }
  composable("home") { Page("안녕, ${d.nickname} 👋","오늘은 누구와 놀아볼까?") {
    Hero("우리 함께 놀자! ✨","친구와 비는 시간을 찾고\n새로운 추억을 만들어 봐요")
    val next=d.lessons.filter{it.owner=="me"}.sortedBy{(it.day-LocalDate.now().dayOfWeek.value+7)%7}.firstOrNull()
    Info("내 다음 일정",next?.let{"${dayName(it.day)}요일 ${hm(it.start)} · ${it.title}"} ?: "등록된 일정이 없어요")
    Info("놀자 신청", "받은 신청 ${d.hangouts.count{it.to=="me" && it.status=="pending"}}개 · 예정된 약속 ${d.hangouts.count{it.status=="confirmed" && it.date>=LocalDate.now().toString()}}개")
    Text("빠르게 시작하기",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) { Quick("📚","시간표",Modifier.weight(1f)){nav.navigate("schedule")}; Quick("💌","친구",Modifier.weight(1f)){nav.navigate("friends")} }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) { Quick("🕒","빈 시간",Modifier.weight(1f)){nav.navigate("chooseFriend")}; Quick("🎈","약속",Modifier.weight(1f)){nav.navigate("hangouts")} }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={nav.navigate("requests")}){Text("신청함")}; OutlinedButton(onClick={nav.navigate("profile")}){Text("마이페이지")}}
  } }
  composable("schedule") { Page("내 시간표 📚","매주 반복되는 일정을 등록해요",back) {
    Button(onClick={editLesson=null;nav.navigate("edit")},modifier=Modifier.fillMaxWidth()){Text("+ 일정 추가")}
    (1..7).forEach { day -> Text("${dayName(day)}요일",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp)); val lessons=d.lessons.filter{it.owner=="me" && it.day==day}.sortedBy{it.start}; if(lessons.isEmpty()) Text("일정 없음",color=Color.Gray) else lessons.forEach { l -> CardBox { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Column(Modifier.weight(1f)){Text(l.title,fontWeight=FontWeight.Bold);Text("${hm(l.start)} - ${hm(l.end)} · ${l.type}",color=Color.Gray)}; TextButton(onClick={editLesson=l;nav.navigate("edit")}){Text("수정")}; TextButton(onClick={vm.deleteLesson(l.id)}){Text("삭제")} } } } }
  } }
  composable("edit") { var title by remember{mutableStateOf(editLesson?.title?:"")}; var day by remember{mutableIntStateOf(editLesson?.day?:1)}; var start by remember{mutableStateOf(editLesson?.start?.let(::hm)?:"16:00")}; var end by remember{mutableStateOf(editLesson?.end?.let(::hm)?:"18:00")}; var type by remember{mutableStateOf(editLesson?.type?:"학원")}; val a=parseTime(start); val b=parseTime(end)
   Page(if(editLesson==null) "일정 추가" else "일정 수정","요일과 시간을 입력해요",back) {
    OutlinedTextField(title,{title=it},label={Text("일정 이름")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Text("요일",fontWeight=FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){(1..7).forEach{ FilterChip(selected=day==it,onClick={day=it},label={Text(dayName(it))}) }}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},label={Text("시작 HH:mm")},modifier=Modifier.weight(1f),singleLine=true); OutlinedTextField(end,{end=it},label={Text("종료 HH:mm")},modifier=Modifier.weight(1f),singleLine=true)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("학원","기타 일정").forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(it)})}}
    Text("같은 날 안에서 종료 시간이 시작보다 늦어야 해요.",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
    Button(onClick={vm.saveLesson(Lesson(id=editLesson?.id?:java.util.UUID.randomUUID().toString(),day=day,start=a!!,end=b!!,title=title.trim(),type=type));back()},enabled=title.isNotBlank() && a!=null && b!=null && a<b,modifier=Modifier.fillMaxWidth()){Text("저장하기")}
  } }
  composable("friends") { Page("친구들 💌","함께 놀 친구를 만나봐요",back) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={nav.navigate("addFriend")}){Text("+ 친구 추가")}; OutlinedButton(onClick={nav.navigate("friendRequests")}){Text("친구 요청")}}
    if(d.friends.isEmpty()) Empty("아직 친구가 없어요. 친구를 추가해 보세요!")
    d.friends.forEach { f-> CardBox { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Avatar(); Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(person(f,d),fontWeight=FontWeight.Bold);Text("@${people.find{it.id==f}?.username}",color=Color.Gray)};TextButton(onClick={selectedFriend=f;nav.navigate("slots")}){Text("빈 시간")};TextButton(onClick={vm.update{it.copy(friends=it.friends-f)}}){Text("삭제")} } } }
  } }
  composable("addFriend") { var query by remember{mutableStateOf("")}; Page("친구 추가 🔎","아이디 또는 초대 코드를 입력해요",back) {
    OutlinedTextField(query,{query=it},label={Text("아이디 / 초대 코드")},singleLine=true,modifier=Modifier.fillMaxWidth())
    if(query.isBlank()) Empty("데모 친구: minseo 또는 HAN123") else { val found=people.filter{it.username.equals(query.trim(),true)||it.code.equals(query.trim(),true)}; if(found.isEmpty()) Empty("검색 결과가 없어요.") else found.forEach{p->CardBox{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.nickname,fontWeight=FontWeight.Bold);Text("@${p.username}")};Button(onClick={vm.friendRequest(p.id)},enabled=p.id !in d.friends && d.friendRequests.none{it.from=="me"&&it.to==p.id&&it.status=="pending"}){Text(if(p.id in d.friends) "친구" else "요청")}}}} }
  } }
  composable("friendRequests") { Page("친구 요청 💌","받은 요청과 보낸 요청",back) {
    if(d.friendRequests.isEmpty()) Empty("친구 요청이 없어요")
    d.friendRequests.reversed().forEach{r->CardBox{Text(if(r.to=="me") "${person(r.from,d)}님의 요청" else "${person(r.to,d)}님에게 보낸 요청",fontWeight=FontWeight.Bold); Text(statusLabel(r.status)); if(r.status=="pending") Row { if(r.to=="me") {TextButton(onClick={vm.answerFriend(r.id,true)}){Text("수락")};TextButton(onClick={vm.answerFriend(r.id,false)}){Text("거절")}} else TextButton(onClick={vm.answerFriend(r.id,true)}){Text("데모: 친구가 수락")}} } }
  } }
  composable("chooseFriend") { Page("누구와 놀까? 👫","친구 한 명을 선택해요",back) { if(d.friends.isEmpty()) Empty("먼저 친구를 추가해 주세요."); d.friends.forEach{f-> CardBox { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Avatar();Text(person(f,d),Modifier.weight(1f).padding(12.dp),fontWeight=FontWeight.Bold);Button(onClick={selectedFriend=f;nav.navigate("slots")}){Text("선택")}} }} } }
  composable("slots") { Page("함께 비는 시간 🕒","${person(selectedFriend,d)}님과 이번 주에 만나요",back) { Info("비교 기준", "앞으로 7일 · 오전 9시~밤 10시 · 1시간 이상")
    val slots=commonSlots(d.lessons,selectedFriend); WeekBars(slots); if(slots.isEmpty()) Empty("이번 주에 겹치는 빈 시간이 없어요.")
    slots.forEach{s->CardBox { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${s.date.monthValue}월 ${s.date.dayOfMonth}일 ${dayName(s.date.dayOfWeek.value)}요일",fontWeight=FontWeight.Bold);Text("${hm(s.start)} ~ ${hm(s.end)}",color=Purple)};Button(onClick={selectedSlot=s;nav.navigate("composeRequest")}){Text("놀자!")}} } }
  } }
  composable("composeRequest") { var msg by remember{mutableStateOf("")}; val s=selectedSlot; Page("놀자고 신청하기 💬","${person(selectedFriend,d)}님에게 마음을 보내요",back) { if(s!=null){Info("선택한 시간","${s.date} ${hm(s.start)} ~ ${hm(s.end)}");OutlinedTextField(msg,{msg=it},label={Text("한마디 (선택)")},modifier=Modifier.fillMaxWidth());Button(onClick={vm.send(selectedFriend,s,msg);nav.navigate("requests")},modifier=Modifier.fillMaxWidth()){Text("신청 보내기")}} } }
  composable("requests") { Page("놀자 신청함 💌","받은 신청과 보낸 신청",back) { TextButton(onClick={vm.update{it.copy(hangouts=it.hangouts+Hangout(from="jiwoo",to="me",date=LocalDate.now().plusDays(1).toString(),start=840,end=960,message="같이 놀자!"))}}){Text("데모: 받은 신청 만들기")}; if(d.hangouts.isEmpty()) Empty("아직 신청이 없어요. 친구에게 먼저 놀자고 해볼까요?"); d.hangouts.reversed().forEach{h->HangoutCard(h,d){selectedHangout=h.id;nav.navigate("detail")}} } }
  composable("hangouts") { Page("우리의 약속 🎈","예정된 약속과 지난 약속",back) { val sorted=d.hangouts.sortedBy{it.date}; listOf("예정된 약속" to sorted.filter{it.date>=LocalDate.now().toString()},"지난 약속" to sorted.filter{it.date<LocalDate.now().toString()}).forEach{(label,list)->Text(label,fontWeight=FontWeight.Bold);if(list.isEmpty()) Empty("아직 없어요");list.forEach{h->HangoutCard(h,d){selectedHangout=h.id;nav.navigate("detail")}}} } }
  composable("detail") { val h=d.hangouts.find{it.id==selectedHangout}; Page("약속 상세 🎈","두근두근 만날 준비!",back) { if(h==null) Empty("약속을 찾을 수 없어요") else { Info("함께하는 친구",person(if(h.from=="me")h.to else h.from,d)); Info("시간","${h.date} · ${hm(h.start)} ~ ${hm(h.end)}");Info("상태",statusLabel(h.status));if(h.message.isNotBlank()) Info("한마디",h.message);places.find{it.id==h.placeId}?.let{Info("장소", "${it.name} · ${it.category}")}
    if(h.status=="pending") { if(h.to=="me") Row{Button(onClick={vm.status(h.id,"accepted")}){Text("수락")};TextButton(onClick={vm.status(h.id,"rejected")}){Text("거절")}} else Button(onClick={vm.status(h.id,"accepted")}){Text("데모: 친구가 수락")}; if(h.from=="me") TextButton(onClick={vm.status(h.id,"rejected")}){Text("데모: 친구가 거절")} }
    if(h.status=="accepted"||h.status=="place_selecting") Button(onClick={nav.navigate("places")},modifier=Modifier.fillMaxWidth()){Text("장소 고르기")}
    if(h.status=="place_selecting" && h.placeId.isNotBlank()) { Info("장소 제안", "${places.find{it.id==h.placeId}?.name} · 상대의 동의를 기다려요");Button(onClick={vm.confirm(h.id)}){Text("데모: 친구가 동의")}}
  } } }
  composable("places") { val h=d.hangouts.find{it.id==selectedHangout}; Page("어디서 놀까? 📍","취향을 반영한 데모 장소예요",back) { Info("샘플 추천", "표시된 장소와 거리는 예시이며 현재 위치를 사용하지 않아요."); places.sortedByDescending{d.preferences[it.category]?:0}.forEach{p->CardBox { Text(p.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("${p.category} · ⭐ ${p.rating} · ${p.distance}",color=Purple);Text(p.address,color=Color.Gray);Text("선호 점수 ${d.preferences[p.category]?:0}",style=MaterialTheme.typography.bodySmall); Button(onClick={h?.let{vm.propose(it.id,p.id)};back()}){Text("이 장소 제안하기")}}} } }
  composable("profile") { Page("마이페이지 🌷","나의 놀친 정보",back) { Info("닉네임",d.nickname);Info("아이디","@${d.username}");Info("내 초대 코드",d.code+" (데모)");Info("나의 취향",if(d.preferences.isEmpty()) "확정한 장소가 아직 없어요" else d.preferences.entries.joinToString(" · "){"${it.key} ${it.value}회"});TextButton(onClick={vm.update{it.copy(loggedIn=false)};nav.navigate("login"){popUpTo("home"){inclusive=true}}}){Text("데모 로그아웃")} } }
 }
}
fun parseTime(s:String):Int? { val m=Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s.trim())?:return null; val h=m.groupValues[1].toInt(); val min=m.groupValues[2].toInt();return if(h in 0..23 && min in 0..59) h*60+min else null }
fun statusLabel(s:String)=when(s){"pending"->"대기 중";"accepted"->"수락됨";"rejected"->"거절됨";"place_selecting"->"장소 선택 중";"confirmed"->"확정됨";"completed"->"완료됨";else->s}
@Composable fun Page(title:String, subtitle:String, back:(()->Unit)?=null, content:@Composable ColumnScope.()->Unit) { Scaffold(containerColor=Bg) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { if(back!=null) TextButton(onClick=back,contentPadding=PaddingValues(0.dp)){Text("‹  돌아가기",color=Purple)};Text(title,fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Ink);Text(subtitle,color=Color(0xFF777188));Spacer(Modifier.height(4.dp));content();Spacer(Modifier.height(32.dp)) } } }
@Composable fun Hero(title:String,body:String){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFECE4FF)),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(22.dp)){Text(title,fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=Purple);Spacer(Modifier.height(8.dp));Text(body,lineHeight=23.sp,color=Ink)}}}
@Composable fun CardBox(content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth(),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp),content=content)}}
@Composable fun Info(title:String,body:String){CardBox{Text(title,fontWeight=FontWeight.Bold);Text(body,color=Color(0xFF777188))}}
@Composable fun Empty(text:String){Box(Modifier.fillMaxWidth().padding(18.dp),contentAlignment=Alignment.Center){Text(text,color=Color.Gray)}}
@Composable fun Avatar(){Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFFFE5EF),modifier=Modifier.size(48.dp)){Box(contentAlignment=Alignment.Center){Text("😊",fontSize=23.sp)}}}
@Composable fun Quick(emoji:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Text(emoji,fontSize=26.sp);Spacer(Modifier.height(8.dp));Text(label,fontWeight=FontWeight.Bold)}}}
@Composable fun HangoutCard(h:Hangout,d:AppData,onClick:()->Unit){Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("${person(if(h.from=="me")h.to else h.from,d)}와의 약속",fontWeight=FontWeight.Bold);Text("${h.date} · ${hm(h.start)} ~ ${hm(h.end)}",color=Color.Gray);Text(statusLabel(h.status),color=Purple)}}}


