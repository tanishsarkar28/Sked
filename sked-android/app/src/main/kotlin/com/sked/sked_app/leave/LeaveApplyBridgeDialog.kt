package com.sked.sked_app.leave

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sked.sked_app.AutoSyncManager
import com.sked.sked_app.BarlowCondensed
import com.sked.sked_app.Blaze
import com.sked.sked_app.Chalk
import com.sked.sked_app.Ink
import com.sked.sked_app.Rule
import com.sked.sked_app.Slab
import com.sked.sked_app.SlabElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val Slate = Color(0xFF7A7774)
private const val LEAVE_PORTAL_URL = "https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx"

/**
 * Dedicated Leave Application Bridge Dialog.
 *
 * Logs directly into UMS (solving Cloudflare Turnstile if needed),
 * navigates straight to the hostel leave application portal,
 * populates all form details (departure, return, address, mobile, reason, declaration),
 * submits the application, intercepts the new Leave ID, and updates the local state.
 *
 * Completely decoupled from full timetable/attendance re-sync.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeaveApplyBridgeDialog(
    onDismiss: () -> Unit,
    selectedType: LeaveTypeOption,
    selectedPlace: VisitPlaceOption,
    stayAddress: String,
    relativeMobile: String,
    startCal: Calendar,
    endCal: Calendar,
    reason: String,
    onSuccess: (leaveId: String) -> Unit,
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

    var statusText by remember { mutableStateOf("Connecting to UMS hostel portal...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isDone by remember { mutableStateOf(false) }
    var showTurnstileManual by remember { mutableStateOf(false) }
    var waitTicks by remember { mutableIntStateOf(0) }

    // ── Credential Input Dialog (If not saved yet) ─────────────────────────
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
                        text = "Enter your Registration Number and Password to log in and apply for leave directly:",
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
                                Text("APPLY LEAVE", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ── Dedicated Bridge Dialog (Invisible WebView with sleek compact loading card) ──
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            // 1. AndroidView WebView (Runs completely hidden in background)
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

                        var initialTopLeaveId: String? = null
                        var stage = 0 // 0: initial load, 1: leaveType set postback, 2: submitted
                        var capturedAlert: String? = null

                        fun isSuccessAlert(msg: String): Boolean {
                            val lower = msg.lowercase()
                            if (lower.contains("already applied") ||
                                lower.contains("already exist") ||
                                lower.contains("still pending") ||
                                lower.contains("cannot") ||
                                lower.contains("not allow") ||
                                lower.contains("reject") ||
                                lower.contains("fail") ||
                                lower.contains("error") ||
                                lower.contains("invalid") ||
                                lower.contains("exceed") ||
                                lower.contains("exhaust") ||
                                lower.contains("not permissible") ||
                                lower.contains("curfew")
                            ) {
                                return false
                            }
                            return lower.contains("success") ||
                                    (lower.contains("applied") && !lower.contains("already")) ||
                                    lower.contains("approved")
                        }

                        fun extractLeaveId(msg: String): String {
                            val regex = "\\b\\d{5,8}\\b".toRegex()
                            val match = regex.find(msg)
                            return match?.value ?: msg
                        }

                        fun populateAndSubmitForm(wv: WebView) {
                            if (stage >= 2) return
                            stage = 2
                            statusText = "Configuring leave details & dates..."
                            executeFormFillAndSubmit(
                                wv = wv,
                                visitPlace = selectedPlace.code,
                                stayAddress = stayAddress,
                                relativeMobile = relativeMobile,
                                startCal = startCal,
                                endCal = endCal,
                                reason = reason
                            )
                        }

                        class LeaveBridge {
                            @JavascriptInterface
                            fun onInitialTopLeaveId(id: String) {
                                if (initialTopLeaveId == null && id.isNotBlank()) {
                                    initialTopLeaveId = id.trim()
                                    android.util.Log.i("SkedLeaveBridge", "Recorded initial leave ID: '$initialTopLeaveId'")
                                }
                            }

                            @JavascriptInterface
                            fun onLeaveTypeReady() {
                                coroutineScope.launch(Dispatchers.Main) {
                                    populateAndSubmitForm(this@apply)
                                }
                            }

                            @JavascriptInterface
                            fun onAlert(msg: String) {
                                capturedAlert = msg
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (stage >= 1) {
                                        if (isSuccessAlert(msg)) {
                                            if (!isDone) {
                                                isDone = true
                                                val cleanId = extractLeaveId(msg)
                                                saveFreshCookies(cm, prefs)
                                                onSuccess(cleanId)
                                            }
                                        } else {
                                            if (!isDone) {
                                                isDone = true
                                                onError(msg)
                                            }
                                        }
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onError(err: String) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (!isDone) {
                                        isDone = true
                                        onError(err)
                                    }
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
                        }

                        addJavascriptInterface(LeaveBridge(), "LeaveBridge")

                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                val msg = message ?: "Unknown alert"
                                capturedAlert = msg
                                result?.confirm()
                                if (stage >= 1) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        if (isSuccessAlert(msg)) {
                                            if (!isDone) {
                                                isDone = true
                                                val cleanId = extractLeaveId(msg)
                                                saveFreshCookies(cm, prefs)
                                                onSuccess(cleanId)
                                            }
                                        } else {
                                            if (!isDone) {
                                                isDone = true
                                                onError(msg)
                                            }
                                        }
                                    }
                                }
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                result?.confirm()
                                return true
                            }
                        }

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
                                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
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
                                if (curUrl.contains("Login", ignoreCase = true)) {
                                    statusText = "Authenticating with UMS..."
                                } else if (curUrl.contains("StudentDashboard", ignoreCase = true) ||
                                    curUrl.contains("Default3", ignoreCase = true) ||
                                    curUrl.contains("w-schedule", ignoreCase = true)
                                ) {
                                    statusText = "Authenticated! Opening hostel portal..."
                                    saveFreshCookies(cm, prefs)
                                    view?.loadUrl(LEAVE_PORTAL_URL)
                                } else if (curUrl.contains("frmStudentHostelLeaveApplicationTermWise", ignoreCase = true)) {
                                    saveFreshCookies(cm, prefs)
                                    when (stage) {
                                        0 -> {
                                            // Step 1: Capture initial top leave ID & set leave type
                                            statusText = "Setting leave type..."
                                            stage = 1
                                            val jsSetType = """
                                                (function() {
                                                    try {
                                                        window.alert = function(msg) { window.LeaveBridge.onAlert(msg); };
                                                        window.confirm = function() { return true; };

                                                        try {
                                                            var fr = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                                            if (fr && fr.cells[0]) {
                                                                window.LeaveBridge.onInitialTopLeaveId(fr.cells[0].innerText.trim());
                                                            }
                                                        } catch(e) {}

                                                        var sel = document.getElementById('ctl00_cphHeading_drpLeaveType');
                                                        if (sel) {
                                                            if (sel.value === '${selectedType.code}') {
                                                                window.LeaveBridge.onLeaveTypeReady();
                                                            } else {
                                                                sel.value = '${selectedType.code}';
                                                                if (typeof __doPostBack === 'function') {
                                                                    __doPostBack('ctl00${'$'}cphHeading${'$'}drpLeaveType', '');
                                                                } else {
                                                                    sel.dispatchEvent(new Event('change', { bubbles: true }));
                                                                }
                                                            }
                                                        } else {
                                                            window.LeaveBridge.onLeaveTypeReady();
                                                        }
                                                    } catch(e) {
                                                        window.LeaveBridge.onError('Failed to set leave type: ' + e.toString());
                                                    }
                                                })();
                                            """.trimIndent()
                                            view?.evaluateJavascript(jsSetType, null)
                                        }

                                        1 -> {
                                            // PostBack completed after setting leave type -> fill and submit!
                                            populateAndSubmitForm(this@apply)
                                        }

                                        2 -> {
                                            // PostBack completed after clicking submit! Verify result!
                                            statusText = "Verifying submission with UMS..."
                                            view?.postDelayed({
                                                if (isDone) return@postDelayed
                                                verifySubmissionResult(
                                                    wv = this@apply,
                                                    initialTopLeaveId = initialTopLeaveId,
                                                    capturedAlert = capturedAlert,
                                                    startCal = startCal,
                                                    onSuccess = { id ->
                                                        if (!isDone) {
                                                            isDone = true
                                                            saveFreshCookies(cm, prefs)
                                                            onSuccess(id)
                                                        }
                                                    },
                                                    onError = { err ->
                                                        if (!isDone) {
                                                            isDone = true
                                                            onError(err)
                                                        }
                                                    }
                                                )
                                            }, 800)
                                        }
                                    }
                                }
                            }
                        }

                        loadUrl(LEAVE_PORTAL_URL)
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
                                text = "APPLYING HOSTEL LEAVE",
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

            // Continuous polling / form auto-submission loop on login screen
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
                            onError("Leave application timed out. Please check your internet connection.")
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
                                    if (window.LeaveBridge) window.LeaveBridge.onStatus('Security verified! Submitting credentials...');
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

private fun saveFreshCookies(cm: CookieManager, prefs: android.content.SharedPreferences) {
    cm.flush()
    val freshCookies = cm.getCookie("https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx")
        ?.takeIf { it.isNotBlank() }
        ?: cm.getCookie("https://ums.lpu.in/lpuums")
        ?.takeIf { it.isNotBlank() }
        ?: cm.getCookie("https://ums.lpu.in")
        ?: ""
    if (freshCookies.isNotBlank()) {
        prefs.edit().putString(AutoSyncManager.KEY_UMS_COOKIES, freshCookies).apply()
    }
}



// Extracted helper function for the full JS injection to keep Compose clean
fun executeFormFillAndSubmit(
    wv: WebView,
    visitPlace: String,
    stayAddress: String,
    relativeMobile: String,
    startCal: Calendar,
    endCal: Calendar,
    reason: String
) {
    val safeVisitPlace = JSONObject.quote(visitPlace)
    val safeStayAddr = JSONObject.quote(stayAddress)
    val safeMobile = JSONObject.quote(relativeMobile)
    val safeReason = JSONObject.quote(reason)

    val uFormat = SimpleDateFormat("M/d/yyyy h:mm a", Locale.US)
    val isoFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
    val sText = uFormat.format(startCal.time)
    val sIso = isoFormat.format(startCal.time)
    val sYear = startCal.get(Calendar.YEAR)
    val sMonth = startCal.get(Calendar.MONTH)
    val sDay = startCal.get(Calendar.DAY_OF_MONTH)
    val sHour = startCal.get(Calendar.HOUR_OF_DAY)
    val sMin = startCal.get(Calendar.MINUTE)

    val eText = uFormat.format(endCal.time)
    val eIso = isoFormat.format(endCal.time)
    val eYear = endCal.get(Calendar.YEAR)
    val eMonth = endCal.get(Calendar.MONTH)
    val eDay = endCal.get(Calendar.DAY_OF_MONTH)
    val eHour = endCal.get(Calendar.HOUR_OF_DAY)
    val eMin = endCal.get(Calendar.MINUTE)

    val js = """
        (function() {
            try {
                window.alert = function(msg) { window.LeaveBridge.onAlert(msg); };
                window.confirm = function() { return true; };

                // 1. Destination Dropdown (ddlVisitDay)
                var vp = document.getElementById('ctl00_cphHeading_ddlVisitDay');
                if (vp) {
                    var target = $safeVisitPlace;
                    var found = false;
                    for (var i = 0; i < vp.options.length; i++) {
                        if (vp.options[i].value === target || vp.options[i].text.trim() === target) {
                            vp.selectedIndex = i;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        var lower = target.toLowerCase();
                        for (var j = 0; j < vp.options.length; j++) {
                            var t = vp.options[j].text.toLowerCase();
                            var v = vp.options[j].value.toLowerCase();
                            if (t.indexOf(lower) !== -1 || v.indexOf(lower) !== -1) {
                                vp.selectedIndex = j;
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        for (var k = 0; k < vp.options.length; k++) {
                            if (vp.options[k].value.toLowerCase() === 'other' || vp.options[k].text.toLowerCase() === 'other') {
                                vp.selectedIndex = k;
                                found = true;
                                break;
                            }
                        }
                    }
                    vp.dispatchEvent(new Event('change', { bubbles: true }));
                }

                // 2. Leave Term Check
                var ddlTerm = document.getElementById('ctl00_cphHeading_ddlLeaveTerm');
                if (ddlTerm && (ddlTerm.value === '0' || !ddlTerm.value) && ddlTerm.options.length > 1) {
                    ddlTerm.selectedIndex = 1;
                    ddlTerm.dispatchEvent(new Event('change', { bubbles: true }));
                }

                // 3. Relative Mobile
                var mob = document.getElementById('ctl00_cphHeading_txtVisitingMobile');
                if (mob) {
                    mob.value = $safeMobile;
                    mob.dispatchEvent(new Event('input', { bubbles: true }));
                    mob.dispatchEvent(new Event('change', { bubbles: true }));
                }

                // 4. Stay Address
                if (typeof Page_Validators !== 'undefined') {
                    for (var vi = 0; vi < Page_Validators.length; vi++) {
                        var vItem = Page_Validators[vi];
                        if (vItem && vItem.controltovalidate) {
                            var vMsg = (vItem.errormessage || '').toLowerCase();
                            if (vMsg.indexOf('stay') !== -1 || vMsg.indexOf('address') !== -1 || vMsg.indexOf('place') !== -1) {
                                var targetEl = document.getElementById(vItem.controltovalidate);
                                if (targetEl) {
                                    targetEl.value = $safeStayAddr;
                                    targetEl.dispatchEvent(new Event('input', { bubbles: true }));
                                    targetEl.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                            }
                        }
                    }
                }
                var allTextEls = document.querySelectorAll('input[type="text"], textarea');
                for (var ti = 0; ti < allTextEls.length; ti++) {
                    var elId = (allTextEls[ti].id || '').toLowerCase();
                    var elName = (allTextEls[ti].name || '').toLowerCase();
                    if (elId.indexOf('mobile') === -1 && elId.indexOf('reason') === -1 && 
                        elId.indexOf('date') === -1 && elId.indexOf('otp') === -1) {
                        if (elId.indexOf('stay') !== -1 || elId.indexOf('address') !== -1 ||
                            elId.indexOf('place') !== -1 || elId.indexOf('location') !== -1 ||
                            elId.indexOf('city') !== -1 || elName.indexOf('stay') !== -1 ||
                            elName.indexOf('address') !== -1 || elName.indexOf('place') !== -1) {
                            allTextEls[ti].value = $safeStayAddr;
                            allTextEls[ti].dispatchEvent(new Event('input', { bubbles: true }));
                            allTextEls[ti].dispatchEvent(new Event('change', { bubbles: true }));
                        }
                    }
                }

                // 5. Dates & Times Helper (Telerik RadDateTimePicker)
                function setRadPicker(baseId, y, m, d, h, min, textVal, isoVal) {
                    try {
                        var p = (typeof ${'$'}find === 'function') ? ${'$'}find(baseId) : null;
                        if (p && typeof p.set_selectedDate === 'function') {
                            p.set_selectedDate(new Date(y, m, d, h, min, 0));
                        }
                    } catch(e) {}

                    var di = document.getElementById(baseId + '_dateInput');
                    if (di) {
                        try { di.focus(); } catch(e) {}
                        di.value = textVal;
                        di.dispatchEvent(new Event('input', { bubbles: true }));
                        di.dispatchEvent(new Event('change', { bubbles: true }));
                        try { di.blur(); } catch(e) {}
                    }

                    var hid = document.getElementById(baseId);
                    if (hid) hid.value = textVal;

                    var cs = document.getElementById(baseId + '_dateInput_ClientState');
                    if (cs) {
                        try {
                            var obj = JSON.parse(cs.value || '{}');
                            obj.valueAsString = isoVal;
                            obj.validationText = isoVal;
                            obj.lastSetTextBoxValue = textVal;
                            cs.value = JSON.stringify(obj);
                        } catch(e) {}
                    }
                }

                setRadPicker('ctl00_cphHeading_startdateRadDateTimePicker1', $sYear, $sMonth, $sDay, $sHour, $sMin, '$sText', '$sIso');
                setRadPicker('ctl00_cphHeading_enddateRadDateTimePicker2', $eYear, $eMonth, $eDay, $eHour, $eMin, '$eText', '$eIso');

                // 6. Reason
                var txtReason = document.getElementById('ctl00_cphHeading_txtLeaveReason');
                if (txtReason) {
                    txtReason.value = $safeReason;
                    txtReason.dispatchEvent(new Event('input', { bubbles: true }));
                    txtReason.dispatchEvent(new Event('change', { bubbles: true }));
                }

                var ddlBhReason = document.getElementById('ctl00_cphHeading_ddlBhReason');
                if (ddlBhReason && ddlBhReason.options && ddlBhReason.options.length > 1) {
                    var matchedBh = false;
                    var rLower = $safeReason.toLowerCase();
                    for (var b = 0; b < ddlBhReason.options.length; b++) {
                        if (ddlBhReason.options[b].text.toLowerCase().indexOf(rLower) !== -1 ||
                            ddlBhReason.options[b].value.toLowerCase().indexOf(rLower) !== -1) {
                            ddlBhReason.selectedIndex = b;
                            matchedBh = true;
                            break;
                        }
                    }
                    if (!matchedBh && ddlBhReason.selectedIndex <= 0) {
                        ddlBhReason.selectedIndex = 1;
                    }
                    ddlBhReason.dispatchEvent(new Event('change', { bubbles: true }));
                }

                // 7. Declaration Checkbox
                var chk = document.getElementById('ctl00_cphHeading_chkOption');
                if (chk) {
                    chk.checked = true;
                    chk.defaultChecked = true;
                    chk.setAttribute('checked', 'checked');
                    chk.dispatchEvent(new Event('input', { bubbles: true }));
                    chk.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var allCheckboxes = document.querySelectorAll('input[type="checkbox"]');
                for (var ci = 0; ci < allCheckboxes.length; ci++) {
                    if (allCheckboxes[ci].name && allCheckboxes[ci].name.indexOf('chkOption') !== -1) {
                        allCheckboxes[ci].checked = true;
                        allCheckboxes[ci].defaultChecked = true;
                        allCheckboxes[ci].setAttribute('checked', 'checked');
                        allCheckboxes[ci].dispatchEvent(new Event('input', { bubbles: true }));
                        allCheckboxes[ci].dispatchEvent(new Event('change', { bubbles: true }));
                    }
                }

                // Clear/re-evaluate client validators
                if (typeof Page_Validators !== 'undefined') {
                    for (var vi = 0; vi < Page_Validators.length; vi++) {
                        var valItem = Page_Validators[vi];
                        if (valItem && typeof ValidatorValidate === 'function') {
                            ValidatorValidate(valItem);
                        }
                    }
                }

                // 8. Client-side validation check
                if (typeof Page_ClientValidate === 'function') {
                    try {
                        var valid = Page_ClientValidate('123');
                        if (!valid) {
                            var valMsgs = [];
                            if (typeof Page_Validators !== 'undefined') {
                                for (var v = 0; v < Page_Validators.length; v++) {
                                    var vItem = Page_Validators[v];
                                    if (vItem && !vItem.isvalid && vItem.errormessage) {
                                        valMsgs.push(vItem.errormessage);
                                    }
                                }
                            }
                            if (valMsgs.length > 0) {
                                var combined = valMsgs.join('\n');
                                window.LeaveBridge.onError(combined || 'Validation failed. Please verify form details.');
                                return;
                            }
                        }
                    } catch(e) {}
                }

                // 9. Click Submit Button with PostBack fallback cascade
                var btn = document.getElementById('ctl00_cphHeading_btnSubmit');
                if (btn) {
                    btn.focus();
                    btn.click();
                    setTimeout(function() {
                        if (typeof WebForm_DoPostBackWithOptions === 'function') {
                            try {
                                WebForm_DoPostBackWithOptions(new WebForm_PostBackOptions('ctl00${'$'}cphHeading${'$'}btnSubmit', '', true, '123', '', false, false));
                            } catch(e) {}
                        } else if (typeof __doPostBack === 'function') {
                            try {
                                __doPostBack('ctl00${'$'}cphHeading${'$'}btnSubmit', '');
                            } catch(e) {}
                        }
                    }, 400);
                } else {
                    window.LeaveBridge.onError('Submit button not found on UMS');
                }
            } catch(e) {
                window.LeaveBridge.onError('Error populating form: ' + e.toString());
            }
        })();
    """.trimIndent()
    wv.evaluateJavascript(js, null)
}

/**
 * Inspects the page after submission to detect new Leave ID or server rejection messages.
 */
