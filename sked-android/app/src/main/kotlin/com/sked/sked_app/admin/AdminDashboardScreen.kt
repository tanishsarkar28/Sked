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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sked.sked_app.*
import com.sked.sked_app.telemetry.InstallTracker
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

    var installCount by remember { mutableStateOf<Int?>(null) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var remoteVersionName by remember { mutableStateOf("Loading...") }
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
            val count = InstallTracker.getLiveInstallCount()
            installCount = if (count >= 0) count else 0

            // Also check remote version.json
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
                    remoteVersionName = "v1.0.0 (offline)"
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Root Master Control & Telemetry",
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── HERO CARD: Total User Installs ───────────────────────────────
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GLOBAL INSTALLATIONS",
                            fontSize = 12.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Slate,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingStats) {
                        CircularProgressIndicator(
                            color = Blaze,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                            text = "${installCount ?: 0}",
                            fontSize = 54.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk,
                            letterSpacing = (-1).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Unique devices that have installed and opened Sked.",
                        fontSize = 13.sp,
                        color = Slate
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last synced: $lastRefreshedTime",
                            fontSize = 11.sp,
                            color = Slate
                        )

                        Button(
                            onClick = { loadData() },
                            colors = ButtonDefaults.buttonColors(containerColor = SlabElevated),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Chalk,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REFRESH",
                                fontSize = 12.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Chalk
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Grid: Version & Release Diagnostics ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: Installed Version
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slab,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "LOCAL BUILD",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Slate,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "v1.0.0",
                            fontSize = 20.sp,
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

                // Right Card: OTA Remote Version
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = remoteVersionName,
                            fontSize = 20.sp,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticRow("Device Hardware", "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
                    DiagnosticRow("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    DiagnosticRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
                    DiagnosticRow("OTA Host", "raw.githubusercontent.com")
                    DiagnosticRow("Telemetry Provider", "Abacus REST Service")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Admin Actions ────────────────────────────────────────────────
            Text(
                text = "ADMIN CONTROLS",
                fontSize = 12.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                color = Slate,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Purge App Cache
            Button(
                onClick = {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "Local app cache purged successfully", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Slab),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Chalk, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "PURGE LOCAL APP CACHE",
                    fontSize = 13.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Chalk
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Exit Console
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "EXIT ADMIN CONSOLE",
                    fontSize = 15.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Slate)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Chalk)
    }
}
