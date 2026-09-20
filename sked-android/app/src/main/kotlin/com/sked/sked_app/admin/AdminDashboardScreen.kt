package com.sked.sked_app.admin

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sked.sked_app.*
import com.sked.sked_app.telemetry.TelemetryManager
import com.sked.sked_app.telemetry.TelemetrySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AdminDashboardScreen(
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var telemetry by remember { mutableStateOf(TelemetrySnapshot()) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var remoteVersionName by remember { mutableStateOf("Checking...") }
    var remoteVersionCode by remember { mutableStateOf<Int?>(null) }
    var lastRefreshedTime by remember { mutableStateOf("Just now") }

    // Pulse animation for LIVE indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    fun loadData() {
        isLoadingStats = true
        coroutineScope.launch {
            val snap = TelemetryManager.fetchFullTelemetry()
            telemetry = snap

            // Check remote version.json
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://raw.githubusercontent.com/tanishsarkar28/Sked/main/sked-web/public/version.json")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val obj = JSONObject(body)
                        remoteVersionName = obj.optString("versionName", "1.0.0")
                        remoteVersionCode = obj.optInt("versionCode", 1)
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    remoteVersionName = "v1.0.0"
                }
            }

            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            lastRefreshedTime = now
            isLoadingStats = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // ── Top Bar Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SKED.",
                            fontSize = 28.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Blaze,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = Color(0xFF1E1E1E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rule)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E).copy(alpha = pulseAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ADMIN CONSOLE",
                                    fontSize = 11.sp,
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    color = Chalk,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = "Real-time Telemetry & Engagement",
                        fontSize = 12.sp,
                        color = Slate
                    )
                }

                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, Rule, RoundedCornerShape(4.dp))
                        .background(Slab)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Exit Admin",
                        tint = Chalk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 2x2 Primary Engagement Metric Cards ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Total Lifetime Installs
                MetricGridCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL INSTALLS",
                    value = if (isLoadingStats) "..." else "${telemetry.totalInstalls}",
                    subtitle = "Unique devices",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = Blaze
                )

                // Card 2: Daily Active Users (DAU)
                MetricGridCard(
                    modifier = Modifier.weight(1f),
                    title = "ACTIVE TODAY (DAU)",
                    value = if (isLoadingStats) "..." else "${telemetry.dauToday}",
                    subtitle = "Unique students today",
                    icon = Icons.Default.Bolt,
                    accentColor = Color(0xFF22C55E)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 3: Widget Background Syncs Today
                MetricGridCard(
                    modifier = Modifier.weight(1f),
                    title = "WIDGET SYNCS",
                    value = if (isLoadingStats) "..." else "${telemetry.widgetSyncsToday}",
                    subtitle = "Glance updates today",
                    icon = Icons.Default.Sync,
                    accentColor = Color(0xFF38BDF8)
                )

                // Card 4: Retention / Active Ratio
                val retentionPercent = if (telemetry.totalInstalls > 0) {
                    val pct = (telemetry.dauToday.toFloat() / telemetry.totalInstalls.toFloat() * 100).toInt()
                    "${pct.coerceIn(0, 100)}%"
                } else {
                    "100%"
                }

                MetricGridCard(
                    modifier = Modifier.weight(1f),
                    title = "ENGAGEMENT RATE",
                    value = if (isLoadingStats) "..." else retentionPercent,
                    subtitle = "DAU / Total ratio",
                    icon = Icons.Default.Speed,
                    accentColor = Color(0xFFA855F7)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Student Cohort & Batch Distribution Card ─────────────────────
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STUDENT COHORT DISTRIBUTION",
                            fontSize = 12.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Slate,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val totalBatches = (telemetry.batch2026 + telemetry.batch2025 + telemetry.batch2024 + telemetry.batch2023 + telemetry.batchOther).coerceAtLeast(1)

                    BatchBarItem("Batch 2026 (1st Year)", telemetry.batch2026, totalBatches, Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(10.dp))
                    BatchBarItem("Batch 2025 (2nd Year)", telemetry.batch2025, totalBatches, Color(0xFF22C55E))
                    Spacer(modifier = Modifier.height(10.dp))
                    BatchBarItem("Batch 2024 (3rd Year)", telemetry.batch2024, totalBatches, Blaze)
                    Spacer(modifier = Modifier.height(10.dp))
                    BatchBarItem("Batch 2023 (4th Year)", telemetry.batch2023, totalBatches, Color(0xFFA855F7))
                    Spacer(modifier = Modifier.height(10.dp))
                    BatchBarItem("Other / PG Cohorts", telemetry.batchOther, totalBatches, Slate)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Department & Stream Distribution Card ────────────────────────
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DEPARTMENT & STREAM DISTRIBUTION",
                                fontSize = 12.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Slate,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Active students across academic schools",
                                fontSize = 10.sp,
                                color = Slate
                            )
                        }
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val deptList = telemetry.getDepartmentList()
                    val totalDepts = deptList.sumOf { it.count }

                    // Visual Multi-Segment Bar Chart
                    DepartmentSegmentedBar(deptList, totalDepts)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Department Table / Breakdown List
                    deptList.forEachIndexed { index, dept ->
                        DepartmentRowItem(
                            name = dept.name,
                            streamCode = dept.streamCode,
                            count = dept.count,
                            total = totalDepts,
                            color = Color(dept.color)
                        )
                        if (index < deptList.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Grid: Version & Release Diagnostics ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "INSTALLED BUILD",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Slate,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v1.0.0",
                            fontSize = 18.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk
                        )
                        Text(
                            text = "Code 1 • Android",
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "OTA REMOTE",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Slate,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = remoteVersionName,
                            fontSize = 18.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Blaze
                        )
                        Text(
                            text = "Code ${remoteVersionCode ?: 1} • GitHub",
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Device & System Diagnostics ──────────────────────────────────
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SESSION & RUNTIME ENVIRONMENT",
                        fontSize = 12.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Slate,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticRow("Device Hardware", "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
                    DiagnosticRow("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    DiagnosticRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
                    DiagnosticRow("Last Synced", lastRefreshedTime)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Action Buttons ───────────────────────────────────────────────
            Button(
                onClick = { loadData() },
                colors = ButtonDefaults.buttonColors(containerColor = Slab),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Chalk, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REFRESH LIVE TELEMETRY",
                    fontSize = 13.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Chalk,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "Local cache purged successfully", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Slab),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Chalk, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PURGE LOCAL APP CACHE",
                    fontSize = 13.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Chalk
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EXIT ADMIN CONSOLE",
                    fontSize = 15.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MetricGridCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slab,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Slate,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontSize = 32.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                color = Chalk,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Slate
            )
        }
    }
}

@Composable
private fun BatchBarItem(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val progress = (count.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, color = Chalk)
            Text(text = "$count students", fontSize = 12.sp, color = Slate, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Rule)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Slate)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Chalk)
    }
}

@Composable
private fun DepartmentSegmentedBar(
    departments: List<com.sked.sked_app.telemetry.DepartmentItem>,
    total: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (total > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Rule)
            ) {
                departments.filter { it.count > 0 }.forEach { dept ->
                    val weight = dept.count.toFloat() / total.toFloat()
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(Color(dept.color))
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Rule)
            )
        }
    }
}

@Composable
private fun DepartmentRowItem(
    name: String,
    streamCode: String,
    count: Int,
    total: Int,
    color: Color
) {
    val percentage = if (total > 0) (count * 100f / total) else 0f
    val progress = if (total > 0 && count > 0) (count.toFloat() / total.toFloat()).coerceIn(0.04f, 1f) else 0f

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = name,
                        fontSize = 13.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Chalk
                    )
                    Text(
                        text = streamCode,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = Slate
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$count",
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = " (${String.format(java.util.Locale.US, "%.0f", percentage)}%)",
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Slate
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Rule)
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}
