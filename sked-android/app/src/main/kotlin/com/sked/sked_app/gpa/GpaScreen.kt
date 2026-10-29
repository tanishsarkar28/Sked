package com.sked.sked_app.gpa

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sked.sked_app.*

enum class GpaScreenMode {
    RESULT,
    CALCULATOR
}

@Composable
fun GpaScreen(
    timetableEntries: List<ClassItem>,
    onSyncUms: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    mode: GpaScreenMode = GpaScreenMode.RESULT
) {
    val context = LocalContext.current
    var gpaState by remember {
        mutableStateOf(GpaStorage.load(context, timetableEntries))
    }

    DisposableEffect(timetableEntries) {
        val prefs = context.getSharedPreferences("sked_gpa_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "saved_gpa_terms_v2" || key == "saved_gpa_official_cgpa") {
                gpaState = GpaStorage.load(context, timetableEntries)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var globalModeMarks by remember { mutableStateOf(false) }
    var showAddTermDialog by remember { mutableStateOf(false) }
    var showAddCustomSubjectDialog by remember { mutableStateOf(false) }
    var showTargetSimulator by remember { mutableStateOf(false) }
    var selectedSemesterDetail by remember { mutableStateOf<TermData?>(null) }
    var termToDelete by remember { mutableStateOf<TermData?>(null) }

    fun updateState(newState: GpaState) {
        gpaState = newState
        GpaStorage.save(context, newState)
    }

    val activeTerm = gpaState.activeTerm
    val pastSemesters = gpaState.terms
        .filter { !it.isCurrent }
        .sortedBy { it.semesterNumber ?: 99 }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (mode == GpaScreenMode.RESULT) {
                // ── TOP RESULT TITLE & SYNC BAR ─────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Result",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Chalk
                        )

                        if (onSyncUms != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Slab,
                                border = BorderStroke(1.dp, Rule),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onSyncUms() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Blaze,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "SYNC UMS",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Blaze
                                    )
                                }
                            }
                        }
                    }
                }

                // ── OVERALL PERFORMANCE HERO CARD ───────────────────────────────
                item {
                    OverallPerformanceHeroCard(
                        cgpa = gpaState.displayCgpa ?: 8.31,
                        onToggleSimulator = { showTargetSimulator = !showTargetSimulator },
                        isSimulatorActive = showTargetSimulator
                    )
                }

                // ── TARGET CGPA SIMULATOR (EXPANDABLE) ──────────────────────────
                if (showTargetSimulator) {
                    item {
                        TargetSimulatorCard(
                            state = gpaState,
                            onSetTarget = { target ->
                                updateState(gpaState.copy(targetCgpa = target))
                            }
                        )
                    }
                }

                // ── 2-COLUMN SEMESTER CARDS GRID ────────────────────────────────
                if (pastSemesters.isNotEmpty()) {
                    items(pastSemesters.chunked(2)) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { term ->
                                SemesterResultCard(
                                    term = term,
                                    onClick = { selectedSemesterDetail = term },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // ── GPA CALCULATOR MODE ─────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPA Calculator",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Chalk
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (showTargetSimulator) Blaze.copy(alpha = 0.2f) else Slab,
                            border = BorderStroke(1.dp, if (showTargetSimulator) Blaze else Rule),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showTargetSimulator = !showTargetSimulator }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.TrackChanges,
                                    contentDescription = null,
                                    tint = Blaze,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showTargetSimulator) "CLOSE TARGET" else "TARGET GOAL",
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Blaze
                                )
                            }
                        }
                    }
                }

                // ── TARGET CGPA SIMULATOR (EXPANDABLE) ──────────────────────────
                if (showTargetSimulator) {
                    item {
                        TargetSimulatorCard(
                            state = gpaState,
                            onSetTarget = { target ->
                                updateState(gpaState.copy(targetCgpa = target))
                            }
                        )
                    }
                }

                // ── TERM TABS STRIP ─────────────────────────────────────────────
                item {
                    TermTabsStrip(
                        terms = gpaState.terms,
                        activeTermId = gpaState.activeTermId,
                        onSelectTerm = { termId ->
                            updateState(gpaState.copy(activeTermId = termId))
                        },
                        onAddTerm = { showAddTermDialog = true },
                        onDeleteTerm = { term ->
                            if (term.isDeletable) {
                                termToDelete = term
                            }
                        }
                    )
                }

                // ── ACTIVE TERM HEADER & METRICS ────────────────────────────────
                if (activeTerm != null) {
                    item {
                        ActiveTermHeaderCard(
                            term = activeTerm,
                        isMarksMode = globalModeMarks,
                        onToggleMode = {
                            globalModeMarks = it
                            val updatedSubjects = activeTerm.subjects.map { s -> s.copy(isMarksMode = it) }
                            val updatedTerms = gpaState.terms.map {
                                if (it.id == activeTerm.id) it.copy(subjects = updatedSubjects) else it
                            }
                            updateState(gpaState.copy(terms = updatedTerms))
                        },
                        onDeleteTerm = if (activeTerm.isDeletable) {
                            {
                                termToDelete = activeTerm
                            }
                        } else null
                    )
                }

                // ── SUBJECTS HEADER ─────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeTerm.name.uppercase()} COURSES (${activeTerm.subjects.size})",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showAddCustomSubjectDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Blaze,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ADD COURSE",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Blaze
                            )
                        }
                    }
                }

                // ── SUBJECT CARDS ───────────────────────────────────────────
                if (activeTerm.subjects.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Slab,
                            border = BorderStroke(1.dp, Rule),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No subjects added for ${activeTerm.name}",
                                    fontFamily = BarlowCondensed,
                                    fontSize = 14.sp,
                                    color = Slate
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { showAddCustomSubjectDialog = true },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Blaze)
                                ) {
                                    Text("ADD COURSE", fontFamily = BarlowCondensed, color = Blaze)
                                }
                            }
                        }
                    }
                } else {
                    items(activeTerm.subjects, key = { it.courseCode + if (it.isCustom) "_custom" else "" }) { subject ->
                        GpaSubjectCard(
                            subject = subject,
                            isGlobalMarksMode = globalModeMarks,
                            onUpdateSubject = { updatedSub ->
                                val updatedSubs = activeTerm.subjects.map {
                                    if (it.courseCode == subject.courseCode && it.isCustom == subject.isCustom) updatedSub else it
                                }
                                val updatedTerms = gpaState.terms.map {
                                    if (it.id == activeTerm.id) it.copy(subjects = updatedSubs) else it
                                }
                                updateState(gpaState.copy(terms = updatedTerms))
                            },
                            onDeleteSubject = {
                                val remainingSubs = activeTerm.subjects.filterNot {
                                    it.courseCode == subject.courseCode && it.isCustom == subject.isCustom
                                }
                                val updatedTerms = gpaState.terms.map {
                                    if (it.id == activeTerm.id) it.copy(subjects = remainingSubs) else it
                                }
                                updateState(gpaState.copy(terms = updatedTerms))
                            }
                        )
                    }
                }
            }
        }
    }
}

    // ── ADD TERM DIALOG ─────────────────────────────────────────────────────
    if (showAddTermDialog) {
        AddTermDialog(
            existingCount = gpaState.terms.size,
            onDismiss = { showAddTermDialog = false },
            onAdd = { newTerm ->
                val updated = gpaState.terms + newTerm
                updateState(gpaState.copy(terms = updated, activeTermId = newTerm.id))
                showAddTermDialog = false
            }
        )
    }

    // ── DELETE CONFIRMATION DIALOG ──────────────────────────────────────────
    if (termToDelete != null) {
        val target = termToDelete!!
        AlertDialog(
            onDismissRequest = { termToDelete = null },
            containerColor = Slab,
            title = {
                Text(
                    text = "Delete ${target.name}?",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Chalk
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this custom semester? All courses and grades in this term will be permanently removed.",
                    fontFamily = BarlowCondensed,
                    fontSize = 14.sp,
                    color = Slate
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = target.id
                        termToDelete = null
                        val remaining = gpaState.terms.filterNot { it.id == targetId }
                        val nextActive = remaining.firstOrNull { it.isCurrent }?.id ?: remaining.firstOrNull()?.id ?: ""
                        updateState(gpaState.copy(terms = remaining, activeTermId = nextActive))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { termToDelete = null }) {
                    Text("CANCEL", fontFamily = BarlowCondensed, color = Slate)
                }
            }
        )
    }

    // ── ADD CUSTOM SUBJECT DIALOG ───────────────────────────────────────────
    if (showAddCustomSubjectDialog && activeTerm != null) {
        AddCustomSubjectDialog(
            onDismiss = { showAddCustomSubjectDialog = false },
            onAdd = { newSubject ->
                val exists = activeTerm.subjects.any { it.courseCode.equals(newSubject.courseCode, ignoreCase = true) }
                if (exists) {
                    Toast.makeText(context, "Course already in list", Toast.LENGTH_SHORT).show()
                } else {
                    val updatedSubs = activeTerm.subjects + newSubject
                    val updatedTerms = gpaState.terms.map {
                        if (it.id == activeTerm.id) it.copy(subjects = updatedSubs) else it
                    }
                    updateState(gpaState.copy(terms = updatedTerms))
                    showAddCustomSubjectDialog = false
                }
            }
        )
    }

    // ── SEMESTER DETAIL SHEET ───────────────────────────────────────────────
    if (selectedSemesterDetail != null) {
        SemesterDetailSheet(
            term = selectedSemesterDetail!!,
            onDismiss = { selectedSemesterDetail = null },
            onAddCourse = {
                val term = selectedSemesterDetail!!
                updateState(gpaState.copy(activeTermId = term.id))
                selectedSemesterDetail = null
                showAddCustomSubjectDialog = true
            }
        )
    }
}

