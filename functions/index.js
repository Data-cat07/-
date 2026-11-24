const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { randomUUID, createHash } = require("node:crypto");
initializeApp();
const db = getFirestore();
const col = name => db.collection(name);
function check(condition, message = "요청을 처리할 수 없어요.") {
  if (!condition) throw new HttpsError("failed-precondition", message);
}
const safeId = value => typeof value === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(value);
const clean = (value, max) => typeof value === "string" ? value.trim().slice(0, max) : "";
const pair = (a, b) => createHash("sha256").update(JSON.stringify([a,b].sort())).digest("hex");
const categories = { cafe:"카페", park:"공원", board:"보드게임카페", movie:"영화관", dessert:"디저트" };
const minutes = (start,end) => Number.isInteger(start) && Number.isInteger(end) && start >= 0 && end <= 1440 && end > start;
async function notify(uid, title) {
  const tokens = await col("tokens").where("uid","==",uid).limit(20).get();
  await Promise.all(tokens.docs.map(async doc => {
    try { await getMessaging().send({ token:doc.data().token, data:{title,recipient:uid} }); }
    catch (error) {
      if (error.code === "messaging/registration-token-not-registered") {
        await db.runTransaction(async tx => {
          if ((await tx.get(doc.ref)).data()?.uid === uid) tx.delete(doc.ref);
        });
      } else console.error("FCM:", error.code);
    }
  }));
}
exports.nolchin = onCall(async req => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated","로그인이 필요해요.");
  const a = req.data || {};
  const me = col("profiles").doc(uid);
  if (a.op === "register") {
    const username = clean(a.username,20).toLowerCase();
    const nickname = clean(a.nickname,30);
    check(/^[a-z0-9_]{3,20}$/.test(username) && nickname, "닉네임과 아이디를 확인해 주세요.");
    await db.runTransaction(async tx => {
      if ((await tx.get(me)).exists) return;
      const name = col("handles").doc(username);
      check(!(await tx.get(name)).exists,"이미 사용 중인 아이디예요.");
      const code = randomUUID().replaceAll("-","").slice(0,12);
      tx.create(name,{uid});
      tx.create(col("codes").doc(code),{uid});
      tx.create(me,{nickname,username,code,lessons:[],preferences:{}});
    });
    return {};
  }
  if (["token","logout"].includes(a.op)) {
    const token = clean(a.token,4096);
    if (!token) return {};
    const ref = col("tokens").doc(createHash("sha256").update(token).digest("hex"));
    await db.runTransaction(async tx => {
      if (a.op === "token") tx.set(ref,{uid,token});
      else if ((await tx.get(ref)).data()?.uid === uid) tx.delete(ref);
    });
    return {};
  }
  const profile = (await me.get()).data();
  if (a.op === "state" && !profile) return {};
  check(profile,"프로필을 먼저 만들어 주세요.");
  if (a.op === "state") {
    const [edges, hangs] = await Promise.all([
      col("friends").where("members","array-contains",uid).get(),
      col("hangouts").where("members","array-contains",uid).get()
    ]);
    const friends = edges.docs.map(d=>({id:d.id,...d.data()}));
    const hangouts = hangs.docs.map(d=>({id:d.id,...d.data()}));
    const ids = [...new Set([...friends,...hangouts].flatMap(r=>r.members).filter(id=>id!==uid))];
    const accepted = new Set(friends.filter(f=>f.status==="accepted").flatMap(f=>f.members));
    const people = await Promise.all(ids.map(async id => {
      const p = (await col("profiles").doc(id).get()).data();
      return {id,nickname:p.nickname,username:p.username,lessons:accepted.has(id)?p.lessons:[],preferences:accepted.has(id)?p.preferences:{}};
    }));
    return { profile, friends, hangouts, people };
  }
  if (a.op === "search") {
    const q = clean(a.query,32).toLowerCase();
    check(safeId(q),"아이디 또는 초대 코드를 확인해 주세요.");
    const [name,code] = await Promise.all([col("handles").doc(q).get(),col("codes").doc(q).get()]);
    const id = (name.data() || code.data())?.uid;
    if (!id || id === uid) return {};
    const {nickname, username} = (await col("profiles").doc(id).get()).data();
    return {id,nickname,username};
  }
  if (a.op === "lesson" || a.op === "deleteLesson") {
    check(safeId(a.id));
    await db.runTransaction(async tx => {
      const lessons = (await tx.get(me)).data().lessons.filter(l=>l.id!==a.id);
      if (a.op === "lesson") {
        const title = clean(a.title,80);
        check(title && Number.isInteger(a.day) && a.day>=1 && a.day<=7 && minutes(a.start,a.end) && lessons.length<100,"일정 내용을 확인해 주세요.");
        lessons.push({id:a.id, title, day:a.day, start:a.start, end:a.end, type:a.type==="기타 일정"?a.type:"학원"});
      }
      tx.update(me,{lessons});
    });
    return {};
  }
  let event;
  if (["friend","answerFriend","removeFriend","send"].includes(a.op)) {
    check(safeId(a.other) && a.other !== uid);
    const other = a.other, edge = col("friends").doc(pair(uid,other));
    event = await db.runTransaction(async tx => {
      const e = (await tx.get(edge)).data();
      if (a.op === "friend") {
        check((await tx.get(col("profiles").doc(other))).exists);
        check(!e || e.status==="rejected","이미 요청 또는 친구 관계가 있어요.");
        tx.set(edge,{from:uid,to:other,members:[uid,other],status:"pending"});
        return [other,"친구 요청이 왔어요"];
      }
      if (a.op === "answerFriend") {
        check(e?.to===uid && e.status==="pending");
        tx.update(edge,{status:a.accept===true?"accepted":"rejected"});
        return a.accept===true?[other,"친구 요청이 수락됐어요"]:null;
      }
      check(e?.status==="accepted","수락된 친구에게만 보낼 수 있어요.");
      if (a.op === "removeFriend") { tx.delete(edge); return null; }
      const date = clean(a.date,10);
      const stamp = new Date(date+"T00:00:00+09:00");
      const now = new Date();
      const today = new Date(now.getTime()+32400000).toISOString().slice(0,10);
      check(/^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(+stamp) &&
        new Date(+stamp+32400000).toISOString().slice(0,10)===date &&
        date>=today && +stamp<Date.parse(today+"T00:00:00+09:00")+7*86400000 &&
        minutes(a.start,a.end) && a.start>=540 && a.end<=1320 && a.end-a.start>=60 &&
        +stamp+a.start*60000>+now,"선택한 시간이 지났거나 범위를 벗어났어요.");
      const day = new Date(date+"T00:00:00Z").getUTCDay() || 7;
      const p1 = (await tx.get(me)).data();
      const p2 = (await tx.get(col("profiles").doc(other))).data();
      check(p1.lessons.concat(p2.lessons).every(l=>l.day!==day || l.start>=a.end || l.end<=a.start),"시간표가 바뀌었어요. 다른 시간을 골라 주세요.");
      tx.create(col("hangouts").doc(),{members:[uid,other],from:uid,to:other,date,start:a.start,end:a.end,message:clean(a.message,300),status:"pending",place:"",proposer:""});
      return [other,"놀자 신청이 도착했어요"];
    });
  } else {
    check(safeId(a.id));
    const ref = col("hangouts").doc(a.id);
    event = await db.runTransaction(async tx => {
      const h = (await tx.get(ref)).data();
      check(h?.members.includes(uid));
      const other = h.members.find(id=>id!==uid);
      if (a.op === "answer") {
        check(h.to===uid && h.status==="pending");
        tx.update(ref,{status:a.accept===true?"accepted":"rejected"});
        return a.accept===true?[other,"놀자 신청이 수락됐어요"]:null;
      }
      if (a.op === "place") {
        check(["accepted","place_selecting"].includes(h.status) && Object.hasOwn(categories,a.place));
        tx.update(ref,{status:"place_selecting",place:a.place,proposer:uid});
        return [other,"친구가 장소를 제안했어요"];
      }
      check(a.op==="confirm" && h.status==="place_selecting" && h.proposer===other && h.place===a.place && Object.hasOwn(categories,h.place));
      const profiles = await Promise.all(h.members.map(id=>tx.get(col("profiles").doc(id))));
      for (const p of profiles) {
        const scores = p.data().preferences;
        const category = categories[h.place];
        tx.update(p.ref,{preferences:{...scores,[category]:(scores[category]||0)+1}});
      }
      tx.update(ref,{status:"confirmed"});
      return [other,"약속 장소가 확정됐어요"];
    });
  }
  if (event) await notify(...event).catch(error=>console.error("알림 전송:",error.code));
  return {};
});