private fun verifySubmissionResult(
    wv: WebView,
    initialTopLeaveId: String?,
    capturedAlert: String?,
    startCal: Calendar,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val jsCheck = """
        (function() {
            try {
                // 1. Standard server error spans
                var errSelectors = [
                    '#ctl00_cphHeading_lblerror',
                    '#ctl00_cphHeading_lblError',
                    '#ctl00_cphHeading_lblMsg',
                    '#ctl00_cphHeading_lblMessage',
                    '#ctl00_cphHeading_lblWarning',
                    '#ctl00_cphHeading_lblAlert',
                    '#ctl00_cphHeading_lblInfo'
                ];
                for (var si = 0; si < errSelectors.length; si++) {
                    var el = document.querySelector(errSelectors[si]);
                    if (el) {
                        var t = (el.textContent || el.innerText || '').trim();
                        if (t.length > 0 && t !== '*' && !t.includes('Select Term')) {
                            return JSON.stringify({ status: 'error', message: t });
                        }
                    }
                }

                // 2. Telerik RadAlert
                var radAlert = document.querySelector('.rwWindowContent, .rwDialogPopup, [id*="RadAlert"], [id*="radalert"]');
                if (radAlert) {
                    var rt = (radAlert.textContent || radAlert.innerText || '').trim();
                    if (rt.length > 0) {
                        return JSON.stringify({ status: 'error', message: rt });
                    }
                }

                // 3. OTP modal
                var otpTip = document.getElementById('ctl00_cphHeading_RadToolTip1');
                var txtOtp = document.getElementById('ctl00_cphHeading_txtOTP');
                var isOtpVisible = false;
                if (otpTip && (otpTip.style.display !== 'none' && otpTip.style.visibility !== 'hidden')) isOtpVisible = true;
                if (txtOtp && txtOtp.offsetParent !== null) isOtpVisible = true;
                if (isOtpVisible) {
                    return JSON.stringify({ status: 'error', message: 'UMS requires OTP validation for this leave. Please apply directly through UMS portal.' });
                }

                // 4. Validation summaries
                var valSummaries = document.querySelectorAll('[id*="ValidationSummary"]');
                for (var vs = 0; vs < valSummaries.length; vs++) {
                    var vSumEl = valSummaries[vs];
                    var items = vSumEl.querySelectorAll('li');
                    var msgs = [];
                    for (var it = 0; it < items.length; it++) {
                        var itTxt = (items[it].textContent || '').trim();
                        if (itTxt) msgs.push(itTxt);
                    }
                    if (msgs.length > 0) {
                        return JSON.stringify({ status: 'error', message: msgs.join('\n') });
                    }
                    var rawSum = (vSumEl.textContent || '').trim();
                    if (rawSum.length > 3 && rawSum.indexOf('Select') === -1) {
                        return JSON.stringify({ status: 'error', message: rawSum });
                    }
                }

                // 5. Leave table check
                var firstRow = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                if (firstRow) {
                    var leaveId = firstRow.cells[0] ? (firstRow.cells[0].textContent || '').trim() : '';
                    var leaveType = firstRow.cells[1] ? (firstRow.cells[1].textContent || '').trim() : '';
                    var applyDate = firstRow.cells[2] ? (firstRow.cells[2].textContent || '').trim() : '';
                    var status = firstRow.cells[6] ? (firstRow.cells[6].textContent || '').trim() : 'Applied';
                    return JSON.stringify({
                        status: 'table_check',
                        leaveId: leaveId,
                        leaveType: leaveType,
                        applyDate: applyDate,
                        leaveStatus: status
                    });
                }

                return JSON.stringify({ status: 'unknown' });
            } catch(e) {
                return JSON.stringify({ status: 'error', message: e.toString() });
            }
        })();
    """.trimIndent()

    wv.evaluateJavascript(jsCheck) { res ->
        try {
            val cleanJson = if (res.startsWith("\"") && res.endsWith("\"")) {
                JSONObject("{\"v\":$res}").getString("v")
            } else res
            val obj = JSONObject(cleanJson)
            val status = obj.optString("status")

            when (status) {
                "error" -> {
                    val msg = obj.optString("message", "Submission rejected by UMS")
                    onError(msg)
                }

                "table_check" -> {
                    val leaveId = obj.optString("leaveId")
                    val leaveStatus = obj.optString("leaveStatus", "Existing")
                    val isDifferentId = initialTopLeaveId != null && leaveId.isNotBlank() && leaveId != initialTopLeaveId
                    val isFirstEverLeave = initialTopLeaveId.isNullOrBlank() && leaveId.isNotBlank()

                    if (isDifferentId || isFirstEverLeave) {
                        onSuccess(leaveId)
                    } else {
                        // Leave ID was unchanged
                        if (!capturedAlert.isNullOrBlank()) {
                            onError(capturedAlert)
                        } else {
                            val nowMillis = System.currentTimeMillis()
                            val isPastDeparture = startCal.timeInMillis < (nowMillis - 60_000)
                            val detailedReason = if (isPastDeparture) {
                                val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(startCal.time)
                                "Departure time ($timeStr) is in the past. UMS rules require departure time to be later than current time."
                            } else if (leaveId.isNotBlank()) {
                                "A leave application (#$leaveId) already exists with status '$leaveStatus'. UMS does not allow multiple applications for the same slot."
                            } else {
                                "Leave was not applied by UMS. Please verify departure time is in the future, return time is within hostel curfew, and no conflicting leave exists."
                            }
                            onError(detailedReason)
                        }
                    }
                }

                else -> {
                    if (!capturedAlert.isNullOrBlank()) {
                        val alertMsg = capturedAlert
                        val lower = alertMsg.lowercase()
                        if (lower.contains("success") || lower.contains("applied")) {
                            val regex = "\\b\\d{5,8}\\b".toRegex()
                            val match = regex.find(alertMsg)
                            onSuccess(match?.value ?: alertMsg)
                        } else {
                            onError(alertMsg)
                        }
                    } else {
                        onError("Leave submission could not be verified by UMS. Please check your leave records.")
                    }
                }
            }
        } catch (e: Exception) {
            onError("Failed to verify submission: ${e.message}")
        }
    }
}