// ── CIRCULAR GPA GAUGE (RING WITH GREEN PROGRESS ARC) ───────────────────────

@Composable
fun CircularGpaGauge(
    score: Double,
    maxScore: Double = 10.0,
    size: androidx.compose.ui.unit.Dp = 100.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.4f),
    progressColor: Color = Color(0xFF10B981), // Emerald green
    textColor: Color = Color(0xFF18181B),
    textSize: androidx.compose.ui.unit.TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    val progress = (score / maxScore).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gauge_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.toPx() - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(diameter, diameter)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Text(
            text = String.format(Locale.US, "%.2f", score),
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            fontFamily = BarlowCondensed,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

// ── OVERALL PERFORMANCE HERO CARD (WARM GRADIENT WITH CIRCLE) ───────────────

@Composable
fun OverallPerformanceHeroCard(
    cgpa: Double,
    onToggleSimulator: () -> Unit,
    isSimulatorActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF7043), // Vibrant coral/orange
                            Color(0xFFFF9E45), // Warm amber
                            Color(0xFFFFB74D)  // Golden yellow
                        )
                    )
                )
                .padding(top = 12.dp, bottom = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = if (isSimulatorActive) 0.35f else 0.2f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleSimulator() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.TrackChanges,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSimulatorActive) "CLOSE GOAL" else "TARGET GOAL",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Circular CGPA Ring
                CircularGpaGauge(
                    score = cgpa,
                    size = 114.dp,
                    strokeWidth = 9.dp,
                    trackColor = Color.White.copy(alpha = 0.5f),
                    progressColor = Color(0xFF10B981),
                    textColor = Color(0xFF18181B),
                    textSize = 34.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Subtitle
                Text(
                    text = "Overall Performance",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF263238),
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

// ── SEMESTER RESULT CARD (2-COLUMN GRID ITEM) ───────────────────────────────

@Composable
fun SemesterResultCard(
    term: TermData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Slab,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Rule),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dark Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202024))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = term.displayRomanName,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Chalk,
                    textAlign = TextAlign.Center
                )
            }

            // Dark Card Body with Green Circular Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularGpaGauge(
                    score = term.officialTgpa ?: term.tgpa ?: 0.0,
                    size = 76.dp,
                    strokeWidth = 7.dp,
                    trackColor = Color(0xFF27272A),
                    progressColor = Color(0xFF10B981),
                    textColor = Color.White,
                    textSize = 19.sp
                )
            }
        }
    }
}

