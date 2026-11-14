package dev.arrival300

object DemoParking : ParkingRepository {
    override val demoData = true

    private val start = p(-900, 680)
    private val visitor = p(0, 0)
    private val drop = p(160, -80)

    private val hospital = Destination(
        id = "hospital",
        name = "한빛대학교병원",
        category = "대형 병원",
        address = "서울시 한빛구 새봄로 120",
        origin = Coordinate(37.54821, 126.94572),
        visitorEntrance = visitor,
        start = start,
        lots = listOf(
            lot("A", "본관 지하주차장", 0, Tariff(30, 1_500, 10, 600, 24_000),
                Approach(p(160, 335), p(160, 100), p(124, 100)),
                drive = Route(listOf(start, p(160, 680), p(160, 335), p(160, 100), p(124, 100)), 4),
                onward = Route(listOf(drop, p(160, -245), p(430, -245), p(430, 335), p(160, 335), p(160, 100), p(124, 100)), 4),
                walk = walking(p(108, 54), p(40, 54), p(40, 0), visitor),
                entry = "본관 뒤편 남행로에서 민트색 P 표지 아래 오른쪽 지하 경사로로 들어가세요.",
                steps = listOf("A구역 엘리베이터로 지상 1층까지 올라가세요.", "연결 통로를 따라 본관 쪽으로 이동하세요.", "본관 동쪽 자동문으로 들어가세요."),
                hours = "24시간", indoor = true, height = "2.1m"),
            lot("B", "별관 주차장", 1, Tariff(30, 1_000, 10, 500, 15_000),
                Approach(p(-570, -245), p(-300, -245), p(-300, -209)),
                drive = Route(listOf(start, p(-900, -245), p(-570, -245), p(-300, -245), p(-300, -209)), 5),
                onward = Route(listOf(drop, p(160, -245), p(160, -555), p(-570, -555), p(-570, -245), p(-300, -245), p(-300, -209)), 4),
                walk = walking(p(-263, -191), p(-263, -150), p(-110, -150), p(-110, 20), p(0, 20), visitor),
                entry = "벽돌 별관 앞 동행로에서 왼쪽 주황색 차단기로 들어가세요.",
                steps = listOf("주차장 북쪽 보행 게이트로 나오세요.", "별관 앞 횡단보도를 건너 보도 안내선을 따라가세요.", "본관 정문으로 들어가세요."),
                hours = "06:00–23:00", indoor = false),
            lot("C", "새봄 공영주차장", 2, Tariff(60, 1_200, 15, 300, 6_000),
                Approach(p(430, 335), p(430, 680), p(466, 680)),
                drive = Route(listOf(start, p(-900, 335), p(430, 335), p(430, 680), p(466, 680)), 3),
                onward = Route(listOf(drop, p(160, 100), p(430, 100), p(430, 335), p(430, 680), p(466, 680)), 3),
                walk = walking(p(380, 630), p(380, 130), p(40, 130), p(40, 0), visitor),
                entry = "북쪽 교차로 직전, 오른쪽 철제 캐노피 아래 진입로로 들어가세요.",
                steps = listOf("1층 서쪽 보행 출구로 나오세요.", "새봄로 보도를 따라 남쪽으로 이동해 횡단보도를 건너세요.", "본관 동쪽 출입구로 들어가세요."),
                hours = "24시간", indoor = true, height = "2.3m"),
            lot("D", "녹지광장 주차장", 3, null,
                Approach(p(-570, -245), p(-570, -555), p(-534, -555)),
                drive = Route(listOf(start, p(-570, 680), p(-570, -245), p(-570, -555), p(-534, -555)), 6),
                onward = Route(listOf(drop, p(160, -245), p(-570, -245), p(-570, -555), p(-534, -555)), 5),
                walk = walking(p(-488, -522), p(-488, -190), p(-110, -190), p(-110, 20), p(0, 20), visitor),
                entry = "가로수를 지나 왼쪽 낮은 돌담 사이로 들어가세요.",
                steps = listOf("광장 쪽 보행 출구로 나오세요.", "녹지광장 산책로와 별관 앞 보도를 따라가세요.", "본관 정문으로 들어가세요."),
                indoor = false),
            lot("X", "외곽 주차장", null, Tariff(30, 900, 30, 500),
                Approach(p(900, 100), p(1125, 100), p(1125, 60)),
                drive = Route(listOf(start, p(900, 680), p(900, 100), p(1125, 100), p(1125, 60)), 8),
                walk = walking(p(1090, 50), p(900, 50), p(900, 0), visitor),
                entry = "외곽도로에서 진입하세요.", steps = listOf("보도를 따라 병원으로 이동하세요."))
        ),
        dropOff = DropOff("본관 승하차 구역", Route(listOf(start, p(160, 680), p(160, -80)), 4))
    )

    private val mall = Destination(
        id = "mall",
        name = "온결 문화복합센터",
        category = "복합시설",
        address = "경기도 온결시 마루길 28",
        origin = Coordinate(37.40527, 127.10358),
        visitorEntrance = visitor,
        start = start,
        lots = listOf(
            lot("E", "센터 지하주차장", 4, Tariff(30, 2_000, 10, 700, 22_000),
                Approach(p(160, -555), p(160, -245), p(196, -245)),
                drive = Route(listOf(start, p(-900, -555), p(160, -555), p(160, -245), p(196, -245)), 6),
                walk = walking(p(138, -180), p(40, -180), p(40, 0), visitor),
                entry = "마루길 북행 방향 오른쪽, 파란색 패널 아래 경사로로 들어가세요.",
                steps = listOf("E구역 중앙 엘리베이터로 1층에 올라가세요.", "중앙 광장을 지나 센터 남쪽 로비로 들어가세요."),
                hours = "08:00–24:00", indoor = true, height = "2.2m"),
            lot("F", "마루 공영주차장", 5, Tariff(60, 1_000, 20, 400, 7_000),
                Approach(p(430, 335), p(730, 335), p(730, 371)),
                drive = Route(listOf(start, p(-900, 335), p(430, 335), p(730, 335), p(730, 371)), 4),
                walk = walking(p(690, 306), p(690, 130), p(40, 130), p(40, 0), visitor),
                entry = "동행로 왼쪽 흰색 기둥과 초록 패널 사이로 들어가세요.",
                steps = listOf("남쪽 보행 출구에서 나와 횡단보도를 건너세요.", "마루길 보도를 따라 센터 동쪽 출입구로 들어가세요."),
                hours = "24시간", indoor = true, height = "2.4m")
        )
    )

    override fun forNavigation(destination: NaviDestination): Destination? =
        if (destination.demo) listOf(hospital, mall).firstOrNull {
            it.name == destination.name && it.address == destination.address
        } else null

    override fun search(query: String): List<Destination> {
        val keywords = query.trim().split(Regex("\\s+"))
        return listOf(hospital, mall).filter { destination ->
            keywords.all { "${destination.name} ${destination.address} ${destination.category}".contains(it, ignoreCase = true) }
        }
    }

    private fun lot(
        id: String, name: String, photo: Int?, tariff: Tariff?, approach: Approach,
        drive: Route, walk: Route, entry: String, steps: List<String>,
        onward: Route? = null, hours: String? = null, indoor: Boolean? = null, height: String? = null
    ) = Lot(id, name, approach, walk, drive, onward, tariff, photo, entry, steps, hours, indoor, height)
}

