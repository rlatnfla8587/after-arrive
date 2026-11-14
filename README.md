# After Arrive — 도착의 완성, 마지막 300m

**팀 HU:MOTION · 현대자동차그룹 차량용 앱 개발 공모전**

After Arrive는 차량 내비가 도착을 알린 뒤, 주차 위치에서 방문할 건물의 실제 출입구까지 마지막 도보 안내를 이어 주는 앱입니다.

## 제출용 APK

[HU-MOTION_AfterArrive_release.apk](apk/HU-MOTION_AfterArrive_release.apk)

- 저장소 경로 `apk/HU-MOTION_AfterArrive_release.apk`
- SHA-256 `00b2873cfc8c76d7e72fd130eab0b5e3cbb627f87370b9c489c230662ba71dec`
- 패키지 `dev.arrival300.pleos` · 버전 `4.0-demo` (versionCode 4)
- Pleos Connect 연동 코드가 포함된 서명된 release 빌드입니다. 일반 Android에서도 앱의 로컬 시연을 실행할 수 있습니다.

## 실행 방법

1. 위 APK 링크에서 **Download raw file**을 선택해 APK를 내려받습니다.
2. Android Studio의 **Device Manager**에서 Android API 34 휴대폰 AVD 또는 Pleos Connect v2.1.0 AVD를 켭니다.
3. 저장소 루트의 PowerShell에서 아래 명령을 실행합니다. APK만 내려받았다면 `$apk`를 실제 파일 경로로 바꿉니다. 여러 기기가 연결된 경우 `$device`에 대상 ID를 지정합니다.

```powershell
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
& $adb devices -l
$device = 'emulator-5554' # adb devices 결과의 실제 기기 ID로 교체
$apk = '.\apk\HU-MOTION_AfterArrive_release.apk'
& $adb -s $device install -r $apk
& $adb -s $device shell am start -n dev.arrival300.pleos/dev.arrival300.MainActivity
```

이전에 다른 키로 서명한 같은 패키지의 앱이 설치되어 있다면 새 에뮬레이터를 사용하세요. 기존 앱 데이터가 필요한 경우 해당 설치본을 보존할 수 있습니다.

### 일반 Android 시연 순서

1. 홈의 **앱 정보(ⓘ) → 데모 모드 켜기 → 닫기 → 시연 시작**을 누릅니다. Android 13 이상에서 알림 권한을 허용하면 백그라운드 알림 시연도 가능합니다.
2. 목적지 수신 → 목적지 주변 도착 → 정차·P → 시동 OFF를 거쳐 **한빛대학교병원 본관 동문, 도보 190m** 안내가 열립니다. **시동 OFF 시뮬레이션 · 도로변**으로 주차장을 선택하지 않는 경우도 시연할 수 있습니다.
3. **주차장**에서 500m·1km 범위의 후보를 비교합니다. 주차시간을 바꾸면 예상 요금이 갱신됩니다. 가까운 순의 기준은 보행 출구에서 방문 입구까지의 도보 거리입니다.
4. **내 차 찾기**에서 저장한 위치·층·구역·메모를 확인하고 복귀 경로를 열 수 있습니다. 앱 정보의 **도보 경로 이탈 시뮬레이션**으로 데모 경로 재탐색을 확인할 수 있습니다.

시연의 목적지, 입구 사진, 요금, 지도와 경로는 앱에 포함된 가상 데이터입니다. Android 시연에는 API 키, 서버, 차량 연결이 필요하지 않습니다.

## 개발 및 테스트 환경

| 대상 | 버전과 규격 |
| --- | --- |
| 개발 환경 | Windows · Kotlin 2.2.10 · Jetpack Compose · JDK 17 |
| 빌드 | Gradle Wrapper 8.11.1 · Android Gradle Plugin 8.9.2 |
| Android SDK | minSdk 28 · targetSdk 34 · compileSdk 35 · Build-Tools 35.0.0 |
| 일반 Android 에뮬레이터 | Android 14 / API 34 · Google APIs x86_64 · 세로 1080 × 2400 px · 420 dpi |
| Pleos Connect 에뮬레이터 | v2.1.0 · Android Automotive OS 14 / API 34 · x86_64 · 가로 1024 × 768 px · 160 dpi |
| 차량 내비 연동 | Pleos NaviHelper 3.0.5 · Pleos Playground Maven 저장소 |