// ── SEMESTER SUBJECT DETAIL SHEET ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterDetailSheet(
    term: TermData,
    onDismiss: () -> Unit,
    onAddCourse: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slab,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Rule) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = term.displayRomanName.uppercase(),
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Chalk,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Verified Academic Performance Breakdown",
                        fontFamily = BarlowCondensed,
                        fontSize = 12.sp,
                        color = Slate
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text(
                        text = if (term.tgpa != null) String.format(Locale.US, "%.2f TGPA", term.tgpa) else "—",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = SlabElevated,
                    border = BorderStroke(1.dp, Rule)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (term.totalCredits > 0.0 && term.manualTgpa == null) "TOTAL CREDITS" else "ACADEMIC STATUS",
                            fontFamily = BarlowCondensed,
                            fontSize = 11.sp,
                            color = Slate
                        )
                        Text(
                            text = if (term.totalCredits > 0.0 && term.manualTgpa == null) {
                                String.format(Locale.US, "%.1f Cr", term.totalCredits)
                            } else {
                                "Verified Grade"
                            },
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (term.totalCredits > 0.0 && term.manualTgpa == null) Chalk else Color(0xFF10B981)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = SlabElevated,
                    border = BorderStroke(1.dp, Rule)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("TOTAL COURSES", fontFamily = BarlowCondensed, fontSize = 11.sp, color = Slate)
                        Text(
                            text = "${term.subjects.size} Courses",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Chalk
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (term.subjects.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlabElevated,
                    border = BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Official TGPA: ${String.format(Locale.US, "%.2f", term.tgpa ?: 0.0)}",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Chalk
                        )
                        Text(
                            text = "Course grades declared by University.",
                            fontFamily = BarlowCondensed,
                            fontSize = 13.sp,
                            color = Slate,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onAddCourse,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Blaze)
                        ) {
                            Text("ADD COURSES", fontFamily = BarlowCondensed, color = Blaze)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(term.subjects) { subject ->
                        SemesterSubjectRow(subject = subject)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── SEMESTER SUBJECT ROW ────────────────────────────────────────────────────

@Composable
fun SemesterSubjectRow(subject: GpaSubject) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SlabElevated,
        border = BorderStroke(1.dp, Rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subject.courseCode,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Chalk
                    )
                    if (subject.credits > 0.0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Slab,
                            border = BorderStroke(1.dp, Rule)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", subject.credits)} Cr",
                                fontFamily = BarlowCondensed,
                                fontSize = 11.sp,
                                color = Slate,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (subject.courseTitle.isNotBlank()) {
                    Text(
                        text = subject.courseTitle,
                        fontFamily = BarlowCondensed,
                        fontSize = 12.sp,
                        color = Slate,
                        maxLines = 1
                    )
                }
            }

            val grade = subject.effectiveGrade
            if (grade != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when(grade) {
                        LpuGrade.O, LpuGrade.A_PLUS -> Color(0xFF10B981).copy(alpha = 0.2f)
                        LpuGrade.A, LpuGrade.B_PLUS -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                        LpuGrade.B, LpuGrade.C -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                        else -> Color(0xFFEF4444).copy(alpha = 0.2f)
                    },
                    border = BorderStroke(1.dp, when(grade) {
                        LpuGrade.O, LpuGrade.A_PLUS -> Color(0xFF10B981)
                        LpuGrade.A, LpuGrade.B_PLUS -> Color(0xFF3B82F6)
                        LpuGrade.B, LpuGrade.C -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    })
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = grade.letter,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Chalk
                        )
                    }
                }
            } else {
                Text(
                    text = "Declared",
                    fontFamily = BarlowCondensed,
                    fontSize = 12.sp,
                    color = Slate
                )
            }
        }
    }
}

