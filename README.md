# 놀친

Kotlin · Jetpack Compose · Material 3 · Hilt · Flow. Android 8(API 26) 이상, SDK 35, Gradle JDK 17 또는 21.

## Firebase 설정

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트를 만들고 Android 앱 패키지에 `com.nolchin.app`을 등록합니다. 내려받은 `google-services.json`을 `Nolchin/app/`에 넣습니다. 같은 Firebase 프로젝트의 설정으로 두 기기를 빌드합니다. 이메일/비밀번호 로그인에는 SHA 인증서 등록이 필요하지 않습니다.
2. 콘솔 **Authentication → Sign-in method**에서 **이메일/비밀번호**를 켭니다.
3. **Firestore Database → Create database**에서 기본 이름 **(default)**의 데이터베이스를 프로덕션 모드로 생성합니다. 이 프로젝트의 Firestore 규칙은 클라이언트 직접 접근을 차단합니다. 전용 Firebase 프로젝트에 배포하세요.
4. Cloud Functions 배포를 위해 프로젝트를 **Blaze**로 전환합니다. Firebase 콘솔의 Cloud Messaging에서 FCM HTTP v1이 활성화됐는지 확인합니다.
5. Node.js와 Firebase CLI를 준비하고, 이 폴더에서 아래 명령을 실행합니다. `YOUR_PROJECT_ID`는 Firebase 콘솔 프로젝트 설정에 표시되는 실제 ID입니다.

```powershell
npm install -g firebase-tools
firebase login
npm --prefix functions install
firebase deploy --only firestore:rules,functions --project YOUR_PROJECT_ID
```

함수 이름은 `nolchin`, 기본 리전은 `us-central1`입니다. 앱에는 서비스 계정 키가 필요하지 않습니다. 배포된 함수가 서버에서 Firebase Admin SDK로 푸시를 전송합니다.

## 빌드 및 실행

Android Studio에서 `Nolchin` 폴더를 열고 SDK 35와 Gradle JDK 17 또는 21을 선택합니다. Gradle Sync 후 Run을 누르거나 PowerShell에서 아래 명령을 실행합니다.

```powershell
.\gradlew.bat assembleDebug
adb devices
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.nolchin.app/.MainActivity
```

기기마다 `DEVICE_SERIAL`을 바꿔 실행합니다. 푸시 테스트에는 Google Play 서비스가 있는 실기기 또는 Google APIs 에뮬레이터를 사용합니다. 설정 파일 없이 빌드하면 로컬 데모로 시작합니다. 설정 파일이 있으면 온라인 계정 화면에서 시작합니다. 설정 파일을 추가한 뒤에는 APK를 다시 빌드하고 설치해야 합니다.

## 두 기기 테스트

1. 기기 A와 B에서 서로 다른 이메일로 회원가입하고 서로 다른 고유 아이디를 지정합니다. Android 13 이상에서는 홈의 **푸시 알림 허용**을 누릅니다.
2. A에서 B의 고유 아이디 또는 초대 코드를 검색하여 친구 요청을 보냅니다. B에서 푸시를 확인하고 **친구와 빈 시간** 화면에서 수락합니다.
3. 두 기기에서 같은 요일에 서로 다른 일정을 등록합니다. A에서 B의 공통 빈 시간을 열고 두 일정 모두 막대에서 제외되는지 확인합니다. 진한 노란색은 09:00~22:00 중 두 사람 모두 비어 있는 1시간 이상의 구간입니다. 온라인 날짜와 시각은 한국 시간 기준입니다.
4. A에서 빈 시간을 선택해 놀자 신청을 보내고, B의 알림과 신청 목록에서 수락합니다. 거절 흐름은 새 신청으로 별도 확인할 수 있습니다.
5. A가 장소를 제안하면 B에 표시됩니다. B가 동의하면 두 기기에서 확정 상태와 같은 장소를 확인할 수 있습니다.
6. 확정된 장소 카테고리가 다음 추천에서 우선 표시되는지 확인합니다.

온라인 화면은 5초 간격으로 서버 상태를 갱신합니다. 서버의 `profiles`, `handles`, `codes`, `friends`, `hangouts`, `tokens` 컬렉션은 인증된 요청을 검증하는 Callable Function을 통해서만 접근합니다. 친구를 수락하기 전에는 상대 일정이 공유되지 않습니다. 추천에는 고정 샘플 장소를 사용하며 실제 위치 및 Places API 연결은 이 버전에 포함되지 않습니다.
