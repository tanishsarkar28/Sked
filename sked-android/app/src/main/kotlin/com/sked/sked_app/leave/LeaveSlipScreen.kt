package com.sked.sked_app.leave

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sked.sked_app.AutoSyncManager
import com.sked.sked_app.BarlowCondensed
import com.sked.sked_app.Blaze
import com.sked.sked_app.Chalk
import com.sked.sked_app.DangerRed
import com.sked.sked_app.Ink
import com.sked.sked_app.Rule
import com.sked.sked_app.Slab
import com.sked.sked_app.SlabElevated
import com.sked.sked_app.Slate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Emerald = Color(0xFF10B981)

private const val UMS_LEAVE_SLIP_URL = "https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx"

enum class LeaveSlipMode {
    DIGITAL_PASS,
    UMS_PORTAL
}

@Composable
fun LeaveSlipScreen(
    onBack: () -> Unit,
    onApplyLeave: (() -> Unit)? = null,
    onReSync: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedMode by remember { mutableStateOf(LeaveSlipMode.DIGITAL_PASS) }

    // Live state from storage
    val profile = remember(context) { LeaveStorage.loadProfile(context) }
    var records by remember { mutableStateOf(LeaveStorage.loadRecords(context)) }
    var isSyncing by remember { mutableStateOf(false) }

    // Live clock for authentic warden verification (seconds tick live)
    var liveTimeText by remember { mutableStateOf("") }
    var liveDateText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        while (true) {
            val now = Date()
            liveTimeText = timeFormat.format(now).uppercase()
            liveDateText = dateFormat.format(now)
            delay(1000)
        }
    }

    // Identify active/most recent leave
    val activeLeave = records.firstOrNull() ?: LeaveRecord(
        leaveId = "5768261",
        leaveType = "Day Leave (Extended)",
        applyDate = "Today",
        leaveDate = "Today 3:00 PM",
        returnDate = "Today 10:45 PM",
        status = "Approved",
        checkOut = "",
        checkIn = ""
    )

    val isCheckedOut = activeLeave.checkOut.isNotBlank() && activeLeave.checkIn.isBlank()
    val isApproved = activeLeave.status.equals("Approved", ignoreCase = true)
    val isCompleted = activeLeave.checkIn.isNotBlank()

    // Barcode value: Leave ID or student UID
    val barcodeValue = activeLeave.leaveId.ifBlank { profile.uid.ifBlank { "6000" } }

    fun refreshData() {
        if (isSyncing) return
        isSyncing = true
        coroutineScope.launch {
            when (val res = LeaveSubmitEngine.fetchLatestData(context)) {
                is LeaveSyncResult.Success -> {
                    records = res.data.records
                    Toast.makeText(context, "Leave slip updated", Toast.LENGTH_SHORT).show()
                }
                is LeaveSyncResult.SessionExpired -> {
                    if (onReSync != null) {
                        Toast.makeText(context, "UMS Session expired. Re-authenticating...", Toast.LENGTH_SHORT).show()
                        onReSync.invoke()
                    } else {
                        Toast.makeText(context, "Session expired. Re-sync from Home.", Toast.LENGTH_SHORT).show()
                    }
                }
                is LeaveSyncResult.Error -> {
                    Toast.makeText(context, "Sync note: ${res.message}", Toast.LENGTH_SHORT).show()
                }
            }
            isSyncing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        // ── Top Bar with Mode Segmented Control ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Chalk
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            text = "HOSTEL LEAVE SLIP",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Blaze,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Check In • Check Out Gate Pass",
                            fontSize = 11.sp,
                            color = Slate
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isSyncing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Blaze,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Slip",
                                tint = Slate
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UMS_LEAVE_SLIP_URL))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in Chrome",
                            tint = Slate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector: [ Digital Gate Pass ]  [ Official UMS Portal ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Slab, RoundedCornerShape(8.dp))
                    .border(1.dp, Rule, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 1: Digital Gate Pass
                val isDigital = selectedMode == LeaveSlipMode.DIGITAL_PASS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDigital) Blaze else Color.Transparent)
                        .clickable { selectedMode = LeaveSlipMode.DIGITAL_PASS },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = if (isDigital) Ink else Slate,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DIGITAL GATE PASS",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDigital) Ink else Chalk,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Tab 2: Official UMS Portal
                val isPortal = selectedMode == LeaveSlipMode.UMS_PORTAL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPortal) Blaze else Color.Transparent)
                        .clickable { selectedMode = LeaveSlipMode.UMS_PORTAL },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isPortal) Ink else Slate,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OFFICIAL UMS SLIP",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isPortal) Ink else Chalk,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Rule, thickness = 1.dp)

        // ── Body Content by Mode ────────────────────────────────────────────
        when (selectedMode) {
            LeaveSlipMode.DIGITAL_PASS -> {
                DigitalPassView(
                    profile = profile,
                    leave = activeLeave,
                    barcodeValue = barcodeValue,
                    liveTimeText = liveTimeText,
                    liveDateText = liveDateText,
                    isCheckedOut = isCheckedOut,
                    isApproved = isApproved,
                    isCompleted = isCompleted,
                    onCopyId = {
                        clipboardManager.setText(AnnotatedString(barcodeValue))
                        Toast.makeText(context, "Leave Code copied: $barcodeValue", Toast.LENGTH_SHORT).show()
                    },
                    onApplyNewLeave = onApplyLeave,
                    onSwitchToPortal = { selectedMode = LeaveSlipMode.UMS_PORTAL }
                )
            }
            LeaveSlipMode.UMS_PORTAL -> {
                UmsPortalWebView(
                    onReSync = onReSync
                )
            }
        }
    }
}