// ── TERM TABS HORIZONTAL STRIP ──────────────────────────────────────────────

@Composable
fun TermTabsStrip(
    terms: List<TermData>,
    activeTermId: String,
    onSelectTerm: (String) -> Unit,
    onAddTerm: () -> Unit,
    onDeleteTerm: ((TermData) -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        terms.forEach { term ->
            val isSelected = term.id == activeTermId
            val tgpaStr = if (term.tgpa != null) String.format(java.util.Locale.US, "%.2f", term.tgpa) else "0.00"

            Box(modifier = Modifier.wrapContentSize()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) Blaze else Slab,
                    border = BorderStroke(1.dp, if (isSelected) Blaze else Rule),
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { onSelectTerm(term.id) }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (term.isCurrent) "${term.name} ★" else term.name,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) Ink else Chalk,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "TGPA: $tgpaStr",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Ink.copy(alpha = 0.8f) else Slate
                        )
                    }
                }

                if (term.isDeletable && onDeleteTerm != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(16.dp)
                            .background(Color(0xFFEF4444), RoundedCornerShape(8.dp))
                            .clickable { onDeleteTerm(term) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        // Add Term Chip
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Slab,
            border = BorderStroke(1.dp, Rule),
            modifier = Modifier
                .height(44.dp)
                .clickable { onAddTerm() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Blaze, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NEW TERM",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Blaze
                )
            }
        }
    }
}

