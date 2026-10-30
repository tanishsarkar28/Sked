package com.sked.sked_app.leave

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private val Slate = Color(0xFF7A7774)
private const val LEAVE_PORTAL_URL = "https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx"

/**
 * Dedicated Leave Sync Bridge Dialog.
 *
 * Logs directly into UMS if session cookies have expired,
 * fetches the latest hostel leave balances, profile, and records,
 * saves them locally to LeaveStorage, and syncs fresh cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeaveSyncBridgeDialog(
    onDismiss: () -> Unit,
    onSuccess: (data: LeaveParser.ParsedLeaveData) -> Unit,
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
    var showRawWebView by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isDone by remember { mutableStateOf(false) }
    var turnstileWaitTicks by remember { mutableIntStateOf(0) }

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
                        text = "Enter your Registration Number and Password to sync your hostel leave data:",
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
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlabElevated,
                            border = BorderStroke(1.dp, Rule),
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
                                Text("SYNC LEAVE", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ── Dedicated Bridge Dialog ────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Blaze,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SYNCING HOSTEL LEAVE",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Blaze,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Live UMS balance & history fetch",
                                fontSize = 10.5.sp,
                                color = Slate
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Blaze,
                    trackColor = Slab
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Status message & Captcha toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = Chalk,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slab,
                        border = BorderStroke(0.5.dp, Rule),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showRawWebView = !showRawWebView }
                    ) {
                        Text(
                            text = if (showRawWebView) "HIDE VIEW" else "SHOW BROWSER",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Blaze,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // WebView Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showRawWebView) Color.White else Slab)
                        .border(1.dp, Rule, RoundedCornerShape(8.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true

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

                                class LeaveSyncBridge {
                                    @JavascriptInterface
                                    fun onLeaveHtml(html: String) {
                                        if (isDone) return
                                        isDone = true
                                        coroutineScope.launch(Dispatchers.Main) {
                                            statusText = "Portal loaded! Parsing balances..."
                                            cm.flush()
                                            val freshCookies = cm.getCookie(LEAVE_PORTAL_URL)
                                                ?.takeIf { it.isNotBlank() }
                                                ?: cm.getCookie("https://ums.lpu.in/lpuums")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: cm.getCookie("https://ums.lpu.in")
                                                ?: ""
                                            if (freshCookies.isNotBlank()) {
                                                prefs.edit().putString(AutoSyncManager.KEY_UMS_COOKIES, freshCookies).apply()
                                            }

                                            try {
                                                val parsed = LeaveParser.parse(html)
                                                if (parsed.balance.dayTotal > 0 || parsed.records.isNotEmpty()) {
                                                    LeaveStorage.saveBalance(ctx, parsed.balance)
                                                    LeaveStorage.saveProfile(ctx, parsed.profile)
                                                    LeaveStorage.saveRecords(ctx, parsed.records)
                                                    onSuccess(parsed)
                                                } else {
                                                    onError("UMS portal did not return valid leave balance data.")
                                                }
                                            } catch (e: Exception) {
                                                onError("Failed to parse leave portal: ${e.message}")
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onStatus(msg: String) {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            statusText = msg
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAuthError(err: String) {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            statusText = "Login error: $err"
                                            if (!isDone) {
                                                isDone = true
                                                onError(err)
                                            }
                                        }
                                    }
                                }

                                addJavascriptInterface(LeaveSyncBridge(), "LeaveSyncBridge")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val curUrl = url ?: ""
                                        if (curUrl.contains("Login", ignoreCase = true)) {
                                            statusText = "Solving Cloudflare & authenticating..."
                                        } else if (curUrl.contains("StudentDashboard", ignoreCase = true) ||
                                            curUrl.contains("Default3", ignoreCase = true) ||
                                            curUrl.contains("w-schedule", ignoreCase = true)
                                        ) {
                                            statusText = "Login successful! Opening hostel leave portal..."
                                            view?.loadUrl(LEAVE_PORTAL_URL)
                                        } else if (curUrl.contains("frmStudentHostelLeaveApplicationTermWise", ignoreCase = true)) {
                                            statusText = "Leave portal reached! Extracting data..."
                                            view?.evaluateJavascript(
                                                """
                                                (function() {
                                                    try {
                                                        if (window.LeaveSyncBridge) {
                                                            window.LeaveSyncBridge.onLeaveHtml(document.documentElement.outerHTML);
                                                        }
                                                    } catch(e) {
                                                        if (window.LeaveSyncBridge) window.LeaveSyncBridge.onAuthError(e.toString());
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                    }
                                }

                                loadUrl(LEAVE_PORTAL_URL)
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay when browser is hidden
                    if (!showRawWebView) {
                        Surface(
                            color = Ink.copy(alpha = 0.96f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Blaze, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 13.sp,
                                    color = Chalk,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Direct sync with UMS hostel leave system.\nRetrieves accurate remaining leaves and application status.",
                                    fontSize = 11.sp,
                                    color = Slate,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Slab,
                                    border = BorderStroke(1.dp, Rule),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showRawWebView = true }
                                ) {
                                    Text(
                                        text = "SHOW BROWSER FOR CLOUDFLARE",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = Blaze,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Continuous polling / form auto-submission loop on login screen
            LaunchedEffect(webViewRef, userId, password) {
                val wv = webViewRef ?: return@LaunchedEffect
                val safeUser = JSONObject.quote(userId)
                val safePass = JSONObject.quote(password)

                while (!isDone) {
                    delay(700)
                    turnstileWaitTicks++
                    if (turnstileWaitTicks > 6 && !showRawWebView) {
                        // Reveal browser if Cloudflare Turnstile takes > 4.5 seconds
                        showRawWebView = true
                    }

                    val autoFillJs = """
                        (function() {
                            try {
                                var u = document.getElementById('txtU');
                                var p = document.querySelector('input[type="password"]');
                                var cf = document.querySelector('[name="cf-turnstile-response"]');
                                var form = document.querySelector('form');

                                if (u && $safeUser && (!u.value || u.value.length === 0)) {
                                    u.value = $safeUser;
                                }
                                if (p && $safePass && (!p.value || p.value.length === 0)) {
                                    p.value = $safePass;
                                }

                                if (window._skedSubmitting) return;

                                if (cf && cf.value && cf.value.length > 0) {
                                    window._skedSubmitting = true;
                                    if (window.LeaveSyncBridge) window.LeaveSyncBridge.onStatus('Turnstile cleared! Submitting credentials...');
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
