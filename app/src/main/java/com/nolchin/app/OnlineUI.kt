package com.nolchin.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

@Composable
fun WeekBars(slots:List<Slot>, date:LocalDate = LocalDate.now()) {
    CardBox {
        Text("공통 빈 시간 · 진한 노란색")
        Row(Modifier.fillMaxWidth().padding(start=36.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("09:00","13:00","17:00","22:00").forEach { Text(it,style=MaterialTheme.typography.labelSmall) }
        }
        repeat(7) { index ->
            val day = date.plusDays(index.toLong())
            val daily = slots.filter { it.date == day }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(dayName(day.dayOfWeek.value)+"요일",Modifier.width(36.dp),style=MaterialTheme.typography.labelSmall)
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    drawRect(Color(0xFFE6E1EA))
                    daily.forEach { slot ->
                        val start = (slot.start-540)/780f
                        val width = (slot.end-slot.start)/780f
                        drawRect(Color(0xFFDEA000),Offset(size.width*start,0f),Size(size.width*width,size.height))
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineApp(vm:OnlineVM = hiltViewModel()) {
    val uid by vm.uid.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val profile = state?.profile
    key(uid) {
        var page by rememberSaveable { mutableStateOf("home") }
        var friendId by rememberSaveable { mutableStateOf("") }
        var hangId by rememberSaveable { mutableStateOf("") }
        var lessonId by rememberSaveable { mutableStateOf("") }
        var slotDate by rememberSaveable { mutableStateOf("") }
        var slotStart by rememberSaveable { mutableIntStateOf(0) }
        var slotEnd by rememberSaveable { mutableIntStateOf(0) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val back:()->Unit = { page = "home" }
        val people = state?.people.orEmpty()
        val friend = people.find { it.id == friendId }
        val hang = state?.hangouts?.find { it.id == hangId }
        fun name(id:String) = people.find{it.id==id}?.nickname ?: "친구"
        val title = when {
            uid == null -> "놀친 💜"
            state == null -> "연결 중"
            profile == null -> "프로필 만들기"
            else -> when(page) {
                "friends" -> "친구 💌"
                "schedule" -> "내 시간표 📚"
                "lesson" -> "일정 편집"
                "slots" -> "함께 비는 시간"
                "send" -> "놀자 신청"
                "hangouts" -> "신청과 약속 🎈"
                "detail" -> "약속 상세"
                "places" -> "어디서 놀까?"
                else -> "안녕, "+profile.nickname+" 👋"
            }
        }
        Page(title,"놀친 온라인",if(page=="home" || uid==null || profile==null) null else back) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
            when {
                uid == null -> {
                    var email by rememberSaveable { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    OnlineField(email,{email=it},"이메일")
                    OutlinedTextField(password,{password=it},label={Text("비밀번호")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button({vm.login(email,password,false)},enabled=!busy && email.isNotBlank() && password.isNotBlank()){Text("로그인")}
                        OutlinedButton({vm.login(email,password,true)},enabled=!busy && email.isNotBlank() && password.length>=6){Text("회원가입")}
                    }
                }
                state == null -> {
                    if(error.isEmpty()) CircularProgressIndicator()
                    TextButton({vm.logout()},enabled=!busy){Text("로그아웃")}
                }
                profile == null -> {
                    var nickname by rememberSaveable { mutableStateOf("") }
                    var username by rememberSaveable { mutableStateOf("") }
                    OnlineField(nickname,{nickname=it},"닉네임")
                    OnlineField(username,{username=it},"고유 아이디 (영문·숫자·밑줄)")
                    Button({vm.action("register","nickname" to nickname,"username" to username)},enabled=!busy && nickname.isNotBlank() && username.matches(Regex("[A-Za-z0-9_]{3,20}"))){Text("저장")}
                    TextButton({vm.logout()},enabled=!busy){Text("로그아웃")}
                }
                else -> when(page) {
                    "home" -> {
                        Hero("우리 언제 놀까?","친구와 비는 시간을 찾고 약속을 만들어 봐요.")
                        Info("@"+profile.username,"내 초대 코드 · "+profile.code)
                        Info("받은 놀자 신청",state?.hangouts.orEmpty().count{it.to==uid && it.status=="pending"}.toString()+"개")
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Quick("📚","시간표",Modifier.weight(1f)){page="schedule"}
                            Quick("💌","친구와 빈 시간",Modifier.weight(1f)){page="friends"}
                        }
                        Button({page="hangouts"}){Text("신청과 약속 보기")}
                        if(Build.VERSION.SDK_INT>=33) OutlinedButton({notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("푸시 알림 허용")}
                        TextButton({vm.logout()},enabled=!busy){Text("로그아웃")}
                    }
                    "friends" -> {
                        var query by rememberSaveable { mutableStateOf("") }
                        val result by vm.result.collectAsStateWithLifecycle()
                        val searched by vm.searched.collectAsStateWithLifecycle()
                        OnlineField(query,{query=it},"친구 아이디 또는 초대 코드")
                        Button({vm.search(query)},enabled=!busy && query.isNotBlank()){Text("검색")}
                        result?.let { p -> CardBox {
                            Text(p.nickname+" · @"+p.username)
                            Button({vm.action("friend","other" to p.id)},enabled=!busy){Text("친구 요청 보내기")}
                        } }
                        if(searched && result==null) Empty("검색 결과가 없어요.")
                        val requests = state?.friends.orEmpty()
                        if(requests.isEmpty()) Empty("친구나 요청이 아직 없어요.")
                        requests.forEach { request ->
                            val other = if(request.from==uid) request.to else request.from
                            CardBox {
                                Text(name(other))
                                Text(if(request.status=="accepted") "친구" else (if(request.to==uid) "받은 요청 · " else "보낸 요청 · ")+statusLabel(request.status))
                                if(request.status=="accepted") {
                                    Button({friendId=other;page="slots"}){Text("공통 빈 시간 보기")}
                                    TextButton({vm.action("removeFriend","other" to other)},enabled=!busy){Text("친구 삭제")}
                                } else if(request.status=="pending" && request.to==uid) {
                                    Row {
                                        Button({vm.action("answerFriend","other" to other,"accept" to true)},enabled=!busy){Text("수락")}
                                        TextButton({vm.action("answerFriend","other" to other,"accept" to false)},enabled=!busy){Text("거절")}
                                    }
                                }
                            }
                        }
                    }
                    "schedule" -> {
                        Button({lessonId="";page="lesson"}){Text("일정 추가")}
                        for (day in 1..7) {
                            Text(dayName(day)+"요일",style=MaterialTheme.typography.titleMedium)
                            val lessons = profile.lessons.filter{it.day==day}.sortedBy{it.start}
                            if(lessons.isEmpty()) Text("일정 없음",style=MaterialTheme.typography.bodySmall)
                            lessons.forEach { lesson -> CardBox {
                                Text(lesson.title+" · "+lesson.type)
                                Text(hm(lesson.start)+" ~ "+hm(lesson.end))
                                Row {
                                    TextButton({lessonId=lesson.id;page="lesson"}){Text("수정")}
                                    TextButton({vm.action("deleteLesson","id" to lesson.id)},enabled=!busy){Text("삭제")}
                                }
                            } }
                        }
                    }
                    "lesson" -> {
                        val lesson = profile.lessons.find{it.id==lessonId}
                        var label by rememberSaveable(lessonId){mutableStateOf(lesson?.title.orEmpty())}
                        var day by rememberSaveable(lessonId){mutableIntStateOf(lesson?.day ?: 1)}
                        var start by rememberSaveable(lessonId){mutableStateOf(lesson?.start?.let(::hm) ?: "16:00")}
                        var end by rememberSaveable(lessonId){mutableStateOf(lesson?.end?.let(::hm) ?: "18:00")}
                        var type by rememberSaveable(lessonId){mutableStateOf(lesson?.type ?: "학원")}
                        OnlineField(label,{label=it},"일정 이름")
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            (1..7).forEach { d -> FilterChip(day==d,{day=d},{Text(dayName(d))}) }
                        }
                        OnlineField(start,{start=it},"시작 HH:mm")
                        OnlineField(end,{end=it},"종료 HH:mm")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("학원","기타 일정").forEach{ t -> FilterChip(type==t,{type=t},{Text(t)}) }
                        }
                        val a = parseTime(start)
                        val b = parseTime(end)
                        Button({
                            vm.action("lesson","id" to lessonId.ifEmpty{UUID.randomUUID().toString()},"day" to day,"title" to label,"type" to type,"start" to a!!,"end" to b!!){page="schedule"}
                        },enabled=!busy && label.isNotBlank() && a!=null && b!=null && a<b){Text("저장")}
                    }
                    "slots" -> {
                        val accepted = state?.friends.orEmpty().any { it.status=="accepted" && (it.from==friendId || it.to==friendId) }
                        if(friend==null || !accepted) Empty("친구 관계가 해제됐어요.")
                        else {
                            val now = ZonedDateTime.now(seoul)
                            val all = profile.lessons.map{it.copy(owner="me")} + friend.lessons.map{it.copy(owner=friend.id)}
                            val slots = commonSlots(all,friend.id,now.toLocalDate(),now.toLocalTime())
                            Info(friend.nickname+"님과 일정 비교","한국 시간 · 앞으로 7일 · 09:00~22:00 · 1시간 이상")
                            WeekBars(slots,now.toLocalDate())
                            if(slots.isEmpty()) Empty("공통 빈 시간이 없어요.")
                            slots.forEach { slot -> CardBox {
                                Text(slot.date.toString()+" · "+hm(slot.start)+" ~ "+hm(slot.end))
                                Button({slotDate=slot.date.toString();slotStart=slot.start;slotEnd=slot.end;page="send"}){Text("이 시간에 놀자")}
                            } }
                        }
                    }
                    "send" -> {
                        var message by rememberSaveable{mutableStateOf("")}
                        Info(friend?.nickname ?: "친구",slotDate+" · "+hm(slotStart)+" ~ "+hm(slotEnd))
                        OnlineField(message,{message=it},"한마디 (선택)")
                        Button({vm.action("send","other" to friendId,"date" to slotDate,"start" to slotStart,"end" to slotEnd,"message" to message){page="hangouts"}},enabled=!busy){Text("신청 보내기")}
                    }
                    "hangouts" -> {
                        val all = state?.hangouts.orEmpty().sortedWith(compareBy({it.date},{it.start}))
                        if(all.isEmpty()) Empty("받거나 보낸 신청이 없어요.")
                        all.forEach { h -> CardBox {
                            Text(name(if(h.from==uid) h.to else h.from)+" · "+if(h.to==uid) "받은 신청" else "보낸 신청")
                            Text(h.date+" · "+hm(h.start)+" ~ "+hm(h.end))
                            Text(statusLabel(h.status))
                            Button({hangId=h.id;page="detail"}){Text("상세 보기")}
                        } }
                    }
                    "detail" -> if(hang==null) Empty("약속을 찾을 수 없어요.") else {
                        Info(statusLabel(hang.status),hang.date+" · "+hm(hang.start)+" ~ "+hm(hang.end))
                        if(hang.message.isNotBlank()) Info("한마디",hang.message)
                        if(hang.status=="pending" && hang.to==uid) Row {
                            Button({vm.action("answer","id" to hang.id,"accept" to true)},enabled=!busy){Text("수락")}
                            TextButton({vm.action("answer","id" to hang.id,"accept" to false)},enabled=!busy){Text("거절")}
                        }
                        val selected = places.find{it.id==hang.place}
                        if(selected!=null) Info(if(hang.status=="confirmed") "확정 장소 (샘플)" else "제안 장소 (샘플)",selected.name+" · "+selected.category)
                        if(hang.status=="place_selecting") {
                            if(hang.proposer==uid) Text("친구의 동의를 기다리고 있어요.")
                            else Button({vm.action("confirm","id" to hang.id,"place" to hang.place)},enabled=!busy){Text("이 장소에 동의")}
                        }
                        if(hang.status in listOf("accepted","place_selecting")) OutlinedButton({page="places"}){Text("장소 골라 제안하기")}
                    }
                    "places" -> if(hang!=null) {
                        val other = people.find{it.id==if(hang.to==uid) hang.from else hang.to}
                        Info("샘플 장소","장소 제안과 동의는 상대 기기에 동기화됩니다.")
                        places.sortedByDescending{ (profile.preferences[it.category] ?: 0)+(other?.preferences?.get(it.category) ?: 0) }.forEach { p ->
                            CardBox {
                                Text(p.name+" · "+p.category)
                                Button({vm.action("place","id" to hang.id,"place" to p.id){page="detail"}},enabled=!busy && hang.status in listOf("accepted","place_selecting")){Text("장소 제안")}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineField(value:String, change:(String)->Unit, label:String) {
    OutlinedTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth())
}