// ── ACTIVE TERM HEADER CARD ─────────────────────────────────────────────────

@Composable
fun ActiveTermHeaderCard(
    term: TermData,
    isMarksMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    onDeleteTerm: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Slab,
        border = BorderStroke(1.dp, Rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = term.name.uppercase(),
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Blaze,
                        letterSpacing = 0.5.sp
                    )
                    if (onDeleteTerm != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Term",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteTerm() }
                        )
                    }
                }

                // Mode toggle
                Row(
                    modifier = Modifier
                        .background(Ink, RoundedCornerShape(4.dp))
                        .border(1.dp, Rule, RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (!isMarksMode) Blaze else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { onToggleMode(false) }
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "GRADES",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (!isMarksMode) Ink else Slate
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(if (isMarksMode) Blaze else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { onToggleMode(true) }
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "MARKS",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isMarksMode) Ink else Slate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val tgpaVal = if (term.simulatedTgpa != null) term.simulatedTgpa else (term.manualTgpa ?: 0.0)
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f", tgpaVal),
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Chalk,
                        lineHeight = 36.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TGPA",
                        fontSize = 14.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Blaze
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", term.totalCredits)} Credits",
                        fontSize = 12.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.SemiBold,
                        color = Chalk
                    )
                    Text(
                        text = "${term.gradedSubjects.size} of ${term.subjects.size} Graded",
                        fontSize = 11.sp,
                        fontFamily = BarlowCondensed,
                        color = Slate
                    )
                }
            }
        }
    }
}

// ── TARGET GOAL SIMULATOR ────────────────────────────────────────────────────

