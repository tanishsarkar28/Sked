package com.sked.sked_app.leave

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.TimeUnit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Emerald = Color(0xFF10B981)
private val Slate = Color(0xFF7A7774)

private const val UMS_LEAVE_SLIP_URL = "https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx"

enum class LeaveSlipMode {
    DIGITAL_PASS,
    UMS_PORTAL
}

/**
 * Parses UMS date format: e.g. "30 Oct 2026 Time - 10:45PM" or standard formats.
 */
fun parseUmsReturnDate(returnDateStr: String): Date? {
    if (returnDateStr.isBlank()) return null
    val clean = returnDateStr.replace("Time -", " ")
        .replace("time -", " ")
        .replace("TIME -", " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    val patterns = listOf(
        "dd MMM yyyy hh:mma",
        "dd MMM yyyy h:mma",
        "dd MMM yyyy hh:mm a",
        "dd MMM yyyy h:mm a",
        "dd MMM yyyy HH:mm",
        "MM/dd/yyyy hh:mm:ss a",
        "MM/dd/yyyy h:mm:ss a",
        "MM/dd/yyyy hh:mm a",
        "MM/dd/yyyy h:mm a",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm"
    )

    for (pattern in patterns) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
            sdf.isLenient = true
            val d = sdf.parse(clean)
            if (d != null) return d
        } catch (_: Exception) {}
    }
    return null
}

/**
 * Returns true if current time is more than bufferMinutes after scheduled return time.
 */
fun isLeaveExpired(returnDateStr: String, bufferMinutes: Int = 10): Boolean {
    val returnDate = parseUmsReturnDate(returnDateStr) ?: return false
    val expiryMillis = returnDate.time + (bufferMinutes * 60 * 1000L)
    return System.currentTimeMillis() > expiryMillis
}

