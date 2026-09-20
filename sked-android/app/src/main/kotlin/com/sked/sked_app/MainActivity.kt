package com.sked.sked_app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.sked.sked_app.widget.PREFS_NAME
import com.sked.sked_app.widget.TimetableWidget
import com.sked.sked_app.widget.TimetableRefreshWorker
import com.sked.sked_app.widget.TimetableRefreshWorker.Companion.KEY_USER_ID
import com.sked.sked_app.widget.TimetableWidgetReceiver
import com.sked.sked_app.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Models ───────────────────────────────────────────────────────────────────

data class ClassItem(
    val day: String = "",
    val timeRange: String = "",
    val start: String = "",
    val end: String = "",
    val room: String = "",
    val courseCode: String = "",
    val type: String = "Lecture",
    val teacher: String = "",
    val section: String = "",
    val group: String = "",
    val description: String = ""
)

// ── Sked Palette — logo-anchored: orange on black, one accent, no scatter ───

val Ink          = Color(0xFF0A0A0A) // True near-black — logo canvas
val Slab         = Color(0xFF141414) // Card/surface — barely visible lift
val SlabElevated = Color(0xFF1A1A1A) // Elevated surface
val Rule         = Color(0xFF252525) // Dividers, borders — mechanical hairline
val Chalk   = Color(0xFFE8E6E3) // Primary text — warm off-white
val Slate   = Color(0xFF7A7774) // Secondary/metadata — warm mid-grey
val Blaze   = Color(0xFFFF6B1A) // Logo orange — THE ONLY chromatic colour
val DangerRed = Color(0xFFEF4444) // Destructive action red

// Legacy aliases — keep until full migration, then remove
val DeepBg          = Ink
val SurfaceCard     = Slab
val SurfaceElevated = Color(0xFF1A1A1A)
val SurfacePill     = Color(0xFF1E1E1E)
val PrimaryPurple   = Blaze
val PurpleGlow      = Blaze
val AccentTeal      = Blaze
val AccentAmber     = Blaze
val AccentRose      = DangerRed
val TextPrimary     = Chalk
val TextSecondary   = Slate
val TextMuted       = Color(0xFF5A5856)
val BorderColor     = Rule
val BorderActive    = Color(0xFF333333)