// ── Digital Pass Screen (High-Tech, Authentic Gate Pass) ────────────────────

@Composable
private fun DigitalPassView(
    profile: LeaveProfile,
    leave: LeaveRecord,
    barcodeValue: String,
    liveTimeText: String,
    liveDateText: String,
    isCheckedOut: Boolean,
    isApproved: Boolean,
    isCompleted: Boolean,
    onCopyId: () -> Unit,
    onApplyNewLeave: (() -> Unit)?,
    onSwitchToPortal: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // University Pass Header Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Slab,
            border = BorderStroke(1.dp, Rule),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // University Emblem Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LOVELY PROFESSIONAL UNIVERSITY",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "DEPARTMENT OF RESIDENTIAL SERVICES",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = Slate.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF221105),
                        border = BorderStroke(1.dp, Blaze.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "WARDEN COPY",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Blaze,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Rule, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Status Banner with Live Pulsing Dot
                val statusColor by animateColorAsState(
                    targetValue = when {
                        isCheckedOut -> Blaze
                        isApproved -> Emerald
                        isCompleted -> Slate
                        else -> DangerRed
                    },
                    label = "statusColor"
                )

                val statusLabel = when {
                    isCheckedOut -> "CHECKED OUT • ON LEAVE"
                    isApproved -> "APPROVED • READY FOR GATE"
                    isCompleted -> "CHECKED IN • COMPLETED"
                    else -> "STATUS: ${leave.status.uppercase()}"
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusLabel,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = statusColor,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Ticking Clock (Warden anti-screenshot proof)
                Text(
                    text = liveTimeText.ifBlank { "00:00:00 AM" },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Chalk,
                    letterSpacing = 1.sp
                )
                Text(
                    text = liveDateText.ifBlank { "Today" },
                    fontSize = 12.sp,
                    color = Slate
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── High Contrast Laser Turnstile Barcode (Code 128) ────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Code128Barcode(
                            code = barcodeValue,
                            height = 70.dp,
                            barColor = Color.Black,
                            backgroundColor = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onCopyId() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "* $barcodeValue *",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Present barcode to hostel turnstile laser scanner or security",
                    fontSize = 10.5.sp,
                    color = Slate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Rule, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Student Identity Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Initials Avatar
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Blaze.copy(alpha = 0.15f))
                            .border(1.dp, Blaze.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = profile.name
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .map { it.first() }
                            .joinToString("")
                            .ifBlank { "TS" }

                        Text(
                            text = initials,
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Blaze
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifBlank { "TANISH SARKAR" },
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Chalk
                        )
                        Text(
                            text = "UID: ${profile.uid.ifBlank { "12102838" }} • Room ${profile.roomNo.ifBlank { "415" }}",
                            fontSize = 12.sp,
                            color = Slate
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SlabElevated,
                        border = BorderStroke(1.dp, Rule)
                    ) {
                        Text(
                            text = profile.hostel.ifBlank { "BH-02" },
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Chalk,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Leave Timing Details Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SlabElevated,
                    border = BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PassDetailRow(
                            label = "Leave Type",
                            value = leave.leaveType.ifBlank { "Day Leave (Extended)" },
                            highlight = true
                        )

                        PassDetailRow(
                            label = "Sanction Authority",
                            value = profile.authority.ifBlank { "Warden" }
                        )

                        PassDetailRow(
                            label = "Scheduled Out",
                            value = leave.leaveDate.ifBlank { "Today" }
                        )

                        PassDetailRow(
                            label = "Curfew Return By",
                            value = leave.returnDate.ifBlank { "10:45 PM" },
                            valueColor = Blaze
                        )

                        if (leave.checkOut.isNotBlank()) {
                            PassDetailRow(
                                label = "Gate Check-Out",
                                value = leave.checkOut,
                                valueColor = Emerald
                            )
                        }

                        if (leave.checkIn.isNotBlank()) {
                            PassDetailRow(
                                label = "Gate Check-In",
                                value = leave.checkIn,
                                valueColor = Slate
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Slab,
                border = BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSwitchToPortal() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Slate,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UMS PORTAL VIEW",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Chalk
                        )
                    }
                }
            }

            if (onApplyNewLeave != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Blaze,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyNewLeave() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "APPLY NEW LEAVE",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Ink,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PassDetailRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    valueColor: Color = Chalk
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = Slate
        )
        Text(
            text = value,
            fontFamily = if (highlight) BarlowCondensed else FontFamily.Default,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (highlight) 13.5.sp else 12.sp,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Authenticated UMS Web Portal View (frmHostelLeaveSlipTest.aspx) ────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun UmsPortalWebView(
    onReSync: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Hostel Leave Slip") }
    var isTurnstileBlocked by remember { mutableStateOf(false) }

    fun reloadPortal() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val prefs = context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
        val savedCookies = prefs.getString(AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""
        if (savedCookies.isNotBlank()) {
            for (cookie in savedCookies.split(";")) {
                val trimmed = cookie.trim()
                if (trimmed.isNotBlank()) {
                    cm.setCookie("https://ums.lpu.in", trimmed)
                }
            }
            cm.flush()
        }
        webViewRef?.loadUrl(UMS_LEAVE_SLIP_URL)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef?.stopLoading()
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Portal URL toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slab)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "SSL Secure",
                    tint = Emerald,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { reloadPortal() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = Slate,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { webProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Blaze,
                trackColor = Slab
            )
        } else {
            HorizontalDivider(color = Rule, thickness = 1.dp)
        }

        // Auto-refresh notification banner (90-second cycle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlabElevated)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "UMS auto-refreshes slip every 90 seconds • Official UMS portal",
                fontSize = 10.5.sp,
                color = Slate
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        val prefs = ctx.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
                        val savedCookies = prefs.getString(AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""
                        if (savedCookies.isNotBlank()) {
                            for (cookie in savedCookies.split(";")) {
                                val trimmed = cookie.trim()
                                if (trimmed.isNotBlank()) {
                                    cm.setCookie("https://ums.lpu.in", trimmed)
                                }
                            }
                            cm.flush()
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                webProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false

                                // Check if page redirected to login or turnstile
                                if (url != null && (url.contains("Login.aspx", ignoreCase = true) || url.contains("Default3.aspx", ignoreCase = true))) {
                                    isTurnstileBlocked = true
                                } else {
                                    isTurnstileBlocked = false
                                }

                                // Injected script to ensure mobile readability & ensure barcode rendering
                                val jsInjection = """
                                    (function() {
                                        try {
                                            // Ensure barcode is rendered if missing element
                                            if (document.getElementById('barcode') == null && document.getElementById('ctl00_cphHeading_hfLeaveCode') != null) {
                                                var code = document.getElementById('ctl00_cphHeading_hfLeaveCode').value;
                                                if (code && typeof JsBarcode !== 'undefined') {
                                                    var container = document.getElementById('middle');
                                                    if (container) {
                                                        var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                                                        svg.id = 'barcode';
                                                        container.prepend(svg);
                                                        JsBarcode('#barcode', code, { format: 'CODE128', lineColor: '#000', width: 2, height: 60, displayValue: true, fontSize: 14 });
                                                    }
                                                }
                                            }
                                            // Clean margins for mobile
                                            document.body.style.margin = '4px';
                                            document.body.style.padding = '0';
                                        } catch(e) {}
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(jsInjection, null)
                            }
                        }

                        loadUrl(UMS_LEAVE_SLIP_URL)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Turnstile / Session Expired Overlay
            if (isTurnstileBlocked) {
                Surface(
                    color = Ink.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "UMS Session Refresh Required",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Chalk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your UMS session has expired or requires Turnstile clearance to load the official slip directly.",
                            fontSize = 12.5.sp,
                            color = Slate,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        if (onReSync != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Blaze,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onReSync() }
                            ) {
                                Text(
                                    text = "RE-AUTHENTICATE ON UMS",
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Ink,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
