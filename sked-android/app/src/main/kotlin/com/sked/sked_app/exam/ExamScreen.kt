package com.sked.sked_app.exam

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sked.sked_app.*

@Composable
fun ExamScreen(
    exams: List<ExamItem>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val upcomingExams = remember(exams) { exams.filter { it.getStatus() != ExamStatus.COMPLETED } }
    val completedExams = remember(exams) { exams.filter { it.getStatus() == ExamStatus.COMPLETED } }
    val nextExam = upcomingExams.minByOrNull { it.daysUntil() }

    val filteredList = when (selectedFilter) {
        "UPCOMING" -> upcomingExams
        "COMPLETED" -> completedExams
        else -> exams
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        if (exams.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Blaze.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = Blaze, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO DATESHEET RELEASED YET",
                            fontSize = 18.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The official Examination Branch has not published the datesheet on UMS for this semester yet.\n\nAs soon as LPU announces your examination schedule or seating plan, tap Re-Sync to fetch it directly into Sked.",
                            fontSize = 12.sp,
                            color = Slate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = Ink, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CHECK / RE-SYNC FROM UMS",
                                fontSize = 12.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        } else {
            // ── Next Exam Hero Countdown Banner ──────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (nextExam != null) Blaze.copy(alpha = 0.5f) else Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Blaze)
                                )
                                Text(
                                    text = "EXAM RADAR",
                                    fontSize = 11.sp,
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    color = Blaze,
                                    letterSpacing = 1.sp
                                )
                            }

                            if (nextExam != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Rule)
                                    ) {
                                        Text(
                                            text = nextExam.displayExamType(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Blaze.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Blaze)
                                    ) {
                                        Text(
                                            text = nextExam.statusLabel(),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Blaze,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (nextExam != null) {
                            Text(
                                text = nextExam.courseCode,
                                fontSize = 26.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Chalk
                            )
                            val cleanHeroTitle = nextExam.cleanSubjectTitle()
                            if (cleanHeroTitle.isNotEmpty()) {
                                Text(
                                    text = cleanHeroTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Chalk.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${nextExam.formattedDate()} • ${nextExam.timeSlot}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Chalk
                                    )
                                }
                            }

                            val heroVenue = if (nextExam.room.isNotBlank()) nextExam.room else "Seating Awaited"
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Slate, modifier = Modifier.size(14.dp))
                                val seatText = if (nextExam.seatNo.isNotEmpty() && nextExam.seatNo != "Awaited") " · ${nextExam.seatNo}" else ""
                                Text(
                                    text = "$heroVenue$seatText",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Slate
                                )
                            }
                        }
                    }
                }
            }

            // ── Filter Chips Row ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterTabChip("ALL", "ALL (${exams.size})", selectedFilter == "ALL") { selectedFilter = "ALL" }
                    FilterTabChip("UPCOMING", "UPCOMING (${upcomingExams.size})", selectedFilter == "UPCOMING") { selectedFilter = "UPCOMING" }
                    FilterTabChip("COMPLETED", "OVER (${completedExams.size})", selectedFilter == "COMPLETED") { selectedFilter = "COMPLETED" }
                }
            }

        // ── Exam Items ───────────────────────────────────────────────────────
        if (filteredList.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = Slate, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No exams in this filter",
                            fontSize = 14.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switch filter or re-sync from UMS datesheet",
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }
                }
            }
        } else {
            itemsIndexed(filteredList) { index, exam ->
                ExamCard(exam = exam)
            }
        }

        // ── Footer Re-Sync Button ────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRefresh() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Blaze, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RE-SYNC DATESHEET FROM UMS",
                        fontSize = 12.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Chalk,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "100% Offline • Synchronized with official LPU Examination datesheet",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Slate,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        }
    }
}

@Composable
private fun FilterTabChip(
    key: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) Blaze else Slab,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Blaze else Rule),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Ink else Slate,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ExamCard(exam: ExamItem) {
    val status = exam.getStatus()
    val isToday = status == ExamStatus.TODAY
    val isTomorrow = status == ExamStatus.TOMORROW
    val isUpcoming = status == ExamStatus.UPCOMING

    val accentColor = when (status) {
        ExamStatus.TODAY -> Color(0xFF10B981)   // Emerald Green
        ExamStatus.TOMORROW -> Color(0xFFFF8533) // Warm Orange
        ExamStatus.UPCOMING -> Color(0xFF818CF8) // Indigo
        ExamStatus.COMPLETED -> Color(0xFF71717A)// Grey
    }

    val verticalBarColor = when (status) {
        ExamStatus.TODAY -> Color(0xFF10B981)
        ExamStatus.TOMORROW -> Color(0xFFFF8533)
        ExamStatus.UPCOMING -> Color(0xFF818CF8)
        ExamStatus.COMPLETED -> Color(0xFF383838)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slab,
        border = androidx.compose.foundation.BorderStroke(
            if (isToday) 1.5.dp else 1.dp,
            if (isToday) accentColor.copy(alpha = 0.5f) else Rule
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(verticalBarColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Top row: Course Code + Tag Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exam.courseCode,
                        fontFamily = BarlowCondensed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Chalk
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Exam Type tag (ETE / MTE / ETP)
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rule)
                        ) {
                            Text(
                                text = exam.displayExamType(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Countdown Status tag
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)
                        ) {
                            Text(
                                text = exam.statusLabel(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Course title / Subject name
                val cleanCardTitle = exam.cleanSubjectTitle()
                if (cleanCardTitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanCardTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Chalk.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Date & Time Slot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exam.formattedDate(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isToday) accentColor else Chalk
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "·", color = Slate)
                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = exam.timeSlot,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Slate
                    )
                }

                // Venue & Seating
                val cardVenue = if (exam.room.isNotBlank()) exam.room else "Seating Awaited"
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cardVenue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Slate
                    )

                    if (exam.seatNo.isNotEmpty() && exam.seatNo != "Awaited") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "·", color = Slate)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = exam.seatNo,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Blaze
                        )
                    }

                    if (exam.reportingTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "·", color = Slate)
                        Spacer(modifier = Modifier.width(6.dp))
                        val rep = if (exam.reportingTime.startsWith("Report", ignoreCase = true)) exam.reportingTime else "Report ${exam.reportingTime}"
                        Text(
                            text = rep,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }
                }
            }
        }
    }
}