// ── Typography — Barlow Condensed for display, Inter/default for body ────────

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WebView.setWebContentsDebuggingEnabled(true)
        com.sked.sked_app.telemetry.InstallTracker.registerInstallIfNeeded(this)
        TimetableRefreshWorker.schedule(this)
        com.sked.sked_app.widget.WeeklyResetWorker.schedule(this)
        AttendanceManager.checkAndResetWeekly(this)

        setContent {
            SkedApp(
                onPinWidget = { pinHomeWidget() },
                onWidgetUpdate = { TimetableRefreshWorker.runNow(this) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        TimetableRefreshWorker.runNow(this)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            TimetableRefreshWorker.updateWidget(this@MainActivity)
        }
    }

    private fun pinHomeWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getSystemService(AppWidgetManager::class.java)
            val myProvider = ComponentName(this, TimetableWidgetReceiver::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = PendingIntent.getBroadcast(
                    this, 0,
                    Intent(this, TimetableWidgetReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            } else {
                Toast.makeText(this, "Home screen pin not supported by your launcher. Please add widget from home screen directly.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Long-press your home screen to add the Sked widget.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun verifyAdminCredentials(id: String, pass: String): Boolean {
    val input = "${id.trim()}:${pass}:sked_admin_salt_99"
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    val hash = digest.joinToString("") { "%02x".format(it) }
    return hash == "724a25c83f0bc481e107a9b40df041af5fdee54ef7353f411917af941f901b76"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkedApp(onPinWidget: () -> Unit, onWidgetUpdate: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var currentUserId by remember {
        mutableStateOf(prefs.getString(KEY_USER_ID, "") ?: "")
    }

    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    var todayName by remember { mutableStateOf(TimetableParser.todayName()) }
    val initialDay = remember {
        val currentDay = TimetableParser.todayName()
        if (currentDay in days) currentDay else "Monday"
    }

    var todayClasses by remember { mutableStateOf<List<ClassItem>>(emptyList()) }
    var weekClasses by remember { mutableStateOf<Map<String, List<ClassItem>>>(emptyMap()) }
    var selectedDay by remember { mutableStateOf(initialDay) }
    var isLoading by remember { mutableStateOf(false) }
    var showWebViewBridge by remember { mutableStateOf(false) }
    var showReSyncDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var loginUserIdInput by remember { mutableStateOf("") }
    var loginPasswordInput by remember { mutableStateOf("") }
    var hasTimetable by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun loadLocalData() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val allEntries = TimetableParser.loadFromPrefs(context)
                todayName = TimetableParser.todayName()
                val todayEntries = TimetableParser.filterByDay(allEntries, todayName)
                val weekMap = TimetableParser.groupByDay(allEntries)

                AttendanceManager.populateWeekFromHistory(context)
                withContext(Dispatchers.Main) {
                    todayClasses = todayEntries
                    weekClasses = weekMap
                    hasTimetable = allEntries.isNotEmpty()
                    if (selectedDay !in days) {
                        selectedDay = if (todayName in days) todayName else "Monday"
                    }
                    isLoading = false
                }

                // Background sync live attendance from mobile API without blocking timetable display
                try {
                    val savedPwd = prefs.getString("saved_ums_pwd", "") ?: ""
                    val hasToken = !prefs.getString("mobile_token", "").isNullOrBlank()
                    if (!hasToken && savedPwd.isNotBlank() && currentUserId.isNotBlank()) {
                        AttendanceManager.loginAndSyncMobileApi(context, currentUserId, savedPwd)
                    } else {
                        AttendanceManager.refreshFromMobileApi(context)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Sked", "Error refreshing from mobile API", e)
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLocalData()
    }

    // Logout function
    fun performLogout() {
        prefs.edit().clear().apply()
        AttendanceManager.clearAll(context)
        currentUserId = ""
        todayClasses = emptyList()
        weekClasses = emptyMap()
        hasTimetable = false
        loginUserIdInput = ""
        loginPasswordInput = ""
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
        onWidgetUpdate()
    }

    var selectedCourseForDetail by remember { mutableStateOf<ClassItem?>(null) }
    var showAdminDashboard by remember { mutableStateOf(false) }

    // ── SCREEN SWITCHING: Admin / Login / Detail / Dashboard ──────────────
    if (showAdminDashboard) {
        com.sked.sked_app.admin.AdminDashboardScreen(
            onExit = { showAdminDashboard = false }
        )
    } else if (currentUserId.isBlank()) {
        LoginScreen(
            onStartLogin = { id, pass ->
                if (verifyAdminCredentials(id, pass)) {
                    showAdminDashboard = true
                    return@LoginScreen
                }
                loginUserIdInput = id
                loginPasswordInput = pass
                prefs.edit().putString("saved_ums_pwd", pass).apply()
                coroutineScope.launch(Dispatchers.IO) {
                    AttendanceManager.loginAndSyncMobileApi(context, id, pass)
                }
                showWebViewBridge = true
            }
        )
    } else if (selectedCourseForDetail != null) {
        val detailTarget = selectedCourseForDetail!!
        CourseDetailScreen(
            courseCode = detailTarget.courseCode,
            courseName = detailTarget.description,
            teacher = detailTarget.teacher,
            room = detailTarget.room,
            section = detailTarget.section,
            onBack = { selectedCourseForDetail = null }
        )
    } else {
        // ── LOGGED IN: Main Timetable Dashboard ──────────────────────────────
        DashboardScreen(
            userId = currentUserId,
            todayClasses = todayClasses,
            weekClasses = weekClasses,
            selectedDay = selectedDay,
            onSelectDay = { selectedDay = it },
            todayName = todayName,
            days = days,
            isLoading = isLoading,
            onRefresh = { loadLocalData() },
            onPinWidget = onPinWidget,
            onCourseClick = { selectedCourseForDetail = it },
            onReSync = {
                loginUserIdInput = currentUserId
                showReSyncDialog = true
            },
            onLogoutClick = { showLogoutDialog = true }
        )
    }

    // ── Turnstile / UMS WebView Bridge Dialog ────────────────────────────────
    if (showWebViewBridge) {
        UmsAuthBridgeDialog(
            userId = loginUserIdInput,
            password = loginPasswordInput,
            onDismiss = { showWebViewBridge = false },
            onSuccess = { syncedUserId ->
                currentUserId = syncedUserId
                prefs.edit()
                    .putString(KEY_USER_ID, syncedUserId)
                    .putString("saved_ums_user", syncedUserId)
                    .putString("saved_ums_pwd", loginPasswordInput)
                    .apply()
                showWebViewBridge = false
                showReSyncDialog = false
                loadLocalData()
                onWidgetUpdate()
            }
        )
    }

    // ── Re-Sync Password Dialog ──────────────────────────────────────────────
    if (showReSyncDialog) {
        val savedPwd = prefs.getString("saved_ums_pwd", "") ?: ""
        var resyncPassword by remember { mutableStateOf(savedPwd) }
        var passwordVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showReSyncDialog = false },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = PrimaryPurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-Sync Timetable", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "Enter your UMS password to refresh your latest schedule for $currentUserId:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = resyncPassword,
                        onValueChange = { resyncPassword = it },
                        label = { Text("UMS Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = PrimaryPurple,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resyncPassword.isNotBlank()) {
                            prefs.edit().putString("saved_ums_pwd", resyncPassword).apply()
                            loginPasswordInput = resyncPassword
                            showReSyncDialog = false
                            coroutineScope.launch(Dispatchers.IO) {
                                AttendanceManager.loginAndSyncMobileApi(context, currentUserId, resyncPassword)
                            }
                            showWebViewBridge = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sync Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReSyncDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // ── Logout Confirmation Dialog ───────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Log Out from Sked?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This will remove your saved credentials and timetable from this device and widget. You can log back in anytime.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        performLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ── Login — SKED. wordmark, flat Ink background, one Blaze CTA ──────────────

@Composable
fun LoginScreen(
    onStartLogin: (userId: String, password: String) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }

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
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // SKED. wordmark — the logo IS the brand, no icon needed
            Text(
                text = "SKED.",
                fontSize = 48.sp,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                color = Blaze,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Sign-in card — sharp, utilitarian
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slab,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "SIGN IN.",
                        fontSize = 18.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = Chalk
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Enter your UMS credentials to sync your class schedule.",
                        fontSize = 13.sp,
                        color = Slate,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Registration Number
                    OutlinedTextField(
                        value = userId,
                        onValueChange = {
                            userId = it.trim()
                            errorMessage = ""
                        },
                        label = { Text("Registration Number") },
                        placeholder = { Text("e.g. 1240xxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Chalk
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = Slate)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blaze,
                            unfocusedBorderColor = Rule,
                            focusedTextColor = Chalk,
                            unfocusedTextColor = Chalk,
                            focusedLabelColor = Blaze,
                            unfocusedLabelColor = Slate,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = ""
                        },
                        label = { Text("UMS Password") },
                        placeholder = { Text("Enter your password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 15.sp,
                            color = Chalk
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Slate)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Slate
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blaze,
                            unfocusedBorderColor = Rule,
                            focusedTextColor = Chalk,
                            unfocusedTextColor = Chalk,
                            focusedLabelColor = Blaze,
                            unfocusedLabelColor = Slate,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error message
                    if (errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Blaze.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(1.dp, Blaze.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Blaze,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                color = Blaze,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // SIGN IN. button — Blaze filled, Ink text
                    Button(
                        onClick = {
                            if (userId.isBlank()) {
                                errorMessage = "Please enter your registration number"
                                return@Button
                            }
                            if (password.isBlank()) {
                                errorMessage = "Please enter your UMS password"
                                return@Button
                            }
                            onStartLogin(userId, password)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "SIGN IN.",
                            fontSize = 16.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Trust notice — period-terminated
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Slate,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "On-device only. Never leaves your phone.",
                            fontSize = 11.sp,
                            color = Slate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showAboutDialog = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BUILT BY TANISH SARKAR",
                    fontSize = 11.sp,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Slate
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showAboutDialog) {
            AboutDeveloperDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

// ── Main Timetable Dashboard (Shown when user IS logged in) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userId: String,
    todayClasses: List<ClassItem>,
    weekClasses: Map<String, List<ClassItem>>,
    selectedDay: String,
    onSelectDay: (String) -> Unit,
    todayName: String,
    days: List<String>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onPinWidget: () -> Unit,
    onCourseClick: (ClassItem) -> Unit = {},
    onReSync: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    var attendanceVersion by remember { mutableStateOf(0) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val popCount = AttendanceManager.populateWeekFromHistory(context)
            if (popCount > 0) {
                withContext(Dispatchers.Main) {
                    attendanceVersion++
                }
            }
            val count = AttendanceManager.refreshFromMobileApi(context)
            if (count > 0) {
                withContext(Dispatchers.Main) {
                    attendanceVersion++
                }
            }
            try {
                val update = AppUpdateManager.checkForUpdate(context)
                if (update != null) {
                    withContext(Dispatchers.Main) {
                        pendingUpdate = update
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val currentDayDate = remember {
        SimpleDateFormat("EEEE • MMM d", Locale.getDefault()).format(Date())
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SKED.",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Blaze,
                        letterSpacing = 0.5.sp
                    )
                },
                actions = {
                    val refreshRotation by rememberInfiniteTransition(label = "refresh").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "refreshRot"
                    )

                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Developer",
                            tint = Slate
                        )
                    }

                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isLoading) Blaze else Chalk,
                            modifier = if (isLoading) Modifier.rotate(refreshRotation) else Modifier
                        )
                    }

                    IconButton(onClick = onPinWidget) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Pin Widget",
                            tint = Slate
                        )
                    }

                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log Out",
                            tint = DangerRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header row — flat, rule-separated, no card wrapper
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "REG: $userId",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Slate
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentDayDate,
                                fontSize = 12.sp,
                                color = Slate
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val isTodaySelected = selectedDay.equals(todayName, ignoreCase = true)
                        val displayCount = if (isTodaySelected) todayClasses.size else (weekClasses.entries.find { it.key.equals(selectedDay, ignoreCase = true) }?.value?.size ?: 0)
                        Text(
                            text = if (isTodaySelected) {
                                if (displayCount > 0) "$displayCount classes today." else "Nothing today."
                            } else {
                                if (displayCount > 0) "$displayCount classes on $selectedDay." else "No classes on $selectedDay."
                            },
                            fontSize = 16.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk
                        )
                    }

                    // Re-sync — sharp, utilitarian
                    OutlinedButton(
                        onClick = onReSync,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Chalk),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "RE-SYNC",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            color = Chalk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Rule, thickness = 1.dp)
            }

            // Day selector — distributed evenly across screen width, filling available space
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEach { day ->
                    val isSelected = selectedDay.equals(day, ignoreCase = true)
                    val isCurrentDay = todayName.equals(day, ignoreCase = true)
                    val count = weekClasses.entries.find { it.key.equals(day, ignoreCase = true) }?.value?.size
                        ?: if (isCurrentDay) todayClasses.size else 0
                    val label = day.uppercase().take(3)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectDay(day) }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) Blaze else if (isCurrentDay) Chalk else Slate
                            )
                            if (isCurrentDay) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Blaze)
                                )
                            }
                        }
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Blaze else Slate
                            )
                        } else {
                            Spacer(modifier = Modifier.height(13.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(2.dp)
                                .background(if (isSelected) Blaze else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(color = Rule, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

            // Class list with crossfade
            AnimatedContent(
                targetState = selectedDay,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(140))
                },
                label = "daySwitch"
            ) { targetDay ->
                val isToday = targetDay.equals(todayName, ignoreCase = true)
                val listForDay = weekClasses.entries.find { it.key.equals(targetDay, ignoreCase = true) }?.value
                    ?: if (isToday) todayClasses else emptyList()

                if (isLoading && listForDay.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SkeletonClassCard()
                        SkeletonClassCard()
                        SkeletonClassCard()
                    }
                } else if (listForDay.isEmpty()) {
                    // Empty state — text only, no card wrapper, no icon
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isToday) "Nothing today." else "No classes scheduled.",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = Chalk
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isToday) "You're clear." else "Free day.",
                                fontSize = 14.sp,
                                color = Slate
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            DeveloperFooterCard(onClick = { showAboutDialog = true })
                        }
                    }
                } else {
                    val nowCal = Calendar.getInstance()
                    val nowM = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

                    val todayIndex = days.indexOfFirst { it.equals(todayName, ignoreCase = true) }
                    val targetDayIndex = days.indexOfFirst { it.equals(targetDay, ignoreCase = true) }

                    val todayList = if (todayIndex >= 0) {
                        weekClasses.entries.find { it.key.equals(todayName, ignoreCase = true) }?.value
                            ?: if (isToday) todayClasses else emptyList()
                    } else emptyList()

                    val todayAllDone = todayList.isNotEmpty() && todayList.all { item ->
                        val sM = parseTimeInMinutes(item.start)
                        val eEnd = parseTimeInMinutes(item.end)
                        val eM = if (eEnd > 0) eEnd else if (sM > 0) sM + 50 else 0
                        eM > 0 && nowM > eM
                    }

                    // Find the single next upcoming class across the timetable
                    var upcomingDayName: String? = null
                    var upcomingClassItem: ClassItem? = null

                    if (todayIndex >= 0 && !todayAllDone) {
                        // 1. Is there an active live class today?
                        val live = todayList.firstOrNull { item ->
                            val sM = parseTimeInMinutes(item.start)
                            val eEnd = parseTimeInMinutes(item.end)
                            val eM = if (eEnd > 0) eEnd else if (sM > 0) sM + 50 else 0
                            sM > 0 && eM > 0 && nowM in sM..eM
                        }
                        if (live != null) {
                            upcomingDayName = todayName
                            upcomingClassItem = live
                        } else {
                            // 2. Next class starting later today
                            val nextToday = todayList.firstOrNull { item ->
                                val sM = parseTimeInMinutes(item.start)
                                sM > nowM
                            }
                            if (nextToday != null) {
                                upcomingDayName = todayName
                                upcomingClassItem = nextToday
                            }
                        }
                    }

                    // 3. If no upcoming class found today (all classes over or today is Sunday/free day):
                    if (upcomingClassItem == null) {
                        if (todayIndex < 0) {
                            val isPast2359 = (nowCal.get(Calendar.HOUR_OF_DAY) == 23 && nowCal.get(Calendar.MINUTE) >= 59)
                            if (isPast2359) {
                                // After Sunday 23:59 reset: next upcoming class is the first scheduled class of the upcoming week
                                for (dName in days) {
                                    val dList = weekClasses.entries.find { it.key.equals(dName, ignoreCase = true) }?.value ?: emptyList()
                                    if (dList.isNotEmpty()) {
                                        upcomingDayName = dName
                                        upcomingClassItem = dList.first()
                                        break
                                    }
                                }
                            }
                            // Before Sunday 23:59: active week has concluded; upcomingClassItem remains null
                        } else {
                            // Monday to Saturday: only look forward to future days in this week
                            for (checkIndex in (todayIndex + 1) until days.size) {
                                val dName = days[checkIndex]
                                val dList = weekClasses.entries.find { it.key.equals(dName, ignoreCase = true) }?.value ?: emptyList()
                                if (dList.isNotEmpty()) {
                                    upcomingDayName = dName
                                    upcomingClassItem = dList.first()
                                    break
                                }
                            }
                        }
                    }

                    val allDone = isToday && todayAllDone

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (allDone) {
                            item {
                                CelebrationCard(count = listForDay.size)
                            }
                        }

                        itemsIndexed(listForDay) { index, item ->
                            val _v = attendanceVersion
                            val sM = parseTimeInMinutes(item.start)
                            val eEnd = parseTimeInMinutes(item.end)
                            val eM = if (eEnd > 0) eEnd else if (sM > 0) sM + 50 else 0

                            val isLive = isToday && (sM > 0 && eM > 0 && nowM in sM..eM)
                            val isUpcoming = if (todayIndex < 0) false else targetDay.equals(upcomingDayName, ignoreCase = true) && (item == upcomingClassItem)

                            val isSunday = todayIndex < 0
                            val isPastSunday2359 = isSunday && (nowCal.get(Calendar.HOUR_OF_DAY) == 23 && nowCal.get(Calendar.MINUTE) >= 59)

                            val isOver = when {
                                isSunday -> !isPastSunday2359
                                targetDayIndex < todayIndex -> true
                                targetDayIndex == todayIndex -> (eM > 0 && nowM > eM)
                                else -> false
                            }

                            val timingState = when {
                                isOver -> ClassTimingState.OVER
                                isUpcoming -> ClassTimingState.UPCOMING
                                else -> ClassTimingState.PENDING
                            }

                            val targetDateKey = AttendanceManager.getDateKeyForDay(targetDay)
                            val status = AttendanceManager.getStatus(context, item.courseCode, item.start, targetDateKey)
                            val courseAtt = AttendanceManager.getCourseAttendance(context, item.courseCode)

                            StaggeredClassCard(
                                item = item,
                                index = index,
                                isLive = isLive,
                                timingState = timingState,
                                attendanceStatus = status,
                                courseAttendance = courseAtt,
                                onClick = { onCourseClick(item) }
                            )
                        }

                        item {
                            DeveloperFooterCard(onClick = { showAboutDialog = true })
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDeveloperDialog(
            onDismiss = { showAboutDialog = false },
            onUpdateFound = { update ->
                showAboutDialog = false
                pendingUpdate = update
            }
        )
    }

    if (pendingUpdate != null) {
        UpdateAvailableDialog(
            updateInfo = pendingUpdate!!,
            onDismiss = { pendingUpdate = null }
        )
    }
}

