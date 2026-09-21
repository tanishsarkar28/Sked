package com.sked.sked_app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sked.sked_app.MainActivity
import com.sked.sked_app.R
import com.sked.sked_app.exam.ExamItem
import com.sked.sked_app.exam.ExamParser
import com.sked.sked_app.exam.ExamStatus
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Sked Palette — logo-anchored ─────────────────────────────────────────────
private val Ink        = Color(0xFF0A0A0A)
private val Slab       = Color(0xFF141414)
private val SlabElevated = Color(0xFF1A1A1A)
private val Rule       = Color(0xFF252525)
private val Chalk      = Color(0xFFE8E6E3)
private val Slate          = Color(0xFF7A7774)
private val TextDim        = Color(0xFF5A5856)
private val Blaze          = Color(0xFFFF6B1A)
private val PresentGreen   = Color(0xFF22C55E)
private val AbsentRed      = Color(0xFFEF4444)
private val DutyLeaveBlue  = Color(0xFF38BDF8)
private val UpcomingOrange = Blaze
private val PendingIndigo  = Color(0xFF818CF8)
private val NotMarkedGrey  = Color(0xFF71717A)

const val PREFS_NAME = "SkedWidget"
const val KEY_TODAY  = "today_entries"

class TimetableWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_TODAY, null)
        val userId = prefs.getString(TimetableRefreshWorker.KEY_USER_ID, null)
        val entries = parseEntries(context, rawJson)

        val isSunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        val exams = try {
            ExamParser.loadExamsFromPrefs(context)
        } catch (_: Exception) {
            emptyList()
        }
        val todayExam = exams.firstOrNull { it.getStatus() == ExamStatus.TODAY }
        val tomorrowExam = exams.firstOrNull { it.getStatus() == ExamStatus.TOMORROW }

        val mondayEntries = if (isSunday) {
            val allEntries = com.sked.sked_app.TimetableParser.loadFromPrefs(context)
            val monList = com.sked.sked_app.TimetableParser.filterByDay(allEntries, "Monday")
            monList.map { item ->
                ClassData(
                    courseCode = item.courseCode,
                    start      = item.start,
                    end        = item.end,
                    room       = item.room,
                    type       = item.type,
                    timeRange  = item.timeRange,
                    teacher    = item.teacher,
                    section    = item.section
                )
            }
        } else emptyList()

        provideContent {
            GodLevelWidgetContent(
                context = context,
                entries = entries,
                isLoggedIn = !userId.isNullOrBlank(),
                isSunday = isSunday,
                mondayEntries = mondayEntries,
                todayExam = todayExam,
                tomorrowExam = tomorrowExam
            )
        }
    }

    @Composable
    private fun GodLevelWidgetContent(
        context: Context,
        entries: List<ClassData>,
        isLoggedIn: Boolean,
        isSunday: Boolean = false,
        mondayEntries: List<ClassData> = emptyList(),
        todayExam: ExamItem? = null,
        tomorrowExam: ExamItem? = null
    ) {
        val launchApp = actionStartActivity<MainActivity>()

        val todayStr = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val nowCal = Calendar.getInstance()
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // Determine current class status
        var liveClass: ClassData? = null
        var nextClass: ClassData? = null
        var completedCount = 0

        entries.forEach { entry ->
            val startM = parseMinutes(entry.start)
            val endM = parseMinutes(entry.end.ifEmpty { entry.start })

            if (startM > 0 && endM > 0) {
                if (nowMinutes in startM..endM) {
                    liveClass = entry
                } else if (nowMinutes > endM) {
                    completedCount++
                } else if (nowMinutes < startM && nextClass == null) {
                    nextClass = entry
                }
            }
        }

        val allCompleted = entries.isNotEmpty() && completedCount >= entries.size

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Ink))
                .cornerRadius(16.dp)
                .clickable(launchApp)
                .padding(12.dp)
        ) {
            // ── Header (Always fixed at top) ─────────────────────────────
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(launchApp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SKED. wordmark
                Text(
                    text = "SKED.",
                    style = TextStyle(
                        color = ColorProvider(Blaze),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(GlanceModifier.defaultWeight())

                // Date + Class count or Sunday subtitle
                val headerSubtitle = when {
                    todayExam != null -> "$todayStr · ${todayExam.examType} EXAM"
                    isSunday -> todayStr
                    entries.isNotEmpty() -> "$todayStr · ${entries.size} classes"
                    else -> todayStr
                }
                Text(
                    text = headerSubtitle,
                    style = TextStyle(
                        color = ColorProvider(if (todayExam != null) Blaze else Slate),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // ── Content States ────────────────────────────────────────────
            if (!isLoggedIn) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(ColorProvider(Slab))
                        .cornerRadius(8.dp)
                        .clickable(launchApp)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SKED.",
                        style = TextStyle(
                            color = ColorProvider(Blaze),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "Tap to sign in with UMS.",
                        style = TextStyle(
                            color = ColorProvider(Slate),
                            fontSize = 12.sp
                        )
                    )
                    Spacer(GlanceModifier.height(12.dp))
                    Row(
                        modifier = GlanceModifier
                            .background(ColorProvider(Blaze))
                            .cornerRadius(4.dp)
                            .clickable(launchApp)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "SIGN IN.",
                            style = TextStyle(
                                color = ColorProvider(Ink),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else if (isSunday && todayExam == null) {
                SundayRelaxCard(modifier = GlanceModifier.defaultWeight(), onClick = launchApp)
            } else if (todayExam != null) {
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    item {
                        ExamSpotlightCard(
                            exam = todayExam,
                            isToday = true,
                            onClick = launchApp
                        )
                        Spacer(GlanceModifier.height(10.dp))
                    }

                    if (entries.isNotEmpty()) {
                        item {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(launchApp)
                                    .padding(top = 4.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CLASSES",
                                    style = TextStyle(
                                        color = ColorProvider(Slate),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        items(
                            entries,
                            itemId = { entry -> (entry.start + "_" + entry.courseCode).hashCode().toLong() }
                        ) { entry ->
                            val startM = parseMinutes(entry.start)
                            val endM = parseMinutes(entry.end.ifEmpty { entry.start })
                            val isDone = endM > 0 && nowMinutes > endM
                            val isLive = startM > 0 && endM > 0 && nowMinutes in startM..endM
                            val isNext = entry == nextClass

                            DepartureRow(
                                entry = entry,
                                isDone = isDone,
                                isLive = isLive,
                                isNext = isNext,
                                onClick = launchApp
                            )
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    } else {
                        item {
                            ExamPreparationCard(
                                exam = todayExam,
                                onClick = launchApp
                            )
                        }
                    }
                }
            } else if (entries.isEmpty()) {
                if (tomorrowExam != null) {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        item {
                            ExamSpotlightCard(
                                exam = tomorrowExam,
                                isToday = false,
                                onClick = launchApp
                            )
                            Spacer(GlanceModifier.height(10.dp))
                        }
                        item {
                            ExamPreparationCard(
                                exam = tomorrowExam,
                                onClick = launchApp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(ColorProvider(Slab))
                            .cornerRadius(8.dp)
                            .clickable(launchApp)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nothing today.",
                            style = TextStyle(
                                color = ColorProvider(Chalk),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = "You're clear.",
                            style = TextStyle(
                                color = ColorProvider(Slate),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val spotlightItem = liveClass ?: nextClass

                    // Spotlight / Featured Hero Card
                    if (spotlightItem != null) {
                        item {
                            SpotlightCard(
                                entry = spotlightItem,
                                isLive = liveClass != null,
                                onClick = launchApp
                            )
                            Spacer(GlanceModifier.height(10.dp))
                        }
                    } else if (allCompleted) {
                        item {
                            AllCompletedCard(
                                total = entries.size,
                                onClick = launchApp
                            )
                            Spacer(GlanceModifier.height(10.dp))
                        }
                    }

                    // All Today's Classes (shown in schedule list as well as spotlight hero)
                    if (entries.isNotEmpty()) {
                        item {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(launchApp)
                                    .padding(top = 6.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (allCompleted) "TODAY'S CLASSES" else "SCHEDULE",
                                    style = TextStyle(
                                        color = ColorProvider(Slate),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        items(
                            entries,
                            itemId = { entry -> (entry.start + "_" + entry.courseCode).hashCode().toLong() }
                        ) { entry ->
                            val startM = parseMinutes(entry.start)
                            val endM = parseMinutes(entry.end.ifEmpty { entry.start })
                            val isDone = endM > 0 && nowMinutes > endM
                            val isLive = startM > 0 && endM > 0 && nowMinutes in startM..endM
                            val isNext = entry == nextClass

                            DepartureRow(
                                entry = entry,
                                isDone = isDone,
                                isLive = isLive,
                                isNext = isNext,
                                onClick = launchApp
                            )
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    // ── Exam Spotlight Hero Card (Exam Day Focus) ─────────────────────────────

    @Composable
    private fun ExamSpotlightCard(
        exam: ExamItem,
        isToday: Boolean,
        onClick: androidx.glance.action.Action
    ) {
        val statusText = if (isToday) "EXAM TODAY." else "EXAM TOMORROW."
        val dotColor = Blaze

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(SlabElevated))
                .cornerRadius(8.dp)
                .clickable(onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Bar — 3.5dp wide Blaze bar
            Box(
                modifier = GlanceModifier
                    .width(3.5.dp)
                    .height(56.dp)
                    .background(ColorProvider(Blaze))
                    .cornerRadius(2.dp)
            ) {}

            Spacer(GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                // Top Row: Dot + Status + Exam Type & Session + Room Badge
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .cornerRadius(3.dp)
                            .background(ColorProvider(dotColor))
                    ) {}

                    Spacer(GlanceModifier.width(6.dp))

                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(Blaze),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    Text(
                        text = "${exam.examType} · ${exam.session.uppercase()}",
                        style = TextStyle(
                            color = ColorProvider(Slate),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    if (exam.room.isNotBlank() && exam.room != "Seating Awaited") {
                        Row(
                            modifier = GlanceModifier
                                .background(ColorProvider(Rule))
                                .cornerRadius(4.dp)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exam.room,
                                style = TextStyle(
                                    color = ColorProvider(Chalk),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(GlanceModifier.height(4.dp))

                // Middle Row: Course Code + Time Slot
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exam.courseCode,
                        style = TextStyle(
                            color = ColorProvider(Chalk),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Text(
                        text = exam.timeSlot,
                        style = TextStyle(
                            color = ColorProvider(Blaze),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Course title
                if (exam.courseTitle.isNotBlank()) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = exam.courseTitle,
                        style = TextStyle(
                            color = ColorProvider(Slate),
                            fontSize = 11.sp
                        )
                    )
                }

                // Bottom Row: Reporting Time and/or Seat No
                val seatDisplay = if (exam.seatNo.isNotBlank() && exam.seatNo != "Awaited") "Seat: ${exam.seatNo}" else null
                val reportDisplay = if (exam.reportingTime.isNotBlank()) {
                    if (exam.reportingTime.startsWith("Report", ignoreCase = true)) exam.reportingTime
                    else "Report ${exam.reportingTime}"
                } else null

                if (seatDisplay != null || reportDisplay != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (reportDisplay != null) {
                            Text(
                                text = reportDisplay,
                                style = TextStyle(
                                    color = ColorProvider(PresentGreen),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        if (reportDisplay != null && seatDisplay != null) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = "·",
                                style = TextStyle(
                                    color = ColorProvider(Slate),
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        if (seatDisplay != null) {
                            Text(
                                text = seatDisplay,
                                style = TextStyle(
                                    color = ColorProvider(Chalk),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Exam Preparation Checklist Card ───────────────────────────────────────

    @Composable
    private fun ExamPreparationCard(
        exam: ExamItem,
        onClick: androidx.glance.action.Action
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Slab))
                .cornerRadius(8.dp)
                .clickable(onClick)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXAM PROTOCOL",
                    style = TextStyle(
                        color = ColorProvider(Slate),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Text(
                    text = "DATESHEET →",
                    style = TextStyle(
                        color = ColorProvider(Blaze),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = "Carry Student ID Card & Physical Admit Card.",
                style = TextStyle(
                    color = ColorProvider(Chalk),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(GlanceModifier.height(3.dp))

            val locationText = if (exam.room.isNotBlank() && exam.room != "Seating Awaited") {
                "Report to Room ${exam.room} 15 mins before time."
            } else {
                "Check desk seating & room in UMS before leaving."
            }

            Text(
                text = locationText,
                style = TextStyle(
                    color = ColorProvider(TextDim),
                    fontSize = 10.sp
                )
            )
        }
    }

    // ── Spotlight Hero Card (Live or Next Class) ──────────────────────────────

    @Composable
    private fun SpotlightCard(
        entry: ClassData,
        isLive: Boolean,
        onClick: androidx.glance.action.Action
    ) {
        val typeTag = when (entry.type) {
            "Lecture"   -> "LEC"
            "Practical" -> "PRAC"
            "Tutorial"  -> "TUT"
            else        -> entry.type.uppercase().take(3)
        }

        val dotColor = if (isLive) Blaze else Slate
        val barColor = if (isLive) Blaze else Blaze.copy(alpha = 0.6f)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(SlabElevated))
                .cornerRadius(8.dp)
                .clickable(onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Bar — 3dp wide
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(44.dp)
                    .background(ColorProvider(barColor))
                    .cornerRadius(1.5.dp)
            ) {}

            Spacer(GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                // Top row: Dot + Status Tag + Type + Room
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Dot in Spotlight
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .cornerRadius(3.dp)
                            .background(ColorProvider(dotColor))
                    ) {}

                    Spacer(GlanceModifier.width(6.dp))

                    Text(
                        text = if (isLive) "NOW." else "NEXT.",
                        style = TextStyle(
                            color = ColorProvider(if (isLive) Blaze else Slate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    Text(
                        text = typeTag,
                        style = TextStyle(
                            color = ColorProvider(Slate),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    if (entry.room.isNotBlank()) {
                        Text(
                            text = entry.room,
                            style = TextStyle(
                                color = ColorProvider(Chalk),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(GlanceModifier.height(4.dp))

                // Middle row: Course Code + Time Range
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.courseCode,
                        style = TextStyle(
                            color = ColorProvider(Chalk),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Text(
                        text = entry.timeRange.ifEmpty { "${entry.start} – ${entry.end}" },
                        style = TextStyle(
                            color = ColorProvider(if (isLive) Blaze else Chalk),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Bottom row: Teacher + Section
                if (entry.teacher.isNotBlank()) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = "${entry.teacher}${if (entry.section.isNotBlank()) " · Sec ${entry.section}" else ""}",
                        style = TextStyle(
                            color = ColorProvider(Slate),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }

    // ── All Completed ────────────────────────────────────────────────────────

    @Composable
    private fun AllCompletedCard(total: Int, onClick: androidx.glance.action.Action) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Slab))
                .cornerRadius(8.dp)
                .clickable(onClick)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "All done.",
                style = TextStyle(
                    color = ColorProvider(Chalk),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = "$total classes completed.",
                style = TextStyle(
                    color = ColorProvider(Slate),
                    fontSize = 12.sp
                )
            )
        }
    }

    // ── Sunday Relax Card (Centered Bitmoji & Relax greeting) ─────────────────

    @Composable
    private fun SundayRelaxCard(
        modifier: GlanceModifier = GlanceModifier,
        onClick: androidx.glance.action.Action
    ) {
        val widgetHeight = LocalSize.current.height
        val bitmojiSize = when {
            widgetHeight > 280.dp -> 150.dp
            widgetHeight > 200.dp -> 125.dp
            else -> 100.dp
        }
        val textSize = when {
            widgetHeight > 280.dp -> 19.sp
            widgetHeight > 200.dp -> 17.sp
            else -> 15.sp
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(ColorProvider(SlabElevated))
                .cornerRadius(12.dp)
                .clickable(onClick)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Centered User Bitmoji
            Image(
                provider = ImageProvider(R.drawable.sunday_bitmoji),
                contentDescription = "Sunday Bitmoji",
                modifier = GlanceModifier.size(bitmojiSize)
            )

            Spacer(GlanceModifier.height(10.dp))

            Text(
                text = "Enjoy your Sunday.",
                style = TextStyle(
                    color = ColorProvider(Chalk),
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    // ── Clean Departure Row (Airy, single-line tabular layout) ────────────────

    @Composable
    private fun DepartureRow(
        entry: ClassData,
        isDone: Boolean,
        isLive: Boolean,
        isNext: Boolean = false,
        onClick: androidx.glance.action.Action
    ) {
        val typeTag = when (entry.type) {
            "Lecture"   -> "LEC"
            "Practical" -> "PRAC"
            "Tutorial"  -> "TUT"
            else        -> entry.type.uppercase().take(3)
        }

        val dotColor = when {
            isLive -> Blaze
            isNext -> UpcomingOrange
            isDone -> Color(0xFF333333)
            else   -> Slate
        }

        val rowBg = if (isDone) Color(0xFF0F0F0F) else Slab
        val textColor = if (isDone) TextDim else Chalk
        val timeColor = if (isDone) TextDim else Slate
        val typeColor = if (isDone) TextDim else Slate

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(rowBg))
                .cornerRadius(8.dp)
                .clickable(onClick)
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live/Next/Done indicator dot
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .cornerRadius(3.dp)
                    .background(ColorProvider(dotColor))
            ) {}

            Spacer(GlanceModifier.width(8.dp))

            // Time Column
            Text(
                text = entry.start,
                style = TextStyle(
                    color = ColorProvider(timeColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.width(44.dp)
            )

            // Course Code
            Text(
                text = entry.courseCode,
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(GlanceModifier.width(6.dp))

            // Type
            Text(
                text = typeTag,
                style = TextStyle(
                    color = ColorProvider(typeColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Room Pill Badge
            if (entry.room.isNotBlank()) {
                Row(
                    modifier = GlanceModifier
                        .background(ColorProvider(if (isDone) Color(0xFF141414) else SlabElevated))
                        .cornerRadius(4.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.room,
                        style = TextStyle(
                            color = ColorProvider(if (isDone) TextDim else Chalk),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }

    // ── Data & Helpers ────────────────────────────────────────────────────────

    data class ClassData(
        val courseCode: String,
        val start: String,
        val end: String,
        val room: String,
        val type: String,
        val timeRange: String,
        val teacher: String = "",
        val section: String = ""
    )

    private fun parseMinutes(timeStr: String): Int {
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return -1
            val m = parts[1].take(2).toIntOrNull() ?: return -1
            return h * 60 + m
        }
        return -1
    }

    private fun parseEntries(context: Context, raw: String?): List<ClassData> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val code = obj.optString("courseCode")
                val start = obj.optString("start")
                ClassData(
                    courseCode = code,
                    start      = start,
                    end        = obj.optString("end"),
                    room       = obj.optString("room"),
                    type       = obj.optString("type", "Lecture"),
                    timeRange  = obj.optString("timeRange"),
                    teacher    = obj.optString("teacher"),
                    section    = obj.optString("section")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