@Composable
fun LeaveSlipScreen(
    onBack: () -> Unit,
    onApplyLeave: (() -> Unit)? = null,
    onReSync: (() -> Unit)? = null,
    onRefreshSession: (((Boolean) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedMode by remember { mutableStateOf(LeaveSlipMode.DIGITAL_PASS) }

    // Live state from storage
    var gatePass by remember { mutableStateOf(LeaveStorage.loadGatePass(context)) }
    val profile = remember(context) { LeaveStorage.loadProfile(context) }
    var records by remember { mutableStateOf(LeaveStorage.loadRecords(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    var isFetchingSlip by remember { mutableStateOf(false) }
    var showSlipBridge by remember { mutableStateOf(false) }

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

    fun triggerFetchSlip() {
        if (isFetchingSlip) return
        isFetchingSlip = true

        fun doFetch(isRetry: Boolean = false) {
            coroutineScope.launch {
                // 1. Fetch gate pass directly from frmHostelLeaveSlipTest.aspx
                val passRes = LeaveSubmitEngine.fetchGatePass(context)
                // 2. Concurrently sync leave records from frmStudentHostelLeaveApplicationTermWise.aspx
                val recRes = LeaveSubmitEngine.fetchLatestData(context)
                if (recRes is LeaveSyncResult.Success) {
                    records = recRes.data.records
                }

                if (passRes.isSuccess) {
                    val pass = passRes.getOrThrow()
                    gatePass = pass
                    isFetchingSlip = false

                    val activeRec = records.firstOrNull { r ->
                        r.status.contains("Approved", ignoreCase = true) && r.checkIn.isBlank()
                    }

                    if (pass.hasActivePass && pass.checkInTime.isBlank()) {
                        Toast.makeText(context, "Active leave slip #${pass.leaveId} fetched", Toast.LENGTH_SHORT).show()
                    } else if (activeRec != null) {
                        Toast.makeText(context, "Active approved leave #${activeRec.leaveId} loaded", Toast.LENGTH_SHORT).show()
                    } else if (!pass.hasActivePass) {
                        Toast.makeText(context, "UMS reports: No active leave slip", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Leave slip fetched (Checked in / completed)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val err = passRes.exceptionOrNull()?.message ?: ""
                    val isSessionExpired = err.contains("session", ignoreCase = true) ||
                            err.contains("login", ignoreCase = true) ||
                            err.contains("re-auth", ignoreCase = true) ||
                            err.contains("expired", ignoreCase = true)

                    if (!isRetry && isSessionExpired && onRefreshSession != null) {
                        Toast.makeText(context, "UMS session expired. Refreshing session...", Toast.LENGTH_SHORT).show()
                        onRefreshSession.invoke { success ->
                            if (success) {
                                val syncedPass = LeaveStorage.loadGatePass(context)
                                if (syncedPass.hasActivePass) {
                                    gatePass = syncedPass
                                    isFetchingSlip = false
                                    Toast.makeText(context, "Leave slip fetched from UMS", Toast.LENGTH_SHORT).show()
                                } else {
                                    doFetch(isRetry = true)
                                }
                            } else {
                                isFetchingSlip = false
                                showSlipBridge = true
                            }
                        }
                    } else if (!isRetry && isSessionExpired && onReSync != null) {
                        isFetchingSlip = false
                        Toast.makeText(context, "UMS session expired. Refreshing session...", Toast.LENGTH_SHORT).show()
                        onReSync.invoke()
                    } else {
                        isFetchingSlip = false
                        val activeRec = records.firstOrNull { r ->
                            r.status.contains("Approved", ignoreCase = true) && r.checkIn.isBlank()
                        }
                        if (activeRec != null) {
                            Toast.makeText(context, "Active approved leave #${activeRec.leaveId} loaded", Toast.LENGTH_SHORT).show()
                        } else {
                            showSlipBridge = true
                        }
                    }
                }
            }
        }

        doFetch(isRetry = false)
    }

    fun refreshData(allowSessionRefresh: Boolean = false) {
        if (isSyncing) return
        isSyncing = true
        coroutineScope.launch {
            // First fetch latest gate pass from frmHostelLeaveSlipTest.aspx
            val passRes = LeaveSubmitEngine.fetchGatePass(context)
            if (passRes.isSuccess) {
                gatePass = passRes.getOrThrow()
            }

            // Also fetch history & balance from frmStudentHostelLeaveApplicationTermWise.aspx
            when (val res = LeaveSubmitEngine.fetchLatestData(context)) {
                is LeaveSyncResult.Success -> {
                    records = res.data.records
                    Toast.makeText(context, "Gate pass updated", Toast.LENGTH_SHORT).show()
                }
                is LeaveSyncResult.SessionExpired -> {
                    if (allowSessionRefresh && onRefreshSession != null) {
                        onRefreshSession.invoke { success ->
                            if (success) {
                                coroutineScope.launch {
                                    val retryPass = LeaveSubmitEngine.fetchGatePass(context)
                                    if (retryPass.isSuccess) gatePass = retryPass.getOrThrow()
                                    val retryData = LeaveSubmitEngine.fetchLatestData(context)
                                    if (retryData is LeaveSyncResult.Success) {
                                        records = retryData.data.records
                                    }
                                }
                            }
                        }
                    }
                }
                is LeaveSyncResult.Error -> {
                    if (passRes.isSuccess) {
                        Toast.makeText(context, "Gate pass refreshed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            isSyncing = false
        }
    }

    // Refresh automatically on launch if cookies exist, silently in background without prompting for re-sync
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
        val cookies = prefs.getString(AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""
        if (cookies.isNotBlank()) {
            refreshData(allowSessionRefresh = false)
        }
    }

    // Identify any active approved leave record in UMS history (not checked in yet)
    val approvedRecord = remember(records) {
        records.firstOrNull { r ->
            if (!r.status.contains("Approved", ignoreCase = true)) return@firstOrNull false
            if (r.checkIn.isNotBlank()) return@firstOrNull false
            // Ignore stale records older than 36 hours past return date
            val retDate = parseUmsReturnDate(r.returnDate)
            if (retDate != null && System.currentTimeMillis() - retDate.time > 36 * 3600 * 1000L) {
                return@firstOrNull false
            }
            true
        }
    }

    // Effective gate pass: use parsed pass if active; otherwise synthesize from approved leave record
    val effectiveGatePass = remember(gatePass, approvedRecord, profile) {
        if (gatePass.hasActivePass && gatePass.leaveId.isNotBlank()) {
            gatePass
        } else if (approvedRecord != null) {
            HostelGatePass(
                universityTitle = "Lovely Professional University",
                passSubtitle = "Hostel Leave Gate Pass",
                leaveType = approvedRecord.leaveType.ifBlank { "Hostel Leave" },
                leaveId = approvedRecord.leaveId,
                leaveCode = if (gatePass.leaveCode.isNotBlank() && gatePass.leaveCode != "6000") gatePass.leaveCode else approvedRecord.leaveId,
                regNo = profile.uid.ifBlank { gatePass.regNo },
                name = profile.name.ifBlank { gatePass.name },
                dateOfApply = approvedRecord.applyDate.ifBlank { gatePass.dateOfApply },
                dateOfLeaving = approvedRecord.leaveDate.ifBlank { gatePass.dateOfLeaving },
                dateOfReturn = approvedRecord.returnDate.ifBlank { gatePass.dateOfReturn },
                checkOutTime = approvedRecord.checkOut.ifBlank { gatePass.checkOutTime },
                checkInTime = approvedRecord.checkIn,
                contactNo = profile.registeredMobile.ifBlank { gatePass.contactNo },
                hostel = profile.hostel.ifBlank { gatePass.hostel },
                roomNo = profile.roomNo.ifBlank { gatePass.roomNo },
                gates = gatePass.gates,
                note = "Note : Do not look back after checkout; please proceed forward only.",
                hasActivePass = true
            )
        } else {
            gatePass
        }
    }

    // Keep cached gate pass in sync if synthesized
    LaunchedEffect(effectiveGatePass) {
        if (effectiveGatePass.hasActivePass && !gatePass.hasActivePass) {
            gatePass = effectiveGatePass
            LeaveStorage.saveGatePass(context, effectiveGatePass)
        }
    }

    val isExpired = isLeaveExpired(effectiveGatePass.dateOfReturn, bufferMinutes = 10)
    val isCheckInDone = effectiveGatePass.checkInTime.isNotBlank()
    val isCheckedOut = effectiveGatePass.checkOutTime.isNotBlank() && !isCheckInDone
    val isCompleted = isCheckInDone
    // Keep slip accessible to scan barcode even if return time passed, until student checks in!
    val shouldDisplaySlip = effectiveGatePass.hasActivePass && !isCheckInDone

    if (showSlipBridge) {
        LeaveSlipFetchBridgeDialog(
            onDismiss = { showSlipBridge = false },
            onSuccess = { fetchedPass ->
                gatePass = fetchedPass
                showSlipBridge = false
                val expired = isLeaveExpired(fetchedPass.dateOfReturn, 10)
                if (fetchedPass.hasActivePass && !expired && fetchedPass.checkInTime.isBlank() && fetchedPass.leaveCode != "6000") {
                    Toast.makeText(context, "Leave slip fetched from UMS", Toast.LENGTH_SHORT).show()
                } else if (!fetchedPass.hasActivePass || fetchedPass.leaveCode == "6000") {
                    Toast.makeText(context, "UMS reports: No active leave slip", Toast.LENGTH_SHORT).show()
                } else if (expired) {
                    Toast.makeText(context, "Leave slip fetched (Expired 10m post-return)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Leave slip fetched (Checked in / completed)", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { err ->
                Toast.makeText(context, "Login failed: $err", Toast.LENGTH_LONG).show()
                showSlipBridge = false
            }
        )
    }

    // Invisible background WebView to auto-extract gate pass from UMS directly
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                visibility = View.GONE
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = LeaveSubmitEngine.getDefaultUserAgent(ctx)

                class HiddenBridge {
                    @JavascriptInterface
                    fun onPassHtml(html: String) {
                        if (html.isBlank()) return
                        val p = LeaveParser.parseGatePass(html)
                        if (p != null && p.hasActivePass) {
                            coroutineScope.launch(Dispatchers.Main) {
                                gatePass = p
                                LeaveStorage.saveGatePass(context, p)
                                if (p.photoUrl.isNotBlank()) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val client = OkHttpClient()
                                            val cookies = LeaveSubmitEngine.getBestUmsCookies(context)
                                            val req = Request.Builder()
                                                .url(p.photoUrl)
                                                .header("Cookie", cookies)
                                                .header("User-Agent", LeaveSubmitEngine.getDefaultUserAgent(context))
                                                .build()
                                            client.newCall(req).execute().use { resp ->
                                                val b = resp.body?.bytes()
                                                if (b != null && b.size > 200) {
                                                    File(context.filesDir, "student_photo.jpg").writeBytes(b)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
                addJavascriptInterface(HiddenBridge(), "HiddenBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (url != null && url.contains("frmHostelLeaveSlipTest.aspx", ignoreCase = true)) {
                            view?.evaluateJavascript("""
                                (function() {
                                    try {
                                        if (window.HiddenBridge) {
                                            window.HiddenBridge.onPassHtml(document.documentElement.outerHTML);
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent(), null)
                        }
                    }
                }
                loadUrl(UMS_LEAVE_SLIP_URL)
            }
        },
        modifier = Modifier.size(1.dp)
    )

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
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "HOSTEL GATE PASS",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Slate,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Prominent [ FETCH SLIP ] button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Blaze,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isFetchingSlip) { triggerFetchSlip() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFetchingSlip) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = Ink,
                                    strokeWidth = 1.8.dp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Ink,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "FETCH SLIP",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Ink,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

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
                    gatePass = effectiveGatePass,
                    liveTimeText = liveTimeText,
                    liveDateText = liveDateText,
                    isCheckedOut = isCheckedOut,
                    isApproved = effectiveGatePass.hasActivePass,
                    isCompleted = isCompleted,
                    isExpired = isExpired,
                    shouldDisplaySlip = shouldDisplaySlip,
                    onFetchSlip = { triggerFetchSlip() },
                    isFetchingSlip = isFetchingSlip,
                    onCopyCode = {
                        val c = effectiveGatePass.leaveCode.ifBlank { effectiveGatePass.leaveId }
                        clipboardManager.setText(AnnotatedString(c))
                        Toast.makeText(context, "Leave Code copied: $c", Toast.LENGTH_SHORT).show()
                    },
                    onApplyNewLeave = onApplyLeave,
                    onSwitchToPortal = { selectedMode = LeaveSlipMode.UMS_PORTAL }
                )
            }
            LeaveSlipMode.UMS_PORTAL -> {
                UmsPortalWebView(
                    onReSync = onReSync,
                    onRefreshSession = onRefreshSession,
                    onPassExtracted = { extracted ->
                        gatePass = extracted
                        LeaveStorage.saveGatePass(context, extracted)
                    }
                )
            }
        }
    }
}

// ── Official University Leave Gate Pass (Exact UMS Paper Layout) ────────────

@Composable
private fun DigitalPassView(
    gatePass: HostelGatePass,
    liveTimeText: String,
    liveDateText: String,
    isCheckedOut: Boolean,
    isApproved: Boolean,
    isCompleted: Boolean,
    isExpired: Boolean,
    shouldDisplaySlip: Boolean,
    onFetchSlip: () -> Unit,
    isFetchingSlip: Boolean,
    onCopyCode: () -> Unit,
    onApplyNewLeave: (() -> Unit)?,
    onSwitchToPortal: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ── EMPTY STATE: NO ACTIVE GATE PASS / EXPIRED ─────────────────────────
    if (!shouldDisplaySlip) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            val statusColor = if (isExpired) DangerRed else Slate
            val statusLabel = when {
                isExpired -> "LEAVE EXPIRED • RETURN TIME PASSED"
                isCompleted -> "CHECKED IN • COMPLETED"
                else -> "NO ACTIVE GATE PASS"
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

            Spacer(modifier = Modifier.height(10.dp))

            // Live Ticking Clock
            Text(
                text = liveTimeText.ifBlank { "00:00:00 AM" },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Chalk,
                letterSpacing = 1.sp
            )
            Text(
                text = liveDateText.ifBlank { "Today" },
                fontSize = 11.5.sp,
                color = Slate
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Large Empty State Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slab,
                border = BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Default.Schedule else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isExpired) DangerRed else Blaze,
                        modifier = Modifier.size(46.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            isExpired -> "Gate Pass Expired"
                            isCompleted -> "Leave Completed"
                            else -> "No Active Gate Pass"
                        },
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Chalk
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isExpired -> "Your leave gate pass expired 10 minutes after scheduled return time (${gatePass.dateOfReturn}). It has been removed from active view."
                            isCompleted -> "You checked in at ${gatePass.checkInTime}. This gate pass is now completed and closed."
                            else -> "There is currently no approved or active hostel gate pass on your UMS portal."
                        },
                        fontSize = 12.5.sp,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Button: [ FETCH SLIP FROM UMS ]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Blaze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isFetchingSlip) { onFetchSlip() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isFetchingSlip) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Ink,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "FETCH SLIP FROM UMS",
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Ink,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    if (onApplyNewLeave != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlabElevated,
                            border = BorderStroke(1.dp, Rule),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onApplyNewLeave() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "APPLY NEW LEAVE",
                                    fontFamily = BarlowCondensed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Chalk,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSwitchToPortal() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "VIEW OFFICIAL UMS SLIP PORTAL",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Slate,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
        return
    }

    // Student profile photo (cached from UMS or fallback to bundled default asset)
    val studentBitmap = remember(gatePass.regNo) {
        try {
            val photoFile = File(context.filesDir, "student_photo.jpg")
            if (photoFile.exists() && photoFile.length() > 200) {
                BitmapFactory.decodeFile(photoFile.absolutePath)
            } else {
                context.assets.open("default_student_photo.jpg").use {
                    BitmapFactory.decodeStream(it)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Banner with Live Pulsing Dot
        val statusColor by animateColorAsState(
            targetValue = when {
                isExpired -> DangerRed
                isCheckedOut -> Blaze
                isApproved -> Emerald
                isCompleted -> Slate
                else -> DangerRed
            },
            label = "statusColor"
        )

        val statusLabel = when {
            isExpired -> "RETURN TIME PASSED • PLEASE CHECK IN AT GATE"
            isCheckedOut -> "CHECKED OUT • ON LEAVE"
            isApproved -> "APPROVED • READY FOR GATE"
            isCompleted -> "CHECKED IN • COMPLETED"
            else -> "NO ACTIVE GATE PASS"
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

        Spacer(modifier = Modifier.height(8.dp))

        // Live Ticking Clock (Warden anti-screenshot proof)
        Text(
            text = liveTimeText.ifBlank { "00:00:00 AM" },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Chalk,
            letterSpacing = 1.sp
        )
        Text(
            text = liveDateText.ifBlank { "Today" },
            fontSize = 11.5.sp,
            color = Slate
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── OFFICIAL UMS HOSTEL LEAVE GATE PASS (WHITE SLIP) ─────────────────
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFF222222)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header Row: University Title + Student Photo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = gatePass.universityTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gatePass.passSubtitle,
                            fontSize = 12.5.sp,
                            color = Color(0xFF222222),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gatePass.leaveType,
                            fontSize = 12.sp,
                            color = Color(0xFF222222),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Student Photo (49px x 56px in official slip)
                    Surface(
                        border = BorderStroke(1.dp, Color(0xFF888888)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .width(52.dp)
                            .height(60.dp)
                    ) {
                        if (studentBitmap != null) {
                            Image(
                                bitmap = studentBitmap.asImageBitmap(),
                                contentDescription = "Student Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFEEEEEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Barcode Container (Code 128 encoding leaveCode e.g. 833168)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val displayCode = gatePass.leaveCode.takeIf { it.isNotBlank() && it != "6000" }
                        ?: gatePass.leaveId.takeIf { it.isNotBlank() }
                        ?: "833168"
                    Code128Barcode(
                        code = displayCode,
                        height = 65.dp,
                        barColor = Color.Black,
                        backgroundColor = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCopyCode() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "* $displayCode *",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Leave Code",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Details Table (3 columns: [Label] [:] [Value])
                Column(modifier = Modifier.fillMaxWidth()) {
                    SlipFieldRow(label = "Leave Id", value = gatePass.leaveId, isBold = true)
                    SlipFieldRow(label = "Leave Code", value = gatePass.leaveCode, isBold = true)
                    SlipFieldRow(label = "VID", value = gatePass.regNo)
                    SlipFieldRow(label = "Name", value = gatePass.name)
                    SlipFieldRow(label = "Date of Apply", value = gatePass.dateOfApply)
                    SlipFieldRow(label = "Date of Leaving", value = gatePass.dateOfLeaving)
                    SlipFieldRow(label = "Date of Return", value = gatePass.dateOfReturn)
                    SlipFieldRow(label = "Checkout Time", value = gatePass.checkOutTime)
                    SlipFieldRow(label = "Checkin Time", value = gatePass.checkInTime)
                    SlipFieldRow(label = "Contact No.", value = gatePass.contactNo)
                    SlipFieldRow(label = "Hostel", value = gatePass.hostel)
                    SlipFieldRow(label = "Room Number", value = gatePass.roomNo)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gate Activation Status Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF555555))
                ) {
                    // Maroon Header matching authentic UMS paper slip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF7A1212))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Gate No.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Activation Status",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    gatePass.gates.forEach { gate ->
                        HorizontalDivider(color = Color(0xFF666666), thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = gate.gateName,
                                fontSize = 12.5.sp,
                                color = Color.Black,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = gate.status.ifBlank { "✔ Activated" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF008000), // Exact green as UMS
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Warning Note
                Text(
                    text = gatePass.note.ifBlank { "Note : Do not look back after checkout; please proceed forward only." },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF0000),
                    modifier = Modifier.fillMaxWidth()
                )
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
                color = Blaze,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isFetchingSlip) { onFetchSlip() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFetchingSlip) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Ink,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Ink,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = "FETCH SLIP",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Ink,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (onApplyNewLeave != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slab,
                    border = BorderStroke(1.dp, Rule),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyNewLeave() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "APPLY LEAVE",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Chalk,
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
private fun SlipFieldRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = Color(0xFF001A80) // Authentic UMS navy blue
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = Color.Black,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(115.dp)
        )
        Text(
            text = ":",
            fontSize = 12.5.sp,
            color = Color.Black,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = value.ifBlank { " " },
            fontSize = 12.5.sp,
            color = if (isBold) Color.Black else valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Authenticated UMS Web Portal View (frmHostelLeaveSlipTest.aspx) ────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun UmsPortalWebView(
    onReSync: (() -> Unit)? = null,
    onRefreshSession: (((Boolean) -> Unit) -> Unit)? = null,
    onPassExtracted: ((HostelGatePass) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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

                        class PortalSlipBridge {
                            @JavascriptInterface
                            fun onSlipHtml(html: String) {
                                if (html.isBlank()) return
                                val p = LeaveParser.parseGatePass(html)
                                if (p != null && p.hasActivePass) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onPassExtracted?.invoke(p)
                                    }
                                }
                            }
                        }
                        addJavascriptInterface(PortalSlipBridge(), "PortalBridge")

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

                                if (url != null && url.contains("frmHostelLeaveSlipTest.aspx", ignoreCase = true)) {
                                    view?.evaluateJavascript("""
                                        (function() {
                                            try {
                                                if (window.PortalBridge) {
                                                    window.PortalBridge.onSlipHtml(document.documentElement.outerHTML);
                                                }
                                            } catch(e) {}
                                        })();
                                    """.trimIndent(), null)
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
                        if (onRefreshSession != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Blaze,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onRefreshSession.invoke { success ->
                                            if (success) {
                                                webViewRef?.reload()
                                            }
                                        }
                                    }
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
                        } else if (onReSync != null) {
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

// ── Dedicated Leave Slip Fetch Bridge Dialog (Logs in & Fetches Slip Only) ───

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeaveSlipFetchBridgeDialog(
    onDismiss: () -> Unit,
    onSuccess: (HostelGatePass) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var userId by remember {
        mutableStateOf(
            prefs.getString("saved_ums_user", "") ?: prefs.getString("user_id", "") ?: ""
        )
    }
    var password by remember {
        mutableStateOf(prefs.getString("saved_ums_pwd", "") ?: "")
    }

    var showPasswordInput by remember {
        mutableStateOf(userId.isBlank() || password.isBlank())
    }
    var inputUser by remember { mutableStateOf(userId) }
    var inputPassword by remember { mutableStateOf(password) }
    var passwordVisible by remember { mutableStateOf(false) }

    var statusText by remember { mutableStateOf("Connecting to UMS...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isDone by remember { mutableStateOf(false) }
    var showTurnstileManual by remember { mutableStateOf(false) }
    var waitTicks by remember { mutableIntStateOf(0) }

    if (showPasswordInput) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Slab,
                border = BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Blaze,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "UMS CREDENTIALS NEEDED",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Chalk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter your Registration Number and Password to log in and fetch your leave slip:",
                        fontSize = 12.sp,
                        color = Slate,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputUser,
                        onValueChange = { inputUser = it },
                        label = { Text("Registration No") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blaze,
                            unfocusedBorderColor = Rule,
                            focusedTextColor = Chalk,
                            unfocusedTextColor = Chalk,
                            focusedLabelColor = Blaze,
                            unfocusedLabelColor = Slate
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("UMS Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                            unfocusedLabelColor = Slate
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlabElevated,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("CANCEL", color = Slate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Blaze,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (inputUser.isNotBlank() && inputPassword.isNotBlank()) {
                                        prefs.edit()
                                            .putString("saved_ums_user", inputUser.trim())
                                            .putString("saved_ums_pwd", inputPassword.trim())
                                            .apply()
                                        userId = inputUser.trim()
                                        password = inputPassword.trim()
                                        showPasswordInput = false
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("FETCH SLIP", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ── Dedicated Bridge Dialog (Runs full layout behind clean loading card) ──
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink),
            contentAlignment = Alignment.Center
        ) {
            // 1. AndroidView WebView (Full layout size so Cloudflare Turnstile auto-solves in ~1.5s)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }

                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        // Prime CookieManager with saved cookies
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

                        class SlipJsBridge {
                            @JavascriptInterface
                            fun onSlipHtml(html: String) {
                                if (isDone) return
                                isDone = true
                                coroutineScope.launch(Dispatchers.Main) {
                                    statusText = "Slip received! Parsing..."
                                    cm.flush()
                                    val freshCookies = cm.getCookie("https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: cm.getCookie("https://ums.lpu.in/lpuums")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: cm.getCookie("https://ums.lpu.in")
                                        ?: ""
                                    if (freshCookies.isNotBlank()) {
                                        prefs.edit().putString(AutoSyncManager.KEY_UMS_COOKIES, freshCookies).apply()
                                    }
                                    val parsed = LeaveParser.parseGatePass(html)
                                    val finalPass = parsed ?: HostelGatePass(
                                        universityTitle = "Lovely Professional University",
                                        passSubtitle = "Hostel Leave Gate Pass",
                                        hasActivePass = false
                                    )
                                    LeaveStorage.saveGatePass(context, finalPass)
                                    if (finalPass.hasActivePass) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val client = OkHttpClient()
                                                val photoReq = Request.Builder()
                                                    .url("https://ums.lpu.in/lpuums/DisplayImageForPowerBi.aspx")
                                                    .header("Cookie", freshCookies)
                                                    .build()
                                                client.newCall(photoReq).execute().use { photoResp ->
                                                    val bytes = photoResp.body?.bytes()
                                                    if (bytes != null && bytes.size > 200) {
                                                        File(context.filesDir, "student_photo.jpg").writeBytes(bytes)
                                                    }
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    onSuccess(finalPass)
                                }
                            }

                            @JavascriptInterface
                            fun onStatus(msg: String) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    statusText = msg
                                    if (msg.contains("verified", ignoreCase = true) || msg.contains("Submitting", ignoreCase = true)) {
                                        showTurnstileManual = false
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onAuthError(err: String) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    statusText = "Login error: $err"
                                    onError(err)
                                }
                            }
                        }

                        addJavascriptInterface(SlipJsBridge(), "SlipBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null
                                if (url.contains("LoginNew.aspx", ignoreCase = true) && request.method.equals("GET", ignoreCase = true)) {
                                    try {
                                        val cookies = cm.getCookie("https://ums.lpu.in") ?: ""
                                        val client = OkHttpClient.Builder()
                                            .connectTimeout(12, TimeUnit.SECONDS)
                                            .readTimeout(12, TimeUnit.SECONDS)
                                            .followRedirects(true)
                                            .build()
                                        val builder = Request.Builder()
                                            .url(url)
                                            .get()
                                            .header("User-Agent", LeaveSubmitEngine.getDefaultUserAgent(context))
                                        if (cookies.isNotBlank()) {
                                            builder.header("Cookie", cookies)
                                        }
                                        val resp = client.newCall(builder.build()).execute()
                                        val (mime, bodyBytes) = resp.use { r ->
                                            val setCookies = r.headers("Set-Cookie")
                                            for (sc in setCookies) {
                                                cm.setCookie("https://ums.lpu.in", sc)
                                            }
                                            val m = r.header("Content-Type", "text/html")?.split(";")?.firstOrNull()?.trim() ?: "text/html"
                                            Pair(m, r.body?.bytes())
                                        }
                                        if (bodyBytes != null) {
                                            return WebResourceResponse(mime, "utf-8", java.io.ByteArrayInputStream(bodyBytes))
                                        }
                                    } catch (_: Exception) {}
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val curUrl = url ?: ""
                                if (curUrl.contains("frmHostelLeaveSlipTest.aspx", ignoreCase = true)) {
                                    statusText = "Hostel slip reached! Extracting pass..."
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            try {
                                                if (window.SlipBridge) {
                                                    window.SlipBridge.onSlipHtml(document.documentElement.outerHTML);
                                                }
                                            } catch(e) {
                                                if (window.SlipBridge) window.SlipBridge.onAuthError(e.toString());
                                            }
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                } else if (!curUrl.contains("Login", ignoreCase = true) && !curUrl.contains("about:blank", ignoreCase = true)) {
                                    statusText = "Authenticated! Opening hostel leave slip..."
                                    view?.loadUrl("https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx")
                                } else if (curUrl.contains("Login", ignoreCase = true)) {
                                    statusText = "Authenticating with UMS..."
                                }
                            }
                        }

                        loadUrl("https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx")
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Foreground UI: Sleek compact loading card OR Manual Turnstile Check header
            if (!showTurnstileManual) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink)
                        .clickable(enabled = false) {}, // absorb touches
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Slab,
                        border = BorderStroke(1.dp, Rule),
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 340.dp)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Blaze,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "FETCHING LEAVE SLIP",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Chalk,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = Slate,
                                textAlign = TextAlign.Center
                            )
                            if (waitTicks >= 8) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(onClick = { showTurnstileManual = true }) {
                                    Text(
                                        text = "Taking longer? Tap to verify",
                                        color = Blaze,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlabElevated,
                                border = BorderStroke(0.5.dp, Rule),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onDismiss() }
                            ) {
                                Text(
                                    text = "CANCEL",
                                    color = Slate,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                    color = Slab,
                    border = BorderStroke(1.dp, Rule)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VERIFY HUMAN CHECK",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Chalk
                            )
                            Text(
                                text = "Tap the Cloudflare checkbox below to finish",
                                fontSize = 11.sp,
                                color = Blaze
                            )
                        }
                        TextButton(onClick = { showTurnstileManual = false }) {
                            Text("HIDE", color = Slate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Continuous polling / form auto-submission
            LaunchedEffect(webViewRef, userId, password) {
                val wv = webViewRef ?: return@LaunchedEffect
                val safeUser = JSONObject.quote(userId)
                val safePass = JSONObject.quote(password)

                waitTicks = 0
                while (!isDone) {
                    delay(700)
                    waitTicks++
                    if (waitTicks >= 50) { // ~35 second safety timeout
                        if (!isDone) {
                            isDone = true
                            onError("Leave slip fetch timed out. Please check your network connection.")
                        }
                        break
                    }

                    val autoFillJs = """
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

                                if (window._skedSubmitting) return;

                                if (cf && cf.value && cf.value.length > 0) {
                                    window._skedSubmitting = true;
                                    if (window.SlipBridge) window.SlipBridge.onStatus('Security verified! Submitting login...');
                                    var submitBtn = form ? form.querySelector('input[type="submit"]') : null;
                                    if (submitBtn) {
                                        submitBtn.click();
                                    } else if (form) {
                                        form.submit();
                                    }
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    wv.evaluateJavascript(autoFillJs, null)
                }
            }
        }
    }
}

