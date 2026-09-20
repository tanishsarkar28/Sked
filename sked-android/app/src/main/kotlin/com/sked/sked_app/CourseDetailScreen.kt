package com.sked.sked_app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val PresentGreen = Color(0xFF22C55E)
private val AbsentRed = Color(0xFFEF4444)
private val DutyLeaveBlue = Color(0xFF38BDF8)
private val TrackGrey = Color(0xFF1F1F1F)

enum class HistoryFilter {
    ALL,
    PRESENT,
    ABSENT,
    DUTY_LEAVE
}

@Composable
fun AttendanceCircleGauge(
    percentage: Double,
    delivered: Int = 1,
    modifier: Modifier = Modifier,
    size: Dp = 175.dp,
    strokeWidth: Dp = 13.dp
) {
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(percentage, delivered) {
        animationStarted = true
    }

    val isZeroDelivered = delivered == 0
    val isCritical = !isZeroDelivered && percentage < 75.0
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationStarted) {
            if (isZeroDelivered) 0f else (percentage / 100.0).toFloat().coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "gaugeProgress"
    )

    val gaugeColor = if (isZeroDelivered) Slate else if (percentage >= 75.0) PresentGreen else AbsentRed
    val trackColor = if (isCritical) Color(0x33EF4444) else TrackGrey

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokeWidthPx = strokeWidth.toPx()

            // Background Track Circle
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Dynamic Progress Sweep (starts at top -90°)
            if (animatedProgress > 0f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Center Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isZeroDelivered) "100%" else "${percentage.roundToInt()}%",
                fontSize = 44.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                color = if (isCritical) AbsentRed else Chalk,
                lineHeight = 44.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ATTENDANCE",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Slate,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = gaugeColor.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, gaugeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = if (isZeroDelivered) "NO SESSIONS" else if (percentage >= 75.0) "ELIGIBLE" else "CRITICAL",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseCode: String,
    courseName: String = "",
    teacher: String = "",
    room: String = "",
    section: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var courseAttendance by remember(courseCode) {
        mutableStateOf(AttendanceManager.getCourseAttendance(context, courseCode))
    }
    var historyList by remember(courseCode) {
        mutableStateOf(AttendanceManager.getCourseHistory(context, courseCode))
    }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshHistory() {
        isRefreshing = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                try {
                    AttendanceManager.refreshFromMobileApi(context)
                } catch (_: Exception) {}
                val updated = AttendanceManager.fetchSingleCourseHistory(context, courseCode)
                val updatedAtt = AttendanceManager.getCourseAttendance(context, courseCode)
                withContext(Dispatchers.Main) {
                    historyList = updated
                    if (updatedAtt != null) courseAttendance = updatedAtt
                    isRefreshing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(courseCode) {
        // Automatically sync fresh history if available
        refreshHistory()
    }

    BackHandler(onBack = onBack)

    val histAttended = remember(historyList) { historyList.count { it.status == AttendanceStatus.PRESENT } }
    val histAbsent = remember(historyList) { historyList.count { it.status == AttendanceStatus.ABSENT } }
    val histDutyLeave = remember(historyList) { historyList.count { it.status == AttendanceStatus.DUTY_LEAVE } }
    val histDelivered = remember(historyList) { historyList.size }

    val hasOfficial = courseAttendance != null
    val hasOfficialDelivered = (courseAttendance?.delivered ?: 0) > 0
    val delivered = when {
        hasOfficialDelivered -> courseAttendance!!.delivered
        hasOfficial -> 0
        else -> histDelivered
    }
    val attended = when {
        hasOfficialDelivered -> courseAttendance!!.attended
        hasOfficial -> 0
        else -> histAttended
    }
    val dutyLeave = when {
        hasOfficialDelivered -> courseAttendance!!.dutyLeave
        hasOfficial -> 0
        else -> histDutyLeave
    }
    val percentage = when {
        hasOfficialDelivered -> courseAttendance!!.percentage
        hasOfficial -> 100.0
        delivered > 0 -> ((attended + dutyLeave).toDouble() / delivered * 100.0)
        else -> 100.0
    }


    val presentCount = remember(historyList) { historyList.count { it.status == AttendanceStatus.PRESENT } }
    val absentCount = remember(historyList) { historyList.count { it.status == AttendanceStatus.ABSENT } }
    val dutyLeaveCount = remember(historyList) { historyList.count { it.status == AttendanceStatus.DUTY_LEAVE } }

    val filteredList = remember(historyList, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> historyList
            HistoryFilter.PRESENT -> historyList.filter { it.status == AttendanceStatus.PRESENT }
            HistoryFilter.ABSENT -> historyList.filter { it.status == AttendanceStatus.ABSENT }
            HistoryFilter.DUTY_LEAVE -> historyList.filter { it.status == AttendanceStatus.DUTY_LEAVE }
        }
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${courseCode.uppercase()}.",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Blaze,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Chalk
                        )
                    }
                },
                actions = {
                    val refreshRotation by rememberInfiniteTransition(label = "detailRefresh").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "detailRot"
                    )

                    IconButton(onClick = { refreshHistory() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Attendance",
                            tint = if (isRefreshing) Blaze else Slate,
                            modifier = if (isRefreshing) Modifier.rotate(refreshRotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Course Meta Card
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        val displayName = (courseAttendance?.courseName ?: courseName).ifEmpty { courseCode }
                        Text(
                            text = displayName,
                            fontSize = 16.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (room.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "ROOM: ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate
                                    )
                                    Text(
                                        text = room,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Chalk
                                    )
                                }
                            }
                            if (section.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SEC: ",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate
                                    )
                                    Text(
                                        text = section,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Chalk
                                    )
                                }
                            }
                        }

                        if (teacher.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Faculty: $teacher",
                                fontSize = 12.sp,
                                color = Slate
                            )
                        }
                    }
                }
            }

            // Circular Gauge & Metrics Card
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AttendanceCircleGauge(percentage = percentage, delivered = delivered)

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = Rule, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // 3-Column Stat Matrix
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "ATTENDED",
                                value = "$attended",
                                valueColor = PresentGreen,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "DELIVERED",
                                value = "$delivered",
                                valueColor = Chalk,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "DUTY LV",
                                value = "$dutyLeave",
                                valueColor = DutyLeaveBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }


            // History Header & Filter Chips
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ATTENDANCE LOG.",
                            fontSize = 16.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk
                        )
                        Text(
                            text = "${historyList.size} SESSIONS",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Slate
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                label = "ALL (${historyList.size})",
                                isSelected = selectedFilter == HistoryFilter.ALL,
                                onClick = { selectedFilter = HistoryFilter.ALL }
                            )
                        }
                        item {
                            FilterChip(
                                label = "PRESENT ($presentCount)",
                                isSelected = selectedFilter == HistoryFilter.PRESENT,
                                activeColor = PresentGreen,
                                onClick = { selectedFilter = HistoryFilter.PRESENT }
                            )
                        }
                        item {
                            FilterChip(
                                label = "ABSENT ($absentCount)",
                                isSelected = selectedFilter == HistoryFilter.ABSENT,
                                activeColor = AbsentRed,
                                onClick = { selectedFilter = HistoryFilter.ABSENT }
                            )
                        }
                        if (dutyLeaveCount > 0) {
                            item {
                                FilterChip(
                                    label = "DUTY LV ($dutyLeaveCount)",
                                    isSelected = selectedFilter == HistoryFilter.DUTY_LEAVE,
                                    activeColor = DutyLeaveBlue,
                                    onClick = { selectedFilter = HistoryFilter.DUTY_LEAVE }
                                )
                            }
                        }
                    }
                }
            }

            // Session List Items
            if (filteredList.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slab,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No records found.",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Chalk
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (historyList.isEmpty()) "No session history recorded yet for this course."
                                else "No sessions matching the selected filter.",
                                fontSize = 12.sp,
                                color = Slate,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredList.size, key = { index -> "${filteredList[index].dateKey}_${filteredList[index].timeSlot}_$index" }) { index ->
                    HistoryRecordCard(record = filteredList[index])
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Slate
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    activeColor: Color = Blaze,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.14f) else Slab,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) activeColor else Rule
        )
    ) {
        Text(
            text = label,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (isSelected) activeColor else Slate,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

fun formatDisplayTime(slot: String): String {
    val clean = slot.trim()
    if (clean.matches(Regex("""^\d{1,2}:\d{2}$"""))) {
        val parts = clean.split(":")
        var h = parts[0].toIntOrNull() ?: 0
        val m = parts[1]
        val ampm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        return String.format(Locale.US, "%02d:%s %s", h, m, ampm)
    }
    return clean
}

@Composable
fun HistoryRecordCard(record: AttendanceHistoryItem) {
    val statusColor = when (record.status) {
        AttendanceStatus.PRESENT -> PresentGreen
        AttendanceStatus.ABSENT -> AbsentRed
        AttendanceStatus.DUTY_LEAVE -> DutyLeaveBlue
        AttendanceStatus.UNMARKED -> Slate
    }

    val statusText = when (record.status) {
        AttendanceStatus.PRESENT -> "PRESENT"
        AttendanceStatus.ABSENT -> "ABSENT"
        AttendanceStatus.DUTY_LEAVE -> "DUTY LV"
        AttendanceStatus.UNMARKED -> "UNMARKED"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slab,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical accent stripe
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Date & Time Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.formattedDate,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Chalk
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.dayOfWeek.isNotBlank()) {
                        Text(
                            text = record.dayOfWeek,
                            fontSize = 12.sp,
                            color = Slate
                        )
                        Text(
                            text = " · ",
                            fontSize = 12.sp,
                            color = Rule
                        )
                    }
                    Text(
                        text = formatDisplayTime(record.timeSlot),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Slate
                    )
                }
            }

            // Right Status Badge
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = statusColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
