package dev.arrival300

import android.Manifest
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h440dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArrivalScreenTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun pricePinsUpdateAndRestrictedStatesRemoveComparison() {
        ui.onNodeWithContentDescription("A 주차장 · 6,900원").assertDoesNotExist()
        ui.onNodeWithText("주변 주차장 비교").performScrollTo().performClick()
        ui.onNodeWithText(DemoDataLabel).assertIsDisplayed()
        ui.onNodeWithText("데모 주차", substring = true).assertDoesNotExist()
        ui.onNodeWithContentDescription("A 주차장 · 6,900원").assertIsDisplayed()
        ui.onNodeWithText("30분").performScrollTo().performClick()
        ui.onNodeWithContentDescription("A 주차장 · 1,500원").assertIsDisplayed()
        ui.onNodeWithContentDescription("C 주차장 · 1,200원").performClick()
        val lotName = DemoArrivalWalks.hospital.lots.first { it.id == "C" }.name
        ui.onNodeWithText(lotName).assertIsDisplayed()
        ui.onAllNodesWithText("운전")[0].assertExists()
        ui.onNodeWithText("현재 위치 → 차량 진입구").assertDoesNotExist()
        ui.onNodeWithText("보행 출구 → 방문 출입구").assertDoesNotExist()
        ui.onNodeWithText("C · 이 주차장으로 안내", substring = true).assertIsDisplayed()

        enableDemoMode()
        ui.onNodeWithText("데모 주차", substring = true).performClick()
        ui.onNodeWithText("주행 상태 재현").performClick()
        ui.onNodeWithText("주행 중에는 짧은 안내만 표시해요").assertIsDisplayed()
        ui.onNodeWithText("목적지 검색").assertDoesNotExist()
        ui.onNodeWithText("입구·요금 상세").assertDoesNotExist()
        ui.onNodeWithContentDescription("C 주차장 · 1,200원").assertDoesNotExist()

        ui.onNodeWithText("데모 주행", substring = true).performClick()
        ui.onNodeWithText("상태 미확인 재현").performClick()
        ui.onNodeWithText("주차 상태를 확인하고 있어요").assertIsDisplayed()
        ui.onNodeWithText("목적지 검색").assertDoesNotExist()

        ui.onNodeWithText("데모 미확인", substring = true).performClick()
        ui.onNodeWithText("주차 상태 재현").performClick()
        ui.onNodeWithText("목적지 검색").assertIsDisplayed()

        ui.onNodeWithText("홈").performClick()
        ui.onNodeWithContentDescription("A 주차장 · 1,500원").assertDoesNotExist()
        ui.onNodeWithText("주차장").performClick()
        ui.onNodeWithContentDescription("A 주차장 · 1,500원").assertIsDisplayed()
    }

    @Test fun regularParkingGuidanceKeepsTheRouteWithoutAnEntranceCue() {
        ui.onNodeWithText("주변 주차장 비교").performScrollTo().performClick()
        ui.onNodeWithContentDescription("B 주차장 · 5,500원").performClick()
        ui.onNodeWithText("B · 이 주차장으로 안내", substring = true).performClick()
        val trip = ViewModelProvider(ui.activity)[TripViewModel::class.java]
        ui.runOnIdle {
            assertEquals(trip.selected.drive, trip.target?.route)
            assertEquals(trip.destination.origin.offset(trip.selected.entrance), trip.target?.coordinate)
        }
        ui.onNodeWithText("주차장 경로를 표시하고 있어요").assertIsDisplayed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        ui.runOnIdle { assertNull(trip.cue) }
        ui.onNodeWithText("주차장 경로 안내를 마쳤어요").assertIsDisplayed()
        ui.onNodeWithText("주차장 진입구에 도착했어요").assertDoesNotExist()
        ui.onNodeWithText("주차 완료 · 도보 안내", substring = true).performClick()
        ui.onNodeWithText("주차장 상세 다시 보기").performScrollTo().assertIsDisplayed()
    }

    @Test fun selectedGarageStartsDirectlyAndStillLeadsToWalking() {
        ui.onNodeWithText("주변 주차장 비교").performScrollTo().performClick()
        enableDemoMode()
        ui.onNodeWithText("이동 방식").assertDoesNotExist()
        ui.onNodeWithText("바로 주차").assertDoesNotExist()
        ui.onNodeWithText("동승자 먼저 하차").assertDoesNotExist()
        ui.onNodeWithContentDescription("B 주차장 · 5,500원").performClick()
        ui.onNodeWithText("B · 이 주차장으로 안내", substring = true).performClick()
        val trip = ViewModelProvider(ui.activity)[TripViewModel::class.java]
        ui.runOnIdle {
            assertEquals("B", trip.selectedId)
            assertEquals(trip.destination.origin.offset(trip.selected.entrance), trip.target?.coordinate)
        }
        ui.onNodeWithText("목적지 검색").assertDoesNotExist()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        ui.runOnIdle { assertTrue(trip.cue.orEmpty().contains("왼쪽")) }
        ui.onNodeWithText("주차 완료 · 도보 안내", substring = true).performClick()
        ui.onNodeWithText("주차장에서 도보 안내 중").performScrollTo().assertIsDisplayed()
    }
    @Test fun homeFollowsNavigationArrivalThenParkedIgnitionOffToBuildingEntrance() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ui.onNodeWithText("내비에서 받은 목적지").assertIsDisplayed()
        ui.onNodeWithText("주변 주차장 비교").performScrollTo().performClick()
        ui.onNodeWithContentDescription("C 주차장 · 2,400원").performClick()
        ui.onNodeWithText("홈").performClick()
        ui.onNodeWithText("활동").assertDoesNotExist()
        ui.onNodeWithText("내 차 찾기").performClick().assertIsSelected()
        ui.onNodeWithText("아직 저장된 주차 위치가 없어요.").assertIsDisplayed()
        ui.onNodeWithText("주차 위치를 저장하면 나중에 내 차를 쉽게 찾을 수 있어요.").assertIsDisplayed()
        ui.onNodeWithText("현재 위치 저장").assertIsDisplayed()
        ui.onNodeWithText("오늘의 도착 브리핑").assertDoesNotExist()
        ui.onNodeWithContentDescription("저장된 주차 위치 지도").assertDoesNotExist()
        ui.onNodeWithText("홈").performClick()
        ui.onNodeWithContentDescription("내 차 찾기 열기").performClick()
        ui.onNodeWithText("현재 위치 저장").assertIsDisplayed()
        ui.onNodeWithText("홈").performClick()
        ui.onNodeWithText("시연 시작").assertDoesNotExist()
        enableDemoMode()
        ui.onNodeWithText("시연 시작").performScrollTo().performClick()
        ui.onNodeWithText("한빛대학교병원").assertIsDisplayed()
        ui.onNodeWithText("도보 안내 시작").assertDoesNotExist()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_750))
        ui.onNodeWithText("목적지 주변에 도착했어요").assertIsDisplayed()
        ui.onNodeWithText("여기서 도보 안내").assertDoesNotExist()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_450))
        ui.onNodeWithText("도보 경로가 준비됐어요").assertIsDisplayed()
        ui.onNodeWithText("시뮬레이션 · 시동 OFF 대기").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("도보 안내 시작").assertDoesNotExist()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        ui.onNodeWithText("도보 안내 시작").assertIsDisplayed()
        ui.onNodeWithText("도보 190m · 약 3분").assertIsDisplayed()
        ui.onNodeWithText("본관 지하주차장 · A구역", substring = true).assertIsDisplayed()
        val trip = ViewModelProvider(ui.activity)[TripViewModel::class.java]
        ui.runOnIdle {
            assertEquals("C", trip.selectedId)
            assertEquals("본관 동문", trip.arrival.prepared?.entranceName)
        }

        ui.onNodeWithText("시뮬레이션 · 도보 안내 알림 표시").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("휴대폰으로 도보 안내 보내기").assertDoesNotExist()
        val map = ui.onNodeWithContentDescription("실제 주차 위치에서 본관 동문까지의 도보 지도")
        map.performScrollTo()
        val marker = ui.onNodeWithText("주차 위치")
        val initial = marker.fetchSemanticsNode().boundsInRoot.center
        map.performTouchInput {
            pinch(
                start0 = center - Offset(35f, 0f),
                end0 = center - Offset(70f, 0f),
                start1 = center + Offset(35f, 0f),
                end1 = center + Offset(70f, 0f)
            )
        }
        val zoomed = marker.fetchSemanticsNode().boundsInRoot.center
        assertTrue(zoomed.x > initial.x)
        map.performTouchInput { swipe(center, center + Offset(52f, 20f)) }
        assertTrue(marker.fetchSemanticsNode().boundsInRoot.center.x > zoomed.x)
        map.performTouchInput { doubleClick(center) }
        assertEquals(initial, marker.fetchSemanticsNode().boundsInRoot.center)

        ui.onNodeWithText("내 차 찾기").performClick()
        ui.onNodeWithContentDescription("저장된 주차 위치 지도").assertIsDisplayed()
        ui.onNodeWithText("본관 지하주차장").assertIsDisplayed()
        ui.onNodeWithText("주차 위치 수정").performScrollTo().assertIsDisplayed()
        ui.runOnIdle {
            val guidance = trip.parkingGuidance
            val saved = requireNotNull(guidance.saved)
            val edited = saved.copy(floor = "B2", zone = "C-12", memo = "기둥 옆")
            guidance.showEdit(saved)
            guidance.saveEdit(edited)
            assertEquals(edited, guidance.saved)
        }
        ui.onNodeWithText("층 B2 · 구역 C-12").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("도보 안내 시작").performScrollTo().performClick()
        ui.onNodeWithContentDescription("저장된 주차 위치까지의 도보 지도").assertIsDisplayed()
        ui.onNodeWithText("시연 데이터 · 차량으로 복귀 중").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("B2 · C-12 · 기둥 옆").performScrollTo().assertIsDisplayed()
        ui.runOnIdle {
            assertEquals(DemoArrivalWalks.target, trip.arrival.destination)
            val returning = requireNotNull(trip.parkingGuidance.carReturn)
            assertTrue(returning.walk.entranceCoordinate.distanceFrom(returning.parking.coordinate) <= 2)
        }
        ui.onNodeWithText("복귀 안내 닫기").performScrollTo().performClick()
        ui.onNodeWithContentDescription("저장된 주차 위치 지도").assertIsDisplayed()
        ui.onNodeWithText("홈").performClick()
        ui.onNodeWithContentDescription("실제 주차 위치에서 본관 동문까지의 도보 지도").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("주차장").performClick()
        ui.onNodeWithText("추천 주차장").performScrollTo().assertIsDisplayed()
    }

    private fun enableDemoMode() {
        ui.onNodeWithContentDescription("앱 정보").performClick()
        ui.onNodeWithText("시동 OFF 시뮬레이션 · 도로변").assertDoesNotExist()
        ui.onNodeWithText("데모 모드 켜기").performClick()
        ui.onNodeWithText("시동 OFF 시뮬레이션 · 도로변").assertExists()
        ui.onNodeWithText("닫기").performClick()
    }
}