GPS 기반 주차 위치 저장에는 위치 권한이 필요합니다. 도보 방향 표시는 기기가 제공하는 방향 센서를 사용합니다. 차량 화면에서는 플랫폼이 허용하는 차량 속도·기어·시동 신호와 UX 제한을 사용합니다.

## Windows에서 소스 빌드

1. Android Studio에서 이 README가 있는 프로젝트 루트를 엽니다. Gradle JDK를 17로 설정하고 SDK Manager에서 **Android SDK Platform 35**, **Build-Tools 35.0.0**, **Platform-Tools**를 설치합니다.
2. PowerShell에서는 JDK 17을 `JAVA_HOME`으로 설정하고, SDK를 `ANDROID_HOME`이나 Android Studio가 만든 `local.properties`의 `sdk.dir`로 지정합니다.
3. 프로젝트 루트에서 아래 작업을 실행합니다. Pleos 빌드는 Pleos Maven 저장소에 접근할 수 있어야 합니다.

```powershell
# 일반 Android 개발 빌드 및 단위 테스트
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest

# Pleos Connect 제출 APK와 같은 release 변형 빌드
.\gradlew.bat '-Ppleos=true' :app:assemblePleosRelease
```

일반 개발 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. Pleos release 빌드는 `app/build/outputs/apk/pleos/release/`에 서명 전 APK를 생성합니다. 설치에는 저장소의 서명된 제출 APK를 사용하거나 자체 키로 서명합니다.

## Pleos Connect에서 확인하기

1. Android Studio의 **SDK Manager → SDK Update Sites**에 Pleos 시스템 이미지 사이트를 추가합니다. **SDK Platforms → Show Package Details → API 34**에서 Pleos 이미지를 설치한 뒤, **Device Manager → Automotive → Pleos Connect v2.1.0**에서 AVD를 만듭니다. [Pleos Connect 공식 설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
2. Pleos AVD를 켠 뒤 위 설치 명령에서 대상 ID를 Pleos AVD로 바꿉니다. 차량 내비 목적지와 도착 콜백을 확인하려면 연결 가능한 내비 서비스가 필요합니다. 차량 속도와 위치는 앱이 띄우는 권한 안내에 따라 허용합니다.
3. P·정차·시동 신호는 지원되는 환경에서 Virtual Console로 재현할 수 있습니다. SDK 연동 시험 메뉴는 `assemblePleosDebug`로 만든 개발용 APK에서 제공합니다. [Pleos Virtual Console 공식 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

Pleos 코드는 NaviHelper 목적지·도착 콜백과 AAOS 차량 상태를 읽는 어댑터를 포함합니다. 실제 차량에서 휴대폰으로 전달하는 통신, 실제 건물 입구와 경로·요금 데이터 공급자는 추가 연결이 필요합니다.

## 제출 APK 검증

- Pleos release 빌드 및 필수 릴리스 Lint를 통과했고 APK Signature Scheme v3 서명을 검증했습니다.
- `:app:testDebugUnitTest`를 다시 실행해 단위 테스트 27개가 통과했습니다.
- Android API 34 에뮬레이터에서 제출 APK를 새로 설치했습니다. 시동 OFF 후 본관 동문까지 190m 도보 안내가 열리고 주변 주차장 화면에 도보 거리와 예상 요금이 표시되는 것을 확인했습니다.
- Pleos Connect v2.1.0의 새 AVD에서 같은 APK를 설치해 홈 화면과 내비 서비스 연결 상태 표시를 확인했습니다. 내비 목적지·도착 콜백이나 차량 전원 신호 수신은 추가 검증 항목입니다.
- 실제 차량의 내비·시동 신호와 휴대폰 전송은 이 제출 APK의 검증 범위에 포함하지 않았습니다.

## 프로젝트 구성

| 경로 | 역할 |
| --- | --- |
| `app/src/main/` | Compose 화면, 로컬 시연 데이터, 도보·주차 안내 및 주차 위치 저장 |
| `app/src/demo/` | 일반 Android 변형의 차량 연동 대체 구현 |
| `app/src/pleos/` | Pleos NaviHelper와 차량 환경 연결 |
| `app/src/test/` | 요금·거리·주행 제한·도보 전환·지도와 방향 표시 관련 테스트 |
| `apk/` | 심사용 서명된 release APK |