@Composable
fun TargetSimulatorCard(
    state: GpaState,
    onSetTarget: (Double?) -> Unit
) {
    var targetInput by remember(state.targetCgpa) {
        mutableStateOf(if (state.targetCgpa != null) String.format(java.util.Locale.US, "%.2f", state.targetCgpa) else "8.50")
    }

    val targetValue = targetInput.toDoubleOrNull() ?: 8.50
    val activeTerm = state.activeTerm
    val activeCr = activeTerm?.totalCredits ?: 0.0
    val totalCr = state.totalCompletedCredits
    val otherWeighted = state.terms.filterNot { it.id == activeTerm?.id }.sumOf { it.weightedPoints }

    val neededTgpa: Double? = if (activeCr > 0.0) {
        val neededWeighted = (targetValue * totalCr) - otherWeighted
        neededWeighted / activeCr
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlabElevated,
        border = BorderStroke(1.dp, Blaze.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = Blaze, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CGPA TARGET SIMULATOR",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Blaze
                    )
                }

                // Custom Target CGPA Control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TARGET:",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Slate
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Ink,
                        border = BorderStroke(1.dp, Blaze.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        val v = (targetInput.toDoubleOrNull() ?: 8.50) - 0.10
                                        val next = (Math.round(v * 100.0) / 100.0).coerceIn(1.0, 10.0)
                                        targetInput = String.format(Locale.US, "%.2f", next)
                                        onSetTarget(next)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("-", color = Blaze, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            androidx.compose.foundation.text.BasicTextField(
                                value = targetInput,
                                onValueChange = { str ->
                                    if (str.length <= 5) {
                                        targetInput = str
                                        val parsed = str.toDoubleOrNull()
                                        if (parsed != null && parsed in 1.0..10.0) {
                                            onSetTarget(parsed)
                                        }
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Chalk,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(44.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .clickable {
                                        val v = (targetInput.toDoubleOrNull() ?: 8.50) + 0.10
                                        val next = (Math.round(v * 100.0) / 100.0).coerceIn(1.0, 10.0)
                                        targetInput = String.format(Locale.US, "%.2f", next)
                                        onSetTarget(next)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("+", color = Blaze, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "To hit ${String.format(java.util.Locale.US, "%.2f", targetValue)} CGPA overall:",
                        fontFamily = BarlowCondensed,
                        fontSize = 13.sp,
                        color = Chalk
                    )
                    Text(
                        text = when {
                            neededTgpa == null -> "Set course credits below to calculate needed TGPA"
                            neededTgpa <= 0.0 -> "Target already secured!"
                            neededTgpa > 10.0 -> "Requires > 10.0 TGPA (Not possible this term)"
                            neededTgpa >= 9.0 -> "Requires predominantly 'O' and 'A+' grades"
                            neededTgpa >= 8.0 -> "Requires 'A' or 'B+' average this term"
                            else -> "Easily achievable with 'B' average"
                        },
                        fontFamily = BarlowCondensed,
                        fontSize = 11.sp,
                        color = if (neededTgpa != null && neededTgpa > 10.0) Color(0xFFFF5252) else Slate
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            neededTgpa == null -> "—"
                            neededTgpa > 10.0 -> "IMPOSSIBLE"
                            else -> String.format(java.util.Locale.US, "%.2f", neededTgpa)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (neededTgpa != null && neededTgpa > 10.0) Color(0xFFFF5252) else if (neededTgpa != null && (activeTerm?.tgpa ?: 0.0) >= neededTgpa) Color(0xFF00E676) else Blaze
                    )
                    Text(
                        text = "NEEDED TGPA",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Slate
                    )
                }
            }
        }
    }
}

// ── SUBJECT CARD (GRADES + MARKS) ───────────────────────────────────────────

@Composable
fun GpaSubjectCard(
    subject: GpaSubject,
    isGlobalMarksMode: Boolean,
    onUpdateSubject: (GpaSubject) -> Unit,
    onDeleteSubject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slab,
        border = BorderStroke(1.dp, Rule),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subject.courseCode,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Chalk
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Credit Pill with +/- stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Ink, RoundedCornerShape(3.dp))
                            .border(1.dp, Rule, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "-",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (subject.credits > 0.0) Slate else Slate.copy(alpha = 0.3f),
                            modifier = Modifier
                                .clickable {
                                    if (subject.credits >= 0.5) {
                                        val next = (subject.credits - 0.5).coerceAtLeast(0.0)
                                        onUpdateSubject(subject.copy(credits = next, userEditedCredits = true))
                                    }
                                }
                                .padding(horizontal = 4.dp)
                        )

                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", subject.credits)} Cr",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (subject.credits > 0.0) Chalk else Slate,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )

                        Text(
                            text = "+",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Slate,
                            modifier = Modifier
                                .clickable {
                                    if (subject.credits < 10.0) {
                                        val next = subject.credits + 0.5
                                        onUpdateSubject(subject.copy(credits = next, userEditedCredits = true))
                                    }
                                }
                                .padding(horizontal = 4.dp)
                        )
                    }

                    if (subject.isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CUSTOM",
                            fontFamily = BarlowCondensed,
                            fontSize = 9.sp,
                            color = Slate
                        )
                    }
                }

                // Selected Grade Pill or Unselected prompt
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val grade = subject.effectiveGrade
                    if (grade != null) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = getGradeColor(grade.letter).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, getGradeColor(grade.letter).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${grade.letter} (${String.format(java.util.Locale.US, "%.0f", grade.points)} pts)",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = getGradeColor(grade.letter),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Tap grade below",
                            fontFamily = BarlowCondensed,
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Slate,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { onDeleteSubject() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isGlobalMarksMode || subject.isMarksMode) {
                // MARKS INPUTS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MarksInputField(
                        label = "CA (30)",
                        value = subject.caMarks,
                        max = 30.0,
                        modifier = Modifier.weight(1f),
                        onValueChange = { onUpdateSubject(subject.copy(caMarks = it)) }
                    )
                    MarksInputField(
                        label = "MID (20)",
                        value = subject.midTermMarks,
                        max = 20.0,
                        modifier = Modifier.weight(1f),
                        onValueChange = { onUpdateSubject(subject.copy(midTermMarks = it)) }
                    )
                    MarksInputField(
                        label = "END (45)",
                        value = subject.endTermMarks,
                        max = 50.0,
                        modifier = Modifier.weight(1f),
                        onValueChange = { onUpdateSubject(subject.copy(endTermMarks = it)) }
                    )
                    MarksInputField(
                        label = "ATT (5)",
                        value = subject.attendanceMarks,
                        max = 5.0,
                        modifier = Modifier.weight(1f),
                        onValueChange = { onUpdateSubject(subject.copy(attendanceMarks = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total: ${String.format(java.util.Locale.US, "%.1f", subject.totalMarks)} / 100",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (subject.totalMarks > 100.0) Color(0xFFFF5252) else Slate
                    )

                    val eg = subject.effectiveGrade
                    if (eg != null) {
                        Text(
                            text = "Predicted: ${eg.letter} • ${eg.label}",
                            fontFamily = BarlowCondensed,
                            fontSize = 11.sp,
                            color = getGradeColor(eg.letter)
                        )
                    }
                }
            } else {
                // GRADE PILLS (UNSELECTED BY DEFAULT UNTIL CLICKED)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LpuGrade.entries.forEach { grade ->
                        val isSelected = subject.selectedGrade.equals(grade.letter, ignoreCase = true)
                        val gradeColor = getGradeColor(grade.letter)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .padding(horizontal = 1.5.dp)
                                .background(
                                    if (isSelected) gradeColor else Ink,
                                    RoundedCornerShape(3.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) gradeColor else Rule,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    // Toggle: if already selected, clicking it again clears it
                                    val newGrade = if (isSelected) null else grade.letter
                                    onUpdateSubject(subject.copy(selectedGrade = newGrade))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = grade.letter,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) Ink else Chalk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarksInputField(
    label: String,
    value: Double?,
    max: Double,
    modifier: Modifier = Modifier,
    onValueChange: (Double?) -> Unit
) {
    var textVal by remember(value) {
        mutableStateOf(if (value != null) String.format(java.util.Locale.US, "%.1f", value) else "")
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = BarlowCondensed,
            fontSize = 9.sp,
            color = Slate
        )
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = textVal,
            onValueChange = { str ->
                textVal = str
                val num = str.toDoubleOrNull()
                onValueChange(num)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Chalk,
                textAlign = TextAlign.Center
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Ink,
                unfocusedContainerColor = Ink,
                focusedBorderColor = Blaze,
                unfocusedBorderColor = Rule
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        )
    }
}

fun getGradeColor(letter: String): Color {
    return when (letter.uppercase()) {
        "O" -> Color(0xFF00E676)
        "A+" -> Color(0xFF00E5FF)
        "A" -> Color(0xFF40C4FF)
        "B+" -> Color(0xFF7C4DFF)
        "B" -> Color(0xFFB388FF)
        "C" -> Color(0xFFFFB74D)
        "D", "P" -> Color(0xFFFF9100)
        else -> Color(0xFFFF5252)
    }
}

// ── DIALOGS: ADD TERM & ADD SUBJECT ─────────────────────────────────────────

@Composable
fun AddTermDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onAdd: (TermData) -> Unit
) {
    var name by remember { mutableStateOf("Semester ${existingCount + 1}") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slab,
            border = BorderStroke(1.dp, Rule),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "ADD NEW SEMESTER",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Chalk
                )
                Text(
                    text = "Enter a name for the new semester. You can add subjects manually after creating it.",
                    fontFamily = BarlowCondensed,
                    fontSize = 12.sp,
                    color = Slate
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Term Name (e.g. Semester 5)", color = Slate) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blaze,
                        unfocusedBorderColor = Rule,
                        focusedTextColor = Chalk,
                        unfocusedTextColor = Chalk
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Slate, fontFamily = BarlowCondensed)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(
                                    TermData(
                                        name = name.trim(),
                                        isCurrent = false,
                                        subjects = emptyList(),
                                        manualTgpa = null,
                                        manualCredits = null,
                                        isCustom = true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("CREATE TERM", color = Ink, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomSubjectDialog(
    onDismiss: () -> Unit,
    onAdd: (GpaSubject) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("3.0") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slab,
            border = BorderStroke(1.dp, Rule),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADD COURSE TO TERM",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Chalk
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Course Code (e.g. INT306)", color = Slate) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = credits,
                    onValueChange = { credits = it },
                    label = { Text("Credits (e.g. 3.0)", color = Slate) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Slate, fontFamily = BarlowCondensed)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cr = credits.toDoubleOrNull() ?: 3.0
                            if (code.isNotBlank()) {
                                onAdd(
                                    GpaSubject(
                                        courseCode = code.trim(),
                                        courseTitle = "Custom Course",
                                        credits = cr,
                                        selectedGrade = null,
                                        isCustom = true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("ADD COURSE", color = Ink, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