// ── In-App OTA Update Dialog (Departures Board Styling) ──────────────────────

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    Dialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading && !updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate && downloadState !is DownloadState.Downloading,
            dismissOnClickOutside = !updateInfo.forceUpdate && downloadState !is DownloadState.Downloading
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SlabElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Header badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Blaze)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UPDATE AVAILABLE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Blaze,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Slab,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rule)
                    ) {
                        Text(
                            text = "v${updateInfo.versionName}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Chalk,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A new version of Sked is available.",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Chalk
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Release notes box
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Ink,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "WHAT'S NEW:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 13.sp,
                            color = Chalk.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                        if (updateInfo.fileSize.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Package Size: ${updateInfo.fileSize}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Slate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download / Install UI State
                when (val state = downloadState) {
                    is DownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!updateInfo.forceUpdate) {
                                TextButton(onClick = onDismiss) {
                                    Text(
                                        text = "LATER",
                                        color = Slate,
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    downloadState = DownloadState.Downloading(0f, 0L, 0L)
                                    coroutineScope.launch {
                                        val file = AppUpdateManager.downloadApk(context, updateInfo) { prog, curr, tot ->
                                            downloadState = DownloadState.Downloading(prog, curr, tot)
                                        }
                                        if (file != null) {
                                            downloadState = DownloadState.ReadyToInstall(file)
                                            AppUpdateManager.installApk(context, file)
                                        } else {
                                            downloadState = DownloadState.Error("Download failed. Please check network connection.")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Blaze)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Ink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "UPDATE NOW",
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Ink
                                )
                            }
                        }
                    }

                    is DownloadState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading update...",
                                    fontSize = 12.sp,
                                    color = Slate
                                )
                                val currentMb = String.format(java.util.Locale.US, "%.1f", state.currentBytes / (1024f * 1024f))
                                val totalMb = if (state.totalBytes > 0) String.format(java.util.Locale.US, "%.1f MB", state.totalBytes / (1024f * 1024f)) else ""
                                Text(
                                    text = if (totalMb.isNotEmpty()) "$percent% ($currentMb / $totalMb)" else "$percent%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Blaze
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Blaze,
                                trackColor = Rule
                            )
                        }
                    }

                    is DownloadState.ReadyToInstall -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Download complete! Ready to install.",
                                fontSize = 13.sp,
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { AppUpdateManager.installApk(context, state.file) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                            ) {
                                Text(
                                    text = "TAP TO INSTALL UPDATE",
                                    color = Ink,
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    is DownloadState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDismiss) {
                                    Text("CANCEL", color = Slate)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { downloadState = DownloadState.Idle },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Blaze)
                                ) {
                                    Text("RETRY", color = Ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Fullscreen/Sheet WebView Dialog for Cloudflare Turnstile Authentication ──

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UmsAuthBridgeDialog(
    userId: String,
    password: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Connecting to LPU UMS...") }
    var isSyncing by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBg)
                .padding(top = 28.dp),
            color = DeepBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LPU UMS Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = if (isSyncing) AccentTeal else TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            webViewRef?.evaluateJavascript("window.skedForceSubmit && window.skedForceSubmit()", null)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Sync Now", tint = AccentTeal)
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }

                if (isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentTeal,
                        trackColor = SurfaceElevated
                    )
                }

                // Continuous Polling and Auto-Submit Engine
                LaunchedEffect(webViewRef, userId, password) {
                    val wv = webViewRef ?: return@LaunchedEffect
                    var isDone = false
                    val safeUser = JSONObject.quote(userId)
                    val safePass = JSONObject.quote(password)

                    try {
                        val themeJs = context.assets.open("theme.js").bufferedReader().use { it.readText() }
                        wv.evaluateJavascript(themeJs, null)
                    } catch (_: Exception) {}

                    while (!isDone) {
                        kotlinx.coroutines.delay(800)
                        val tickJs = """
                            (function() {
                                try {
                                    var u = document.getElementById('txtU');
                                    var p = document.querySelector('input[type="password"]');
                                    var cf = document.querySelector('[name="cf-turnstile-response"]');
                                    var form = document.querySelector('form');

                                    if (u) {
                                        u.removeAttribute('onchange');
                                        u.onchange = null;
                                        if ($safeUser && (!u.value || u.value.length === 0)) {
                                            u.value = $safeUser;
                                        }
                                    }
                                    if (p && $safePass && (!p.value || p.value.length === 0)) {
                                        p.value = $safePass;
                                    }

                                    if (window._skedTimetableResult) {
                                        return JSON.stringify({ hasResult: true, status: 'Timetable ready' });
                                    }

                                    if (window._skedIsSyncing) {
                                        return JSON.stringify({ syncing: true, status: window._skedStatus || 'Authenticating with UMS...' });
                                    }

                                    function triggerSubmit() {
                                        if (window._skedIsSyncing || window._skedTimetableResult) return;
                                        var curU = document.getElementById('txtU');
                                        var curP = document.querySelector('input[type="password"]');
                                        var curCf = document.querySelector('[name="cf-turnstile-response"]');

                                        if (curU && !curU.value && $safeUser) curU.value = $safeUser;
                                        if (curP && !curP.value && $safePass) curP.value = $safePass;

                                        if (!curU || !curU.value) {
                                            window._skedStatus = 'Please enter Registration No';
                                            return;
                                        }
                                        if (!curP || !curP.value) {
                                            window._skedStatus = 'Please enter password';
                                            return;
                                        }
                                        if (!curCf || !curCf.value) {
                                            window._skedStatus = 'Please complete Turnstile check';
                                            return;
                                        }

                                        window._skedIsSyncing = true;
                                        window._skedStatus = 'Authenticating with UMS...';

                                        var fd = new FormData(form);
                                        var params = new URLSearchParams(fd);
                                        var submitBtn = form ? form.querySelector('input[type="submit"]') : null;
                                        if (submitBtn && !params.has(submitBtn.name)) {
                                            params.append(submitBtn.name, submitBtn.value || 'Login');
                                        }

                                        function extractAttendance(docSources) {
                                            var todayStatus = [];
                                            var courses = [];
                                            var debug = [];
                                            var courseCodeRegex = /\b([A-Z]{2,5}\d{3,4})\b/i;
                                            function clean(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
                                            function getText(el) { return clean(el.textContent || el.innerText || ''); }

                                            var parser = new DOMParser();
                                            var allHrefs = [];

                                            for (var s = 0; s < docSources.length; s++) {
                                                var src = docSources[s];
                                                if (!src.html || src.html.length < 30) continue;
                                                var doc = parser.parseFromString(src.html, 'text/html');
                                                var rows = doc.querySelectorAll('tr');
                                                debug.push(src.name + ':r=' + rows.length + '(' + src.html.length + 'b)');

                                                // Discover attendance-related links
                                                var links = doc.querySelectorAll('a[href]');
                                                for (var l = 0; l < links.length; l++) {
                                                    var h = links[l].getAttribute('href') || '';
                                                    if (/attend|daily|present|absent/i.test(h) && !allHrefs.includes(h)) {
                                                        allHrefs.push(h);
                                                    }
                                                }

                                                // Pass 1: check rows for today's classes and status
                                                for (var i = 0; i < rows.length; i++) {
                                                    var row = rows[i];
                                                    var rowText = getText(row);
                                                    var cMatch = rowText.match(courseCodeRegex);
                                                    if (!cMatch) continue;

                                                    var code = cMatch[1].toUpperCase();
                                                    var timeMatch = rowText.match(/(\d{1,2}:\d{2})\s*(?:-\s*(\d{1,2}:\d{2}))?/);
                                                    var start = timeMatch ? timeMatch[1] : '';
                                                    var end = (timeMatch && timeMatch[2]) ? timeMatch[2] : '';

                                                    var status = 'UNMARKED';
                                                    var lower = rowText.toLowerCase();

                                                    if (/\b(present|attended)\b/.test(lower)) {
                                                        status = 'PRESENT';
                                                    } else if (/\b(absent)\b/.test(lower)) {
                                                        status = 'ABSENT';
                                                    } else if (/\b(duty\s*leave|dl)\b/.test(lower)) {
                                                        status = 'DUTY_LEAVE';
                                                    } else if (/\b(leave)\b/.test(lower)) {
                                                        status = 'DUTY_LEAVE';
                                                    } else if (/\b(pending|not\s*marked|yet\s*to\s*be|unmarked)\b/.test(lower)) {
                                                        status = 'UNMARKED';
                                                    } else {
                                                        var cells = row.querySelectorAll('td');
                                                        for (var c = 0; c < cells.length; c++) {
                                                            var cell = cells[c];
                                                            var style = (cell.getAttribute('style') || '').toLowerCase();
                                                            var cls = (cell.className || '').toLowerCase();
                                                            var cellTxt = getText(cell).toLowerCase();
                                                            if (cls.includes('success') || style.includes('green') || cls.includes('present')) {
                                                                if (!cellTxt.includes('total') && !cellTxt.includes('%')) status = 'PRESENT';
                                                            } else if (cls.includes('danger') || style.includes('red') || cls.includes('absent')) {
                                                                if (!cellTxt.includes('total') && !cellTxt.includes('%')) status = 'ABSENT';
                                                            }
                                                        }
                                                    }

                                                    var existing = todayStatus.find(function(x) {
                                                        return x.courseCode === code && (!start || x.start === start);
                                                    });
                                                    if (!existing) {
                                                        todayStatus.push({
                                                            courseCode: code,
                                                            start: start,
                                                            timeRange: (start && end) ? (start + ' - ' + end) : (start || ''),
                                                            status: status,
                                                            raw: rowText.substring(0, 120)
                                                        });
                                                    } else if (existing.status === 'UNMARKED' && status !== 'UNMARKED') {
                                                        existing.status = status;
                                                    }
                                                }

                                                // Pass 1b: check timetable popup cells (openPopup)
                                                var tdCells = doc.querySelectorAll('td[onclick*="openPopup"]');
                                                for (var t = 0; t < tdCells.length; t++) {
                                                    var td = tdCells[t];
                                                    var onclick = td.getAttribute('onclick') || '';
                                                    var popupMatch = onclick.match(/openPopup\([^,]*,\s*"([^"]*)"[^,]*,\s*"([^"]*)"[^,]*,\s*"([^"]*)"[^,]*,\s*"([^"]*)"/);
                                                    if (!popupMatch) continue;
                                                    var timeR = popupMatch[1];
                                                    var cCode = popupMatch[2].toUpperCase();
                                                    var startT = timeR.split('-')[0].trim();
                                                    var tdStyle = (td.getAttribute('style') || '').toLowerCase();
                                                    var tdBg = (td.getAttribute('bgcolor') || '').toLowerCase();
                                                    var tdCls = (td.className || '').toLowerCase();
                                                    var tdText = getText(td).toLowerCase();

                                                    var cellStatus = null;
                                                    if (tdStyle.includes('green') || tdBg.includes('green') || tdCls.includes('present') || tdCls.includes('success')) {
                                                        cellStatus = 'PRESENT';
                                                    } else if (tdStyle.includes('red') || tdBg.includes('red') || tdCls.includes('absent') || tdCls.includes('danger')) {
                                                        cellStatus = 'ABSENT';
                                                    } else if (/\bpresent\b/.test(tdText)) {
                                                        cellStatus = 'PRESENT';
                                                    } else if (/\babsent\b/.test(tdText)) {
                                                        cellStatus = 'ABSENT';
                                                    }

                                                    if (cellStatus) {
                                                        var ex = todayStatus.find(function(x) { return x.courseCode === cCode && (!startT || x.start === startT); });
                                                        if (ex) {
                                                            if (ex.status === 'UNMARKED') ex.status = cellStatus;
                                                        } else {
                                                            todayStatus.push({
                                                                courseCode: cCode,
                                                                start: startT,
                                                                timeRange: timeR,
                                                                status: cellStatus,
                                                                raw: 'cell:' + getText(td).substring(0, 60)
                                                            });
                                                        }
                                                    }
                                                }

                                                // Pass 2: Course-wise summary table
                                                for (var i = 0; i < rows.length; i++) {
                                                    var row = rows[i];
                                                    var cells = row.querySelectorAll('td');
                                                    if (cells.length < 3) continue;

                                                    var rowText = getText(row);
                                                    var cMatch = rowText.match(courseCodeRegex);
                                                    if (!cMatch) continue;
                                                    var code = cMatch[1].toUpperCase();

                                                    var percMatch = rowText.match(/(\d+(?:\.\d+)?)\s*%/);
                                                    var percentage = percMatch ? parseFloat(percMatch[1]) : 0;

                                                    var nums = [];
                                                    for (var c = 0; c < cells.length; c++) {
                                                        var val = parseInt(getText(cells[c]), 10);
                                                        if (!isNaN(val) && val >= 0 && val < 500) nums.push(val);
                                                    }

                                                    var attended = 0, delivered = 0, dl = 0;
                                                    if (nums.length >= 2) {
                                                        nums.sort(function(a, b) { return a - b; });
                                                        attended = nums[0];
                                                        delivered = nums[nums.length - 1];
                                                    }

                                                    var existingC = courses.find(function(x) { return x.courseCode === code; });
                                                    if (!existingC) {
                                                        courses.push({
                                                            courseCode: code,
                                                            courseName: '',
                                                            attended: attended,
                                                            delivered: delivered,
                                                            percentage: percentage > 0 ? percentage : (delivered > 0 ? (attended * 100.0 / delivered) : 0),
                                                            dutyLeave: dl
                                                        });
                                                    } else if (percentage > 0 && existingC.percentage === 0) {
                                                        existingC.percentage = percentage;
                                                        existingC.delivered = delivered;
                                                        existingC.attended = attended;
                                                    }
                                                }
                                            }

                                            if (allHrefs.length > 0) {
                                                debug.push('Hrefs=' + allHrefs.join(','));
                                            }
                                            debug.push('statusCnt=' + todayStatus.length + ' crsCnt=' + courses.length);
                                            window._skedAttDebug = debug.join(' | ');

                                            return {
                                                todayStatus: todayStatus,
                                                courses: courses
                                            };
                                        }

                                        function fetchAllData(loginHtml, curUser, curPass) {
                                            window._skedStatus = 'Fetching timetable & live attendance...';
                                            var docSources = [];
                                            if (loginHtml) docSources.push({ name: 'login', html: loginHtml });

                                            var uid = curUser || $safeUser;
                                            var pwd = curPass || $safePass;

                                            function safeFetch(url, name) {
                                                return fetch(url, { credentials: 'include' })
                                                    .then(function(r) {
                                                        if (!r.ok) return { name: name, status: r.status, html: '' };
                                                        return r.text().then(function(t) {
                                                            if (t.includes('LoginNew') || t.includes('lockerror') || t.includes('lblError')) {
                                                                return { name: name, status: 401, html: '' };
                                                            }
                                                            return { name: name, status: r.status, html: t };
                                                        });
                                                    })
                                                    .catch(function(e) {
                                                        return { name: name, status: 0, html: '', error: e.toString() };
                                                    });
                                            }

                                            var timetablePromise = safeFetch('/lpuums/frmMyCurrentTimeTable.aspx', 'ttAspx')
                                                .then(function(res) {
                                                    if (res.html) docSources.push(res);
                                                    var m = res.html ? res.html.match(/id=["']Select1["'][^>]*>([\s\S]*?)<\/select>/i) : null;
                                                    var termId = '';
                                                    if (m) {
                                                        var selM = m[1].match(/selected[^>]*value=["']([^"']+)["']/i)
                                                                || m[1].match(/value=["']([^"']+)["'][^>]*selected/i)
                                                                || m[1].match(/value=["']([^"']+)["']/i);
                                                        if (selM) termId = selM[1];
                                                    }
                                                    return fetch('/lpuums/frmMyCurrentTimeTable.aspx/GetTimeTable', {
                                                        method: 'POST',
                                                        credentials: 'include',
                                                        headers: {
                                                            'Content-Type': 'application/json; charset=utf-8',
                                                            'X-Requested-With': 'XMLHttpRequest'
                                                        },
                                                        body: JSON.stringify({ TermId: termId || null })
                                                    }).then(function(r) { return r.text(); });
                                                })
                                                .then(function(json) {
                                                    window._skedTimetableResult = json;
                                                    try {
                                                        var dObj = JSON.parse(json);
                                                        if (dObj && dObj.d) docSources.push({ name: 'GetTimeTable', html: dObj.d });
                                                    } catch (_) {
                                                        if (json) docSources.push({ name: 'GetTimeTableRaw', html: json });
                                                    }
                                                });

                                            var candidateUrls = [
                                                ['/lpuums/StudentDashboard.aspx', 'StudentDash'],
                                                ['/lpuums/Default3.aspx', 'Default3']
                                            ];

                                            var attFetches = candidateUrls.map(function(item) {
                                                return safeFetch(item[0], item[1]).then(function(res) {
                                                    if (res.html) docSources.push(res);
                                                    return res;
                                                });
                                            });

                                            var mobilePromise = (function() {
                                                return new Promise(function(resolve) {
                                                    try {
                                                        if (!uid || !pwd) {
                                                            return resolve({ error: 'Empty credentials' });
                                                        }
                                                        var devId = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
                                                        var body = {
                                                            UserId: uid,
                                                            password: pwd,
                                                            Identity: 'aphone',
                                                            DeviceId: devId,
                                                            PlayerId: 'vbnxvcjhvbvcgghgjhgjhdddddjhgjf'
                                                        };
                                                        var buildInfo = {
                                                            baseUrl: null,
                                                            packageName: 'ums.lovely.university',
                                                            basePackageName: 'ums.lovely.university',
                                                            displayName: 'LPUTouch',
                                                            name: 'App',
                                                            version: '20.87',
                                                            versionCode: '20.87',
                                                            debug: true,
                                                            buildDate: '2025-11-08T08:02:50.000Z',
                                                            installDate: '2025-11-08T08:02:50.000Z',
                                                            buildType: '',
                                                            flavor: ''
                                                        };

                                                        // Standalone AES-CBC encryption using Web Crypto API
                                                        var keyB64 = "m0rDSdPyzt+bo/BuTLgmXssN6TSzRPACdahgiCt5SLs=";
                                                        var rawKey = window.atob(keyB64);
                                                        var keyBytes = new Uint8Array(rawKey.length);
                                                        for (var i = 0; i < rawKey.length; i++) keyBytes[i] = rawKey.charCodeAt(i);

                                                        var iv = window.crypto.getRandomValues(new Uint8Array(16));
                                                        var payload = {
                                                            url: 'milkyway',
                                                            action: 'post',
                                                            data: body,
                                                            guest: buildInfo.packageName,
                                                            guestcount: buildInfo.version
                                                        };
                                                        var dataStr = JSON.stringify(payload);
                                                        var enc = new TextEncoder().encode(dataStr);

                                                        window.crypto.subtle.importKey(
                                                            "raw", keyBytes.buffer, { name: "AES-CBC", length: 256 }, false, ["encrypt"]
                                                        ).then(function(cKey) {
                                                            return window.crypto.subtle.encrypt({ name: "AES-CBC", iv: iv }, cKey, enc);
                                                        }).then(function(encBuf) {
                                                            var encBytes = new Uint8Array(encBuf);
                                                            var encBin = "";
                                                            for (var j = 0; j < encBytes.byteLength; j++) encBin += String.fromCharCode(encBytes[j]);
                                                            var ivBin = "";
                                                            for (var k = 0; k < iv.byteLength; k++) ivBin += String.fromCharCode(iv[k]);

                                                            return fetch('https://ums.lpu.in/umswebservice/umswebservice.svc/PVR', {
                                                                method: 'POST',
                                                                headers: { 'Content-Type': 'application/json' },
                                                                body: JSON.stringify({ v: window.btoa(ivBin), d: window.btoa(encBin) })
                                                            });
                                                        }).then(function(r) { return r.json(); })
                                                        .then(function(pvrData) {
                                                            var pvrList = JSON.parse(pvrData.PVRResult || '[]');
                                                            var token = (pvrList && pvrList[0] && pvrList[0].AccessToken) || '';
                                                            if (!token) {
                                                                var em = (pvrList && pvrList[0] && pvrList[0].MenuText) || 'No token';
                                                                return resolve({ error: em });
                                                            }
                                                            window._skedMobileToken = token;
                                                            var bUrl = 'https://ums.lpu.in/umswebservice/umswebservice.svc/StudentBasicInfoForService/' + encodeURIComponent(uid) + '/' + encodeURIComponent(token) + '/' + encodeURIComponent(devId) + '/null/null';
                                                            var cUrl = 'https://ums.lpu.in/umswebservice/umswebservice.svc/StudentAttendanceForServiceNew/' + encodeURIComponent(uid) + '/' + encodeURIComponent(token) + '/' + encodeURIComponent(devId);
                                                            return Promise.all([
                                                                fetch(bUrl).then(function(r) { return r.json(); }).catch(function(e) { return null; }),
                                                                fetch(cUrl).then(function(r) { return r.json(); }).catch(function(e) { return null; })
                                                            ]).then(function(pair) {
                                                                var basicData = pair[0];
                                                                var coursesData = pair[1] || [];
                                                                var coursesList = Array.isArray(coursesData) ? coursesData : (coursesData && Array.isArray(coursesData.StudentAttendanceForServiceNewResult) ? coursesData.StudentAttendanceForServiceNewResult : (coursesData && Array.isArray(coursesData.StudentAttendanceForServiceResult) ? coursesData.StudentAttendanceForServiceResult : []));
                                                                var detailFetches = [];
                                                                if (Array.isArray(coursesList)) {
                                                                    coursesList.forEach(function(c) {
                                                                        var code = (c.CourseCode || c.courseCode || '').trim();
                                                                        if (code) {
                                                                            var dUrl = 'https://ums.lpu.in/umswebservice/umswebservice.svc/StudentAttendanceDetailForService/' + encodeURIComponent(uid) + '/' + encodeURIComponent(token) + '/' + encodeURIComponent(devId) + '/' + encodeURIComponent(code);
                                                                            detailFetches.push(
                                                                                fetch(dUrl)
                                                                                    .then(function(r) { return r.json(); })
                                                                                    .then(function(data) {
                                                                                        var arr = Array.isArray(data) ? data : (data && Array.isArray(data.StudentAttendanceDetailForServiceResult) ? data.StudentAttendanceDetailForServiceResult : []);
                                                                                        return { courseCode: code, records: arr };
                                                                                    })
                                                                                    .catch(function(e) { return { courseCode: code, records: [] }; })
                                                                            );
                                                                        }
                                                                    });
                                                                }
                                                                return Promise.all(detailFetches).then(function(detailsList) {
                                                                    resolve({
                                                                        userId: uid,
                                                                        token: token,
                                                                        deviceId: devId,
                                                                        basic: basicData,
                                                                        courses: coursesList,
                                                                        details: detailsList
                                                                    });
                                                                });
                                                            });
                                                        }).catch(function(e) {
                                                            resolve({ error: e.toString() });
                                                        });
                                                    } catch (e) {
                                                        resolve({ error: e.toString() });
                                                    }
                                                });
                                            })();

                                            return Promise.all([timetablePromise, Promise.all(attFetches), mobilePromise]).then(function(resTriple) {
                                                var parsed = extractAttendance(docSources);
                                                var mobRes = resTriple[2] || null;

                                                if (mobRes && !mobRes.error) {
                                                    window._skedMobileToken = mobRes.token;
                                                    window._skedMobileDeviceId = mobRes.deviceId;
                                                    window._skedMobileUserId = mobRes.userId;

                                                    var basic = mobRes.basic;
                                                    var basicList = Array.isArray(basic) ? basic : (basic && Array.isArray(basic.StudentBasicInfoForServiceResult) ? basic.StudentBasicInfoForServiceResult : []);
                                                    if (Array.isArray(basicList) && basicList[0] && Array.isArray(basicList[0].TimeTable)) {
                                                        var mobToday = [];
                                                        basicList[0].TimeTable.forEach(function(tt) {
                                                            var at = (tt.AttendanceType || '').toLowerCase();
                                                            var st = 'UNMARKED';
                                                            if (at.includes('present')) st = 'PRESENT';
                                                            else if (at.includes('absent')) st = 'ABSENT';
                                                            else if (at.includes('duty') || at.includes('leave')) st = 'DUTY_LEAVE';

                                                            var timeStr = tt.AttendanceTime || '';
                                                            var start = timeStr.split('-')[0].trim().replace(/\s+/g, '');
                                                            if (start.length > 5) start = start.substring(0, 5);

                                                            mobToday.push({
                                                                courseCode: (tt.CourseCode || tt.courseCode || '').toUpperCase().trim(),
                                                                start: start,
                                                                timeRange: timeStr,
                                                                status: st,
                                                                raw: 'mobile:' + JSON.stringify(tt)
                                                            });
                                                        });
                                                        if (mobToday.length > 0) {
                                                            parsed.todayStatus = mobToday;
                                                        }
                                                    }

                                                    var coursesData = mobRes.courses;
                                                    var coursesList = Array.isArray(coursesData) ? coursesData : (coursesData && Array.isArray(coursesData.StudentAttendanceForServiceNewResult) ? coursesData.StudentAttendanceForServiceNewResult : (coursesData && Array.isArray(coursesData.StudentAttendanceForServiceResult) ? coursesData.StudentAttendanceForServiceResult : []));
                                                    if (Array.isArray(coursesList) && coursesList.length > 0) {
                                                        var mobCourses = [];
                                                        coursesList.forEach(function(c) {
                                                            var code = (c.CourseCode || c.courseCode || '').toUpperCase().trim();
                                                            if (!code) return;
                                                            var attd = parseInt(c.Total_Attd || c.attended || '0', 10);
                                                            var delv = parseInt(c.Total_Delv || c.delivered || '0', 10);
                                                            var perc = parseFloat(c.Total_Perc || c.percentage || '0');
                                                            var dl = parseInt(c.DutyLeave || c.dutyLeave || '0', 10);
                                                            mobCourses.push({
                                                                courseCode: code,
                                                                courseName: c.CourseName || c.courseName || '',
                                                                attended: attd,
                                                                delivered: delv,
                                                                percentage: perc,
                                                                dutyLeave: dl
                                                            });
                                                        });
                                                        if (mobCourses.length > 0) {
                                                            parsed.courses = mobCourses;
                                                        }
                                                    }

                                                    if (Array.isArray(mobRes.details)) {
                                                        parsed.courseDetails = mobRes.details;
                                                    }
                                                }

                                                window._skedAttendanceResult = JSON.stringify(parsed);
                                                window._skedAttDebug = (mobRes && mobRes.token ? 'MobileToken:OK' : (mobRes && mobRes.error ? 'MobErr:' + mobRes.error : 'NoMob')) + ' | statusCnt=' + parsed.todayStatus.length + ' crsCnt=' + parsed.courses.length + ' dtlCnt=' + (parsed.courseDetails ? parsed.courseDetails.length : 0);
                                            }).catch(function(e) {
                                                var parsed = extractAttendance(docSources);
                                                window._skedAttendanceResult = JSON.stringify(parsed);
                                                window._skedAttDebug = 'Err:' + e.toString();
                                            });
                                        }

                                        fetch(form ? form.action : window.location.href, {
                                            method: 'POST',
                                            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                            body: params.toString()
                                        }).then(function(r) {
                                            return r.text();
                                        }).then(function(html) {
                                            if (html.includes('StudentDashboard') || html.includes('frmMyCurrentTimeTable') || html.includes('w-schedule') || html.includes('Default3')) {
                                                return fetchAllData(html, curU.value, curP.value);
                                            } else if (html.includes('This App is not trusted')) {
                                                window._skedIsSyncing = false;
                                                window._skedError = 'LPU server rejected request. Retrying...';
                                            } else {
                                                window._skedIsSyncing = false;
                                                var errM = html.match(/id=["']lockerror["'][^>]*>([^<]+)<\/span>/i)
                                                        || html.match(/id=["']lblError["'][^>]*>([^<]+)<\/span>/i);
                                                window._skedError = errM ? errM[1] : 'Invalid credentials. Please verify ID and password.';
                                            }
                                        }).catch(function(err) {
                                            window._skedIsSyncing = false;
                                            window._skedError = 'Network error: ' + err.toString();
                                        });
                                    }

                                    window.skedForceSubmit = triggerSubmit;

                                    if (form && !form.__skedBound) {
                                        form.__skedBound = true;
                                        form.onsubmit = function(e) {
                                            if (e) { e.preventDefault(); e.stopPropagation(); }
                                            triggerSubmit();
                                            return false;
                                        };
                                        var submitBtn = form.querySelector('input[type="submit"]');
                                        if (submitBtn) {
                                            submitBtn.onclick = function(e) {
                                                if (e) { e.preventDefault(); e.stopPropagation(); }
                                                triggerSubmit();
                                                return false;
                                            };
                                        }
                                    }

                                    if (!window._skedIsSyncing && !window._skedTimetableResult) {
                                        if (window.location.href.includes('Default3') || window.location.href.includes('StudentDashboard') || window.location.href.includes('frmMyCurrentTimeTable')) {
                                            window._skedIsSyncing = true;
                                            fetchAllData(document.documentElement.outerHTML, $safeUser, $safePass);
                                        } else if (cf && cf.value && cf.value.length > 20 && u && u.value && p && p.value) {
                                            triggerSubmit();
                                        }
                                    }

                                    var st = 'Waiting for Turnstile verification...';
                                    if (cf && cf.value && cf.value.length > 20) st = 'Turnstile verified! Submitting...';

                                    return JSON.stringify({
                                        status: window._skedError || st,
                                        error: window._skedError || '',
                                        syncing: !!window._skedIsSyncing,
                                        hasResult: !!window._skedTimetableResult
                                    });
                                } catch(e) {
                                    return JSON.stringify({ error: e.toString() });
                                }
                            })()
                        """.trimIndent()

                        wv.evaluateJavascript(tickJs) { stateRaw ->
                            if (!stateRaw.isNullOrBlank() && stateRaw != "\"\"" && stateRaw != "null") {
                                try {
                                    val unquoted = if (stateRaw.startsWith("\"") && stateRaw.endsWith("\"")) {
                                        JSONObject("{\"v\":$stateRaw}").getString("v")
                                    } else stateRaw
                                    val stateObj = JSONObject(unquoted)
                                    val st = stateObj.optString("status")
                                    val err = stateObj.optString("error")
                                    val syncing = stateObj.optBoolean("syncing")
                                    val hasRes = stateObj.optBoolean("hasResult")

                                    if (st.isNotBlank()) statusText = st
                                    isSyncing = syncing
                                    if (err.isNotBlank()) statusText = err

                                    if (hasRes && !isDone) {
                                        isDone = true
                                        statusText = "Parsing timetable & live attendance..."
                                        isSyncing = true
                                        wv.evaluateJavascript("window._skedTimetableResult") { ttRaw ->
                                            wv.evaluateJavascript("window._skedAttendanceResult") { attRaw ->
                                                wv.evaluateJavascript("window._skedAttDebug || ''") { debugRaw ->
                                                    wv.evaluateJavascript("window._skedMobileToken || ''") { tokenRaw ->
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val entries = if (!ttRaw.isNullOrBlank()) TimetableParser.parse(ttRaw) else emptyList()
                                                        if (entries.isNotEmpty()) {
                                                            TimetableParser.saveToPrefs(context, entries, userId)
                                                        }

                                                        var markedCount = 0
                                                        if (!attRaw.isNullOrBlank() && attRaw != "null" && attRaw != "\"\"") {
                                                            val unquotedAtt = if (attRaw.startsWith("\"") && attRaw.endsWith("\"")) {
                                                                try { JSONObject("{\"v\":$attRaw}").getString("v") } catch (_: Exception) { attRaw }
                                                            } else attRaw
                                                            markedCount = AttendanceManager.saveLiveAttendanceBatch(context, unquotedAtt)
                                                        }

                                                        val tokenClean = if (!tokenRaw.isNullOrBlank() && tokenRaw != "null" && tokenRaw != "\"\"") {
                                                            try {
                                                                if (tokenRaw.startsWith("\"")) JSONObject("{\"v\":$tokenRaw}").getString("v") else tokenRaw
                                                            } catch (_: Exception) { tokenRaw }
                                                        } else ""

                                                        android.util.Log.e("SkedSync", "=== SYNC RESULT ===")
                                                        android.util.Log.e("SkedSync", "ttRaw length: ${ttRaw?.length}")
                                                        android.util.Log.e("SkedSync", "attRaw: $attRaw")
                                                        android.util.Log.e("SkedSync", "debugRaw: $debugRaw")
                                                        android.util.Log.e("SkedSync", "tokenClean: ${if (tokenClean.isNotBlank()) "OK" else "NONE"}")
                                                        android.util.Log.e("SkedSync", "markedCount: $markedCount")

                                                        val prefsEditor = context.getSharedPreferences("sked_attendance_prefs", Context.MODE_PRIVATE).edit()
                                                        prefsEditor.putString("last_sync_debug", debugRaw ?: "")
                                                        prefsEditor.putString("last_att_raw", attRaw ?: "")
                                                        if (tokenClean.isNotBlank()) {
                                                            prefsEditor.putString("mobile_token", tokenClean)
                                                            prefsEditor.putString("mobile_user_id", userId)
                                                            prefsEditor.putString("mobile_device_id", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                                        }
                                                        prefsEditor.apply()

                                                        try {
                                                            val manager = GlanceAppWidgetManager(context)
                                                            val ids = manager.getGlanceIds(TimetableWidget::class.java)
                                                            ids.forEach { id ->
                                                                TimetableWidget().update(context, id)
                                                            }
                                                        } catch (_: Exception) {}
                                                        TimetableRefreshWorker.runNow(context)

                                                        withContext(Dispatchers.Main) {
                                                            if (entries.isNotEmpty()) {
                                                                val debugInfo = try {
                                                                    val dq = if (!debugRaw.isNullOrBlank() && debugRaw != "null" && debugRaw != "\"\"") {
                                                                        if (debugRaw.startsWith("\"")) JSONObject("{\"v\":$debugRaw}").getString("v") else debugRaw
                                                                    } else ""
                                                                    dq
                                                                } catch (_: Exception) { "" }
                                                                val msg = if (markedCount > 0) {
                                                                    "Synced timetable & $markedCount live attendance records"
                                                                } else {
                                                                    "Synced timetable (0 attendance records${if (debugInfo.isNotBlank()) " · $debugInfo" else ""})"
                                                                }
                                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                                onSuccess(userId)
                                                            } else {
                                                                isSyncing = false
                                                                statusText = "No classes found in timetable"
                                                                Toast.makeText(context, "Found 0 classes in timetable", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            isSyncing = false
                                                            statusText = "Parse error: ${e.message}"
                                                            Toast.makeText(context, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                                val isSupp = WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
                                if (isSupp) {
                                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        if (newProgress < 100 && !isSyncing) {
                                            statusText = "Loading LPU portal ($newProgress%)..."
                                        } else if (newProgress == 100 && !isSyncing) {
                                            statusText = "Verifying Turnstile..."
                                        }
                                    }
                                }

                                val cachedUa = settings.userAgentString

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        if (url.contains("LoginNew.aspx", ignoreCase = true) && request.method.equals("GET", ignoreCase = true)) {
                                            try {
                                                val okHttp = OkHttpClient.Builder().build()
                                                val cm = CookieManager.getInstance()
                                                val cookies = cm.getCookie("https://ums.lpu.in") ?: ""

                                                val builder = Request.Builder()
                                                    .url(url)
                                                    .get()
                                                    .header("User-Agent", cachedUa)

                                                 if (cookies.isNotBlank()) {
                                                    builder.header("Cookie", cookies)
                                                }

                                                val resp = okHttp.newCall(builder.build()).execute()
                                                val setCookies = resp.headers("Set-Cookie")
                                                for (sc in setCookies) {
                                                    cm.setCookie("https://ums.lpu.in", sc)
                                                }
                                                val mime = resp.header("Content-Type", "text/html")?.split(";")?.firstOrNull()?.trim() ?: "text/html"
                                                val bodyStream = resp.body?.byteStream()
                                                return android.webkit.WebResourceResponse(mime, "utf-8", bodyStream)
                                            } catch (_: Exception) {}
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        try {
                                            val themeJs = ctx.assets.open("theme.js").bufferedReader().use { it.readText() }
                                            view?.evaluateJavascript(themeJs, null)
                                        } catch (_: Exception) {}
                                        if ((url ?: "").contains("LoginNew.aspx", ignoreCase = true) && !isSyncing) {
                                            statusText = "Verifying Turnstile..."
                                        }
                                    }
                                }

                                loadUrl("https://ums.lpu.in/lpuums/LoginNew.aspx")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Helper Time Functions ───────────────────────────────────────────────────

private fun parseTimeInMinutes(timeStr: String): Int {
    val clean = timeStr.trim()
    val parts = clean.split(":")
    if (parts.size >= 2) {
        val h = parts[0].trim().toIntOrNull() ?: 0
        val m = parts[1].trim().take(2).toIntOrNull() ?: 0
        return h * 60 + m
    }
    return 0
}

// ── Celebration — "All done." ─────────────────────────────────────────────────

@Composable
fun CelebrationCard(count: Int) {
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
            Text(
                text = "All done.",
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Chalk
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count classes completed.",
                fontSize = 13.sp,
                color = Slate
            )
        }
    }
}

// ── Skeleton Loader ──────────────────────────────────────────────────────────

@Composable
fun SkeletonClassCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

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
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Rule.copy(alpha = shimmerAlpha))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Rule.copy(alpha = shimmerAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Rule.copy(alpha = shimmerAlpha))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Rule.copy(alpha = shimmerAlpha))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Rule.copy(alpha = shimmerAlpha))
                )
            }
        }
    }
}

// ── Class Timing State & Cards ───────────────────────────────────────────────

enum class ClassTimingState {
    OVER,       // Class time is over and attendance is still not marked -> NOT MKD
    UPCOMING,   // Next upcoming class -> UPCOMING
    PENDING     // Pending future class -> PENDING
}

// ── Staggered Animated Class Item Reveal ────────────────────────────────────

@Composable
fun StaggeredClassCard(
    item: ClassItem,
    index: Int,
    isLive: Boolean = false,
    timingState: ClassTimingState = ClassTimingState.PENDING,
    attendanceStatus: AttendanceStatus = AttendanceStatus.UNMARKED,
    courseAttendance: CourseAttendance? = null,
    onClick: (() -> Unit)? = null
) {
    var visible by remember(item) { mutableStateOf(false) }
    LaunchedEffect(item) {
        kotlinx.coroutines.delay((index * 45).coerceAtMost(320).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
        label = "cardAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "cardOffset"
    )

    Box(
        modifier = Modifier
            .offset(y = offsetY)
            .alpha(alpha)
    ) {
        ClassCard(
            item = item,
            isLive = isLive,
            timingState = timingState,
            attendanceStatus = attendanceStatus,
            courseAttendance = courseAttendance,
            onClick = onClick
        )
    }
}

// ── Class Card — departures-board row, monochrome type, Blaze for active ─────

@Composable
fun ClassCard(
    item: ClassItem,
    isLive: Boolean = false,
    timingState: ClassTimingState = ClassTimingState.PENDING,
    attendanceStatus: AttendanceStatus = AttendanceStatus.UNMARKED,
    courseAttendance: CourseAttendance? = null,
    onClick: (() -> Unit)? = null
) {
    // Monochrome type labels — no multi-colour system
    val tagLabel = when (item.type) {
        "Lecture"   -> "LEC"
        "Practical" -> "PRAC"
        "Tutorial"  -> "TUT"
        else        -> item.type.uppercase().take(3)
    }

    val presentGreen   = Color(0xFF22C55E)
    val absentRed      = Color(0xFFEF4444)
    val dutyLeaveBlue  = Color(0xFF38BDF8)
    val notMarkedGrey  = Color(0xFF71717A)
    val upcomingOrange = Blaze
    val pendingIndigo  = Color(0xFF818CF8)

    // Vertical bar colour inside the app:
    val verticalColor = when (attendanceStatus) {
        AttendanceStatus.PRESENT    -> presentGreen
        AttendanceStatus.ABSENT     -> absentRed
        AttendanceStatus.DUTY_LEAVE -> dutyLeaveBlue
        AttendanceStatus.UNMARKED   -> when (timingState) {
            ClassTimingState.OVER     -> Color(0xFF383838)
            ClassTimingState.UPCOMING -> upcomingOrange
            ClassTimingState.PENDING  -> pendingIndigo.copy(alpha = 0.7f)
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slab,
        border = androidx.compose.foundation.BorderStroke(
            if (isLive) 1.5.dp else 1.dp,
            if (isLive) Blaze.copy(alpha = 0.4f) else Rule
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left vertical bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(verticalColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Top row: Course Code + status/type/attendance tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Course code in Barlow Condensed — the display moment
                    Text(
                        text = item.courseCode,
                        fontFamily = BarlowCondensed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Chalk
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // NOW. status tag for currently ongoing class
                        if (isLive) {
                            Text(
                                text = "NOW.",
                                fontSize = 12.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Blaze,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Monochrome type pill
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rule)
                        ) {
                            Text(
                                text = tagLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Pill: PRESENT (Green), ABSENT (Red), DUTY LV (Blue), NOT MKD (Slate), UPCOMING (Orange), PENDING (Indigo)
                        val statusText = when (attendanceStatus) {
                            AttendanceStatus.PRESENT    -> "PRESENT"
                            AttendanceStatus.ABSENT     -> "ABSENT"
                            AttendanceStatus.DUTY_LEAVE -> "DUTY LV"
                            AttendanceStatus.UNMARKED   -> when (timingState) {
                                ClassTimingState.OVER     -> "NOT MKD"
                                ClassTimingState.UPCOMING -> "UPCOMING"
                                ClassTimingState.PENDING  -> "PENDING"
                            }
                        }
                        val statusColor = when (attendanceStatus) {
                            AttendanceStatus.PRESENT    -> presentGreen
                            AttendanceStatus.ABSENT     -> absentRed
                            AttendanceStatus.DUTY_LEAVE -> dutyLeaveBlue
                            AttendanceStatus.UNMARKED   -> when (timingState) {
                                ClassTimingState.OVER     -> notMarkedGrey
                                ClassTimingState.UPCOMING -> upcomingOrange
                                ClassTimingState.PENDING  -> pendingIndigo
                            }
                        }
                        val statusBorder = when (attendanceStatus) {
                            AttendanceStatus.PRESENT    -> presentGreen
                            AttendanceStatus.ABSENT     -> absentRed
                            AttendanceStatus.DUTY_LEAVE -> dutyLeaveBlue
                            AttendanceStatus.UNMARKED   -> when (timingState) {
                                ClassTimingState.OVER     -> Color(0xFF3F3F46)
                                ClassTimingState.UPCOMING -> upcomingOrange
                                ClassTimingState.PENDING  -> pendingIndigo.copy(alpha = 0.6f)
                            }
                        }
                        val statusBg = when (attendanceStatus) {
                            AttendanceStatus.PRESENT    -> presentGreen.copy(alpha = 0.12f)
                            AttendanceStatus.ABSENT     -> absentRed.copy(alpha = 0.12f)
                            AttendanceStatus.DUTY_LEAVE -> dutyLeaveBlue.copy(alpha = 0.12f)
                            AttendanceStatus.UNMARKED   -> when (timingState) {
                                ClassTimingState.OVER     -> Color(0xFF27272A).copy(alpha = 0.40f)
                                ClassTimingState.UPCOMING -> upcomingOrange.copy(alpha = 0.14f)
                                ClassTimingState.PENDING  -> pendingIndigo.copy(alpha = 0.12f)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = statusBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusBorder)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Time + Room + Course Attendance % row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.timeRange.ifEmpty { "${item.start} – ${item.end}" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = if (isLive) Blaze else Slate
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "· ${item.room}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Slate
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (courseAttendance != null && courseAttendance.delivered > 0) {
                            val pct = courseAttendance.percentage
                            val pctColor = if (pct >= 75.0) Color(0xFF22C55E) else Color(0xFFEF4444)
                            Text(
                                text = "${String.format(Locale.US, "%.0f", pct)}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = pctColor
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Details",
                            tint = Slate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Teacher + Section
                if (item.teacher.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.teacher,
                            fontSize = 12.sp,
                            color = Color(0xFF5A5856)
                        )
                        if (item.section.isNotEmpty()) {
                            Text(
                                text = " · Sec ${item.section}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF5A5856)
                            )
                        }
                        if (item.group.isNotEmpty() && !item.group.equals("All", ignoreCase = true)) {
                            Text(
                                text = " · G:${item.group}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF5A5856)
                            )
                        }
                    }
                }
            }
        }
    }
}
