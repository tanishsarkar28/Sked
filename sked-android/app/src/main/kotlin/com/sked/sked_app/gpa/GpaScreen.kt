package com.sked.sked_app.gpa

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun GpaScreen(
    timetableEntries: List<ClassItem>,
    onSyncUms: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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

    fun updateState(newState: GpaState) {
        gpaState = newState
        GpaStorage.save(context, newState)
    }

    val activeTerm = gpaState.activeTerm

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── TOP COMBINED CGPA EXECUTIVE BANNER ──────────────────────────
            item {
                CombinedCgpaBanner(
                    state = gpaState,
                    onToggleTarget = { showTargetSimulator = !showTargetSimulator },
                    isTargetExpanded = showTargetSimulator,
                    onSyncUms = onSyncUms
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

            // ── TERM TABS STRIP (TERM 1, TERM 2, CURRENT TERM) ──────────────
            item {
                TermTabsStrip(
                    terms = gpaState.terms,
                    activeTermId = gpaState.activeTermId,
                    onSelectTerm = { termId ->
                        updateState(gpaState.copy(activeTermId = termId))
                    },
                    onAddTerm = { showAddTermDialog = true }
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
                        onDeleteTerm = if (!activeTerm.isCurrent && gpaState.terms.size > 1) {
                            {
                                val remaining = gpaState.terms.filterNot { it.id == activeTerm.id }
                                val nextActive = remaining.firstOrNull { it.isCurrent }?.id ?: remaining.first().id
                                updateState(gpaState.copy(terms = remaining, activeTermId = nextActive))
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
}

// ── COMBINED CGPA BANNER ────────────────────────────────────────────────────

@Composable
fun CombinedCgpaBanner(
    state: GpaState,
    onToggleTarget: () -> Unit,
    isTargetExpanded: Boolean,
    onSyncUms: (() -> Unit)? = null
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
                Text(
                    text = if (state.officialCgpa != null) "COMBINED CUMULATIVE SCORE • UMS" else "COMBINED CUMULATIVE SCORE",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (state.officialCgpa != null) Blaze else Slate,
                    letterSpacing = 0.5.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSyncUms != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSyncUms() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Slate,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "SYNC UMS",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Slate
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onToggleTarget() }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = if (isTargetExpanded) Blaze else Slate,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTargetExpanded) "HIDE GOAL" else "TARGET GOAL",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isTargetExpanded) Blaze else Slate
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
                    Text(
                        text = if (state.displayCgpa != null) String.format(java.util.Locale.US, "%.2f", state.displayCgpa) else "—",
                        fontSize = 42.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Chalk,
                        lineHeight = 42.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CGPA",
                        fontSize = 16.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${state.terms.size} Terms Combined",
                        fontSize = 12.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.SemiBold,
                        color = Chalk
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", state.totalCompletedCredits)} Graded Credits",
                        fontSize = 11.sp,
                        fontFamily = BarlowCondensed,
                        color = Slate
                    )
                }
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
    onAddTerm: () -> Unit
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
            val tgpaStr = if (term.tgpa != null) String.format(java.util.Locale.US, "%.2f", term.tgpa) else "—"

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
                    val tgpaVal = term.tgpa
                    Text(
                        text = if (tgpaVal != null) String.format(java.util.Locale.US, "%.2f", tgpaVal) else "—",
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

    val neededTgpa = if (activeCr > 0.0) {
        val neededWeighted = (targetValue * totalCr) - otherWeighted
        neededWeighted / activeCr
    } else {
        targetValue
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

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("8.00", "8.50", "9.00").forEach { goal ->
                        Box(
                            modifier = Modifier
                                .background(if (targetInput == goal) Blaze else Ink, RoundedCornerShape(3.dp))
                                .border(1.dp, Rule, RoundedCornerShape(3.dp))
                                .clickable {
                                    targetInput = goal
                                    onSetTarget(goal.toDoubleOrNull())
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = goal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (targetInput == goal) Ink else Chalk
                            )
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
                            neededTgpa <= 0.0 -> "Target already secured!"
                            neededTgpa > 10.0 -> "Requires > 10.0 TGPA (Not possible this term)"
                            neededTgpa >= 9.0 -> "Requires predominantly 'O' and 'A+' grades"
                            neededTgpa >= 8.0 -> "Requires 'A' or 'B+' average this term"
                            else -> "Easily achievable with 'B' average"
                        },
                        fontFamily = BarlowCondensed,
                        fontSize = 11.sp,
                        color = if (neededTgpa > 10.0) Color(0xFFFF5252) else Slate
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (neededTgpa > 10.0) "IMPOSSIBLE" else String.format(java.util.Locale.US, "%.2f", neededTgpa),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (neededTgpa > 10.0) Color(0xFFFF5252) else if ((activeTerm?.tgpa ?: 0.0) >= neededTgpa) Color(0xFF00E676) else Blaze
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
                            color = Slate,
                            modifier = Modifier
                                .clickable {
                                    if (subject.credits > 0.5) {
                                        onUpdateSubject(subject.copy(credits = subject.credits - 0.5))
                                    }
                                }
                                .padding(horizontal = 4.dp)
                        )

                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", subject.credits)} Cr",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Chalk,
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
                                        onUpdateSubject(subject.copy(credits = subject.credits + 0.5))
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
    var name by remember { mutableStateOf("Term ${existingCount + 1}") }
    var tgpaInput by remember { mutableStateOf("") }
    var creditsInput by remember { mutableStateOf("20.0") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slab,
            border = BorderStroke(1.dp, Rule),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADD NEW TERM",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Chalk
                )
                Text(
                    text = "Add a past term to compute your overall CGPA.",
                    fontFamily = BarlowCondensed,
                    fontSize = 11.sp,
                    color = Slate
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Term Name (e.g. Term 1)", color = Slate) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tgpaInput,
                        onValueChange = { tgpaInput = it },
                        label = { Text("TGPA (Optional)", color = Slate, fontSize = 11.sp) },
                        placeholder = { Text("e.g. 8.40", color = Slate) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = creditsInput,
                        onValueChange = { creditsInput = it },
                        label = { Text("Credits", color = Slate, fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

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
                            val tg = tgpaInput.toDoubleOrNull()
                            val cr = creditsInput.toDoubleOrNull() ?: 20.0
                            if (name.isNotBlank()) {
                                onAdd(
                                    TermData(
                                        name = name.trim(),
                                        isCurrent = false,
                                        manualTgpa = tg,
                                        manualCredits = cr
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
