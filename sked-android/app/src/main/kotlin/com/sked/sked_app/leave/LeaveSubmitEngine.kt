package com.sked.sked_app.leave

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class LeaveSyncResult {
    data class Success(val data: LeaveParser.ParsedLeaveData) : LeaveSyncResult()
    object SessionExpired : LeaveSyncResult()
    data class Error(val message: String) : LeaveSyncResult()
}

object LeaveSubmitEngine {

    const val LEAVE_URL = "https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx"

    fun getBestUmsCookies(context: Context): String {
        val cm = CookieManager.getInstance()
        cm.flush()
        val prefs = context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
        val savedCookies = prefs.getString(com.sked.sked_app.AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""

        val cookieUrls = listOf(
            "https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx",
            "https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx",
            "https://ums.lpu.in/lpuums",
            "https://ums.lpu.in"
        )

        if (savedCookies.isNotBlank()) {
            for (cookie in savedCookies.split(";")) {
                val trimmed = cookie.trim()
                if (trimmed.isNotBlank()) {
                    for (u in cookieUrls) {
                        cm.setCookie(u, trimmed)
                    }
                }
            }
            cm.flush()
        }

        val cookieMap = LinkedHashMap<String, String>()
        fun parseCookies(str: String) {
            for (part in str.split(";")) {
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val k = trimmed.substring(0, eqIdx).trim()
                    val v = trimmed.substring(eqIdx + 1).trim()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        cookieMap[k] = v
                    }
                }
            }
        }
        // Baseline: saved cookies
        parseCookies(savedCookies)
        // Flush and read fresh live cookies from CookieManager
        try {
            cm.flush()
        } catch (_: Exception) {}
        for (u in cookieUrls) {
            val c = cm.getCookie(u) ?: ""
            parseCookies(c)
        }

        val result = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (result.isNotBlank() && result != savedCookies) {
            prefs.edit().putString(com.sked.sked_app.AutoSyncManager.KEY_UMS_COOKIES, result).apply()
        }
        return result
    }

    fun getDefaultUserAgent(context: Context): String {
        return try {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        }
    }

    /**
     * Fast-path: Fetches leave balance & past history via OkHttpClient with session cookies.
     * Takes ~300ms, completely invisible.
     */
    suspend fun fetchLatestData(context: Context): LeaveSyncResult = withContext(Dispatchers.IO) {
        try {
            val cookies = getBestUmsCookies(context)
            if (cookies.isBlank()) {
                return@withContext LeaveSyncResult.SessionExpired
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()

            val req = Request.Builder()
                .url(LEAVE_URL)
                .header("Cookie", cookies)
                .header("User-Agent", getDefaultUserAgent(context))
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code in 300..399) {
                    val location = resp.header("Location") ?: ""
                    if (location.contains("Login", ignoreCase = true)) {
                        return@withContext LeaveSyncResult.SessionExpired
                    }
                }
                if (resp.code != 200) {
                    return@withContext LeaveSyncResult.Error("HTTP ${resp.code}")
                }
                val html = resp.body?.string() ?: ""
                if (html.contains("LoginNew.aspx", ignoreCase = true) || html.contains("Login.aspx", ignoreCase = true)) {
                    return@withContext LeaveSyncResult.SessionExpired
                }
                if (html.contains("cf-browser-verification", ignoreCase = true) ||
                    html.contains("Just a moment", ignoreCase = true) ||
                    html.contains("Attention Required", ignoreCase = true) ||
                    html.contains("challenge-running", ignoreCase = true)
                ) {
                    return@withContext LeaveSyncResult.SessionExpired
                }
                if (!html.contains("ctl00_cphHeading", ignoreCase = true) &&
                    !html.contains("frmStudentHostelLeaveApplicationTermWise", ignoreCase = true)
                ) {
                    return@withContext LeaveSyncResult.Error("Not leave page")
                }

                if (html.isNotBlank()) {
                    val parsed = LeaveParser.parse(html)
                    val hasBal = parsed.balance.dayTotal > 0 || parsed.balance.nightTotal > 0
                    val hasProf = parsed.profile.uid.isNotBlank() || parsed.profile.name.isNotBlank()
                    val hasRecs = parsed.records.isNotEmpty()

                    if (hasBal || hasRecs) {
                        if (hasBal) LeaveStorage.saveBalance(context, parsed.balance)
                        if (hasProf) LeaveStorage.saveProfile(context, parsed.profile)
                        if (hasRecs) LeaveStorage.saveRecords(context, parsed.records)
                        return@withContext LeaveSyncResult.Success(parsed)
                    }
                }
                return@withContext LeaveSyncResult.Error("Could not parse leave portal data")
            }
        } catch (e: Exception) {
            android.util.Log.w("SkedLeave", "fetchLatestData error: ${e.message}")
            return@withContext LeaveSyncResult.Error(e.message ?: "Network error")
        }
    }

    const val LEAVE_SLIP_URL = "https://ums.lpu.in/lpuums/frmHostelLeaveSlipTest.aspx"
    const val STUDENT_PHOTO_URL = "https://ums.lpu.in/lpuums/DisplayImageForPowerBi.aspx"

    /**
     * Fetches official hostel leave gate pass from frmHostelLeaveSlipTest.aspx using session cookies.
     * Also downloads and caches the student photo locally.
     */
    suspend fun fetchGatePass(context: Context): Result<HostelGatePass> = withContext(Dispatchers.IO) {
        try {
            val cookies = getBestUmsCookies(context)
            if (cookies.isBlank()) {
                return@withContext Result.failure(Exception("UMS session cookies missing"))
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()

            val req = Request.Builder()
                .url(LEAVE_SLIP_URL)
                .header("Cookie", cookies)
                .header("User-Agent", getDefaultUserAgent(context))
                .header("Referer", "https://ums.lpu.in/lpuums/StudentDashboard.aspx")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code in 300..399) {
                    val location = resp.header("Location") ?: ""
                    if (location.contains("Login", ignoreCase = true)) {
                        return@withContext Result.failure(Exception("Session expired (redirected to login)"))
                    }
                }
                val html = resp.body?.string() ?: ""
                if (html.contains("LoginNew.aspx", ignoreCase = true) || html.contains("Login.aspx", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("Session expired (login page returned)"))
                }
                if (html.isNotBlank()) {
                    val pass = LeaveParser.parseGatePass(html)
                    if (pass != null) {
                        val existing = LeaveStorage.loadGatePass(context)
                        val hasArrivedInside = pass.checkInTime.isNotBlank() || existing.checkInTime.isNotBlank()
                        val umsHasNoSlip = !pass.hasActivePass || pass.leaveCode == "6000" || pass.leaveId == "0"

                        if (hasArrivedInside && umsHasNoSlip) {
                            // Condition 1: Student has arrived inside the uni AND slip is not showing on UMS
                            LeaveStorage.clearGatePass(context)
                        } else if (pass.hasActivePass) {
                            LeaveStorage.saveGatePass(context, pass)
                            val targetPhotoUrl = if (pass.photoUrl.isNotBlank()) pass.photoUrl else STUDENT_PHOTO_URL
                            downloadPhoto(context, targetPhotoUrl, cookies)
                        } else if (pass.checkInTime.isNotBlank()) {
                            if (existing.hasActivePass) {
                                val updated = existing.copy(checkInTime = pass.checkInTime)
                                LeaveStorage.saveGatePass(context, updated)
                            }
                        }
                        return@withContext Result.success(pass)
                    }
                }
                Result.failure(Exception("Could not parse leave gate pass"))
            }
        } catch (e: Exception) {
            android.util.Log.w("SkedLeave", "fetchGatePass error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Downloads and caches the student photo locally to filesDir/student_photo.jpg.
     */
    fun downloadPhoto(context: Context, photoUrl: String, cookies: String) {
        if (photoUrl.isBlank()) return
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(photoUrl)
                .header("Cookie", cookies)
                .header("User-Agent", getDefaultUserAgent(context))
                .header("Referer", LEAVE_SLIP_URL)
                .build()
            client.newCall(req).execute().use { photoResp ->
                val bytes = photoResp.body?.bytes()
                if (bytes != null && bytes.size > 200) {
                    val photoFile = java.io.File(context.filesDir, "student_photo.jpg")
                    photoFile.writeBytes(bytes)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SkedLeave", "downloadPhoto error: ${e.message}")
        }
    }

    /**
     * Submits the leave application on UMS via an automated WebView bridge.
     * Handles ASP.NET PostBacks, Telerik datepickers, declaration checkbox, and intercepts alerts.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun submitLeave(
        context: Context,
        leaveTypeCode: String,
        visitPlace: String,
        stayAddress: String,
        relativeMobile: String,
        startCal: java.util.Calendar,
        endCal: java.util.Calendar,
        reason: String,
        onStatus: (String) -> Unit,
        onOtpRequired: ((maskedPhone: String, submitCode: (String) -> Unit, resendCode: () -> Unit, cancelAction: () -> Unit) -> Unit)? = null,
        onOtpError: ((errorMessage: String, canResend: Boolean) -> Unit)? = null,
        onOtpResent: (() -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var isResumed = false
            var timeoutRunnable: Runnable? = null

            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
            }

            fun finishWith(result: Result<String>) {
                if (!isResumed) {
                    isResumed = true
                    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) {}
                    continuation.resume(result)
                }
            }

            // Ensure CookieManager has the latest merged UMS session cookies
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            val bestCookies = getBestUmsCookies(context)
            if (bestCookies.isNotBlank()) {
                for (cookie in bestCookies.split(";")) {
                    val trimmed = cookie.trim()
                    if (trimmed.isNotBlank()) {
                        cm.setCookie("https://ums.lpu.in", trimmed)
                    }
                }
                cm.flush()
            }

            var capturedAlert: String? = null
            var capturedError: String? = null
            var initialTopLeaveId: String? = null
            var savedMaskedPhone = ""
            var stage = 0 // 0: initial load, 1: leaveType set postback, 2: submitted
            var executeSubmitFn: (() -> Unit)? = null

            fun isOtpError(msg: String): Boolean {
                val lower = msg.lowercase()
                return lower.contains("otp") ||
                       lower.contains("invalid") ||
                       lower.contains("mismatch") ||
                       lower.contains("wrong") ||
                       lower.contains("expire")
            }

            fun isSuccessMessage(msg: String): Boolean {
                val lower = msg.lowercase().trim()
                if (lower.contains("cannot") ||
                    lower.contains("can not") ||
                    lower.contains("not be applied") ||
                    lower.contains("not applied") ||
                    lower.contains("already applied") ||
                    lower.contains("already exist") ||
                    lower.contains("still pending") ||
                    lower.contains("not allow") ||
                    lower.contains("not permissible") ||
                    lower.contains("warden") ||
                    lower.contains("reject") ||
                    lower.contains("fail") ||
                    lower.contains("error") ||
                    lower.contains("invalid") ||
                    lower.contains("exceed") ||
                    lower.contains("exhaust") ||
                    lower.contains("curfew") ||
                    lower.contains("denied") ||
                    lower.contains("restricted") ||
                    lower.contains("sorry") ||
                    lower.contains("unable") ||
                    lower.contains("greater than") ||
                    lower.contains("less than") ||
                    lower.contains("past") ||
                    lower.contains("future")
                ) {
                    return false
                }
                return (lower.contains("success") && !lower.contains("not success") && !lower.contains("unsuccess")) ||
                       (lower.contains("applied") && lower.contains("success")) ||
                       (lower.contains("applied") && lower.contains("leave id")) ||
                       (lower.contains("approved") && !lower.contains("not approved"))
            }

            class LeaveJsBridge {
                @JavascriptInterface
                fun onInitialTopLeaveId(id: String) {
                    if (initialTopLeaveId == null && id.isNotBlank()) {
                        initialTopLeaveId = id.trim()
                        android.util.Log.i("SkedLeave", "Recorded initial top leave ID: '$initialTopLeaveId'")
                    }
                }

                @JavascriptInterface
                fun onLeaveTypeReady() {
                    mainHandler.post {
                        executeSubmitFn?.invoke()
                    }
                }

                @JavascriptInterface
                fun onAlert(msg: String) {
                    val clean = msg.trim()
                    val lower = clean.lowercase()
                    if (lower == "hello" || lower == "error" || lower.contains("concept note") || lower.contains("idea hub")) {
                        android.util.Log.w("SkedLeave", "Ignoring dummy/header alert in onAlert: $msg")
                        return
                    }
                    android.util.Log.i("SkedLeave", "Intercepted UMS Alert via bridge: $clean")
                    capturedAlert = clean
                    if (stage >= 1) {
                        if (isSuccessMessage(clean)) {
                            finishWith(Result.success(clean))
                        } else if (isOtpError(clean)) {
                            onOtpError?.invoke(clean, true)
                        } else {
                            finishWith(Result.failure(Exception(clean)))
                        }
                    }
                }

                @JavascriptInterface
                fun onError(err: String) {
                    android.util.Log.w("SkedLeave", "Intercepted UMS Error via bridge: $err")
                    capturedError = err
                    finishWith(Result.failure(Exception(err)))
                }

                @JavascriptInterface
                fun onSuccess(leaveId: String) {
                    android.util.Log.i("SkedLeave", "Leave submitted successfully with ID: $leaveId")
                    finishWith(Result.success(leaveId))
                }
            }

            webView.addJavascriptInterface(LeaveJsBridge(), "SkedLeaveBridge")

            webView.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    val msg = message?.trim() ?: "Unknown alert"
                    val lower = msg.lowercase()
                    result?.confirm()
                    if (lower == "hello" || lower == "error" || lower.contains("concept note") || lower.contains("idea hub")) {
                        android.util.Log.w("SkedLeave", "Ignoring dummy/header alert in onJsAlert: $msg")
                        return true
                    }
                    android.util.Log.i("SkedLeave", "WebChromeClient intercepted alert: $msg")
                    capturedAlert = msg
                    if (stage >= 1) {
                        if (isSuccessMessage(msg)) {
                            finishWith(Result.success(msg))
                        } else if (isOtpError(msg)) {
                            onOtpError?.invoke(msg, true)
                        } else {
                            finishWith(Result.failure(Exception(msg)))
                        }
                    }
                    return true
                }
            }

            val performSubmit: () -> Unit = {
                if (stage < 2) {
                    stage = 2
                    onStatus("Submitting leave application...")
                    val safeVisitPlace = org.json.JSONObject.quote(visitPlace)
                    val safeReason = org.json.JSONObject.quote(reason)
                    val safeStayAddr = org.json.JSONObject.quote(stayAddress)
                    val safeMobile = org.json.JSONObject.quote(relativeMobile)

                    val uFormat = java.text.SimpleDateFormat("M/d/yyyy h:mm a", java.util.Locale.US)
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US)
                    val sText = uFormat.format(startCal.time)
                    val sIso = isoFormat.format(startCal.time)
                    val sYear = startCal.get(java.util.Calendar.YEAR)
                    val sMonth = startCal.get(java.util.Calendar.MONTH)
                    val sDay = startCal.get(java.util.Calendar.DAY_OF_MONTH)
                    val sHour = startCal.get(java.util.Calendar.HOUR_OF_DAY)
                    val sMin = startCal.get(java.util.Calendar.MINUTE)

                    val eText = uFormat.format(endCal.time)
                    val eIso = isoFormat.format(endCal.time)
                    val eYear = endCal.get(java.util.Calendar.YEAR)
                    val eMonth = endCal.get(java.util.Calendar.MONTH)
                    val eDay = endCal.get(java.util.Calendar.DAY_OF_MONTH)
                    val eHour = endCal.get(java.util.Calendar.HOUR_OF_DAY)
                    val eMin = endCal.get(java.util.Calendar.MINUTE)

                    val jsFillAndSubmit = """
                        (function() {
                            try {
                                function installHook(name) {
                                    try {
                                        var _orig = window[name];
                                        Object.defineProperty(window, name, {
                                            get: function() {
                                                return function() {
                                                    var m = arguments.length > 0 ? String(arguments[0] || '') : '';
                                                    try {
                                                        if (window.SkedLeaveBridge && window.SkedLeaveBridge.onAlert) {
                                                            window.SkedLeaveBridge.onAlert(m);
                                                        }
                                                    } catch(e) {}
                                                    if (typeof _orig === 'function') {
                                                        try { _orig.apply(window, arguments); } catch(e) {}
                                                    }
                                                };
                                            },
                                            set: function(fn) { _orig = fn; },
                                            configurable: true,
                                            enumerable: true
                                        });
                                    } catch(e) {
                                        window[name] = function() {
                                            var m = arguments.length > 0 ? String(arguments[0] || '') : '';
                                            try {
                                                if (window.SkedLeaveBridge && window.SkedLeaveBridge.onAlert) {
                                                    window.SkedLeaveBridge.onAlert(m);
                                                }
                                            } catch(e) {}
                                        };
                                    }
                                }
                                installHook('alert');
                                installHook('radalert');
                                window.confirm = function() { return true; };
                                window.radconfirm = function() { return true; };

                                // 1. Destination Dropdown (ddlVisitDay)
                                var vp = document.getElementById('ctl00_cphHeading_ddlVisitDay');
                                if (vp) {
                                    // Neutralize onchange so selecting visit place does not fire premature postback
                                    vp.onchange = null;
                                    vp.removeAttribute('onchange');
                                    var target = $safeVisitPlace;
                                    var found = false;
                                    for (var i = 0; i < vp.options.length; i++) {
                                        if (vp.options[i].value === target || vp.options[i].text.trim() === target) {
                                            vp.selectedIndex = i;
                                            vp.value = vp.options[i].value;
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
                                                vp.value = vp.options[j].value;
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!found) {
                                        for (var k = 0; k < vp.options.length; k++) {
                                            if (vp.options[k].value.toLowerCase() === 'other' || vp.options[k].text.toLowerCase() === 'other') {
                                                vp.selectedIndex = k;
                                                vp.value = vp.options[k].value;
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                }

                                // 2. Leave Term Check
                                var ddlTerm = document.getElementById('ctl00_cphHeading_ddlLeaveTerm');
                                if (ddlTerm && (ddlTerm.value === '0' || !ddlTerm.value) && ddlTerm.options.length > 1) {
                                    ddlTerm.selectedIndex = 1;
                                }

                                // 3. Relative Mobile
                                var mob = document.getElementById('ctl00_cphHeading_txtVisitingMobile');
                                if (mob) { mob.value = $safeMobile; mob.dispatchEvent(new Event('input')); }

                                // 4. Stay Address: Multiple robust matching strategies
                                // A. Check all validators for any mentioning 'stay' or 'address'
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

                                // B. Check any input/textarea in rows between ddlVisitDay and txtVisitingMobile
                                var vpEl = document.getElementById('ctl00_cphHeading_ddlVisitDay');
                                var mobEl = document.getElementById('ctl00_cphHeading_txtVisitingMobile');
                                if (vpEl && mobEl) {
                                    var tableEl = vpEl.closest('table') || document;
                                    var allTrs = tableEl.querySelectorAll('tr');
                                    var inRange = false;
                                    for (var trI = 0; trI < allTrs.length; trI++) {
                                        var rowEl = allTrs[trI];
                                        if (rowEl.contains(vpEl)) { inRange = true; continue; }
                                        if (inRange) {
                                            if (rowEl.contains(mobEl)) break;
                                            var rowInps = rowEl.querySelectorAll('input[type="text"], textarea');
                                            for (var rk = 0; rk < rowInps.length; rk++) {
                                                rowInps[rk].value = $safeStayAddr;
                                                rowInps[rk].dispatchEvent(new Event('input', { bubbles: true }));
                                                rowInps[rk].dispatchEvent(new Event('change', { bubbles: true }));
                                            }
                                        }
                                    }
                                }

                                // C. Fallback: Check all inputs for common address keywords (case-insensitive)
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

                                // Re-run validation only on leave form validators (group '123') to clear client errors
                                if (typeof Page_Validators !== 'undefined') {
                                    for (var vi = 0; vi < Page_Validators.length; vi++) {
                                        var valItem = Page_Validators[vi];
                                        if (valItem && (!valItem.validationGroup || valItem.validationGroup === '123') && typeof ValidatorValidate === 'function') {
                                            ValidatorValidate(valItem);
                                        }
                                    }
                                }

                                // 5. Dates & Times Helper (Telerik RadDateTimePicker + inputs + clientState)
                                function setRadPicker(baseId, y, m, d, h, min, textVal, isoVal) {
                                    try {
                                        var p = (typeof ${'$'}find === 'function') ? ${'$'}find(baseId) : null;
                                        if (p) {
                                            p._autoPostBack = false;
                                            var pDi = (typeof p.get_dateInput === 'function') ? p.get_dateInput() : null;
                                            if (pDi) pDi._autoPostBack = false;
                                            if (typeof p.set_selectedDate === 'function') {
                                                p.set_selectedDate(new Date(y, m, d, h, min, 0));
                                            }
                                        }
                                    } catch(e) {}

                                    var di = document.getElementById(baseId + '_dateInput');
                                    if (di) {
                                        di.onchange = null;
                                        di.removeAttribute('onchange');
                                        di.value = textVal;
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

                                // 6. Reason (both text area and dropdown if present)
                                var txtReason = document.getElementById('ctl00_cphHeading_txtLeaveReason');
                                if (txtReason) { txtReason.value = $safeReason; txtReason.dispatchEvent(new Event('input')); }

                                var ddlBhReason = document.getElementById('ctl00_cphHeading_ddlBhReason');
                                if (ddlBhReason && ddlBhReason.options && ddlBhReason.options.length > 1) {
                                    ddlBhReason.onchange = null;
                                    ddlBhReason.removeAttribute('onchange');
                                    var matchedBh = false;
                                    var rLower = $safeReason.toLowerCase();
                                    for (var b = 0; b < ddlBhReason.options.length; b++) {
                                        if (ddlBhReason.options[b].text.toLowerCase().indexOf(rLower) !== -1 ||
                                            ddlBhReason.options[b].value.toLowerCase().indexOf(rLower) !== -1) {
                                            ddlBhReason.selectedIndex = b;
                                            ddlBhReason.value = ddlBhReason.options[b].value;
                                            matchedBh = true;
                                            break;
                                        }
                                    }
                                    if (!matchedBh && ddlBhReason.selectedIndex <= 0) {
                                        ddlBhReason.selectedIndex = 1;
                                        ddlBhReason.value = ddlBhReason.options[1].value;
                                    }
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

                                // 8. Client-side validation check (only for hostel leave group '123')
                                if (typeof Page_ClientValidate === 'function') {
                                    var valid = Page_ClientValidate('123');
                                    if (!valid) {
                                        var valMsgs = [];
                                        if (typeof Page_Validators !== 'undefined') {
                                            for (var vi = 0; vi < Page_Validators.length; vi++) {
                                                var vItem = Page_Validators[vi];
                                                if (vItem && (!vItem.validationGroup || vItem.validationGroup === '123') && !vItem.isvalid && vItem.errormessage) {
                                                    valMsgs.push(vItem.errormessage);
                                                }
                                            }
                                        }
                                        var combined = valMsgs.join('\n');
                                        window.SkedLeaveBridge.onError(combined || 'Validation failed. Please verify form details.');
                                        return;
                                    }
                                }

                                // 9. Click Submit Button (ensure __EVENTTARGET is clean so btnSubmit is processed by UMS)
                                var btn = document.getElementById('ctl00_cphHeading_btnSubmit');
                                if (btn) {
                                    setTimeout(function() {
                                        try {
                                            var et = document.getElementById('__EVENTTARGET');
                                            if (et) et.value = '';
                                            var ea = document.getElementById('__EVENTARGUMENT');
                                            if (ea) ea.value = '';

                                            installHook('alert');
                                            installHook('radalert');

                                            btn.click();
                                        } catch(e) {
                                            window.SkedLeaveBridge.onError('Error clicking submit: ' + e.toString());
                                        }
                                    }, 200);
                                } else {
                                    window.SkedLeaveBridge.onError('Submit button not found');
                                }
                            } catch(e) {
                                window.SkedLeaveBridge.onError('Error populating form: ' + e.toString());
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(jsFillAndSubmit, null)
                }
            }
            fun injectHooks(wv: WebView?) {
                val hookJs = """
                    (function() {
                        try {
                            function installHook(name) {
                                try {
                                    var _orig = window[name];
                                    Object.defineProperty(window, name, {
                                        get: function() {
                                            return function() {
                                                var m = arguments.length > 0 ? String(arguments[0] || '') : '';
                                                try {
                                                    if (window.SkedLeaveBridge && window.SkedLeaveBridge.onAlert) {
                                                        window.SkedLeaveBridge.onAlert(m);
                                                    }
                                                } catch(e) {}
                                                if (typeof _orig === 'function') {
                                                    try { _orig.apply(window, arguments); } catch(e) {}
                                                }
                                            };
                                        },
                                        set: function(fn) { _orig = fn; },
                                        configurable: true,
                                        enumerable: true
                                    });
                                } catch(e) {
                                    window[name] = function() {
                                        var m = arguments.length > 0 ? String(arguments[0] || '') : '';
                                        try {
                                            if (window.SkedLeaveBridge && window.SkedLeaveBridge.onAlert) {
                                                window.SkedLeaveBridge.onAlert(m);
                                            }
                                        } catch(e) {}
                                    };
                                }
                            }
                            installHook('alert');
                            installHook('radalert');
                            window.confirm = function() { return true; };
                            window.radconfirm = function() { return true; };
                            if (typeof Sys !== 'undefined' && Sys.Application) {
                                Sys.Application.add_load(function() {
                                    installHook('alert');
                                    installHook('radalert');
                                });
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                wv?.evaluateJavascript(hookJs, null)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectHooks(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectHooks(view)
                    if (url != null && (url.contains("LoginNew.aspx", ignoreCase = true) || url.contains("Login.aspx", ignoreCase = true) || url.contains("Default3.aspx", ignoreCase = true) || url.contains("lockerror", ignoreCase = true))) {
                        finishWith(Result.failure(Exception("UMS session expired. Please re-authenticate.")))
                        return
                    }
                    if (url == null || !url.contains("frmStudentHostelLeaveApplicationTermWise")) return

                    // Check if an alert was captured on submission
                    if (capturedAlert != null) {
                        val alertMsg = capturedAlert ?: ""
                        val lower = alertMsg.trim().lowercase()
                        if (lower == "hello" || lower == "error" || lower.contains("concept note") || lower.contains("idea hub")) {
                            capturedAlert = null
                        } else {
                            if (isSuccessMessage(alertMsg)) {
                                finishWith(Result.success(alertMsg))
                                return
                            } else if (isOtpError(alertMsg)) {
                                onOtpError?.invoke(alertMsg, true)
                                capturedAlert = null
                            } else {
                                finishWith(Result.failure(Exception(alertMsg)))
                                return
                            }
                        }
                    }

                    when (stage) {
                        0 -> {
                            // Step 1: Record initial top leave ID, set Leave Type dropdown and trigger ASP.NET postback
                            onStatus("Configuring leave type...")
                            stage = 1
                            val jsSetType = """
                                (function() {
                                    try {
                                        window.alert = function(msg) { window.SkedLeaveBridge.onAlert(msg); };
                                        window.radalert = function(msg) { window.SkedLeaveBridge.onAlert(msg); };
                                        window.confirm = function() { return true; };
                                        window.radconfirm = function() { return true; };

                                        // Capture existing top leave ID
                                        try {
                                            var fr = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                            if (fr && fr.cells[0]) {
                                                window.SkedLeaveBridge.onInitialTopLeaveId(fr.cells[0].innerText.trim());
                                            }
                                        } catch(e) {}

                                        var sel = document.getElementById('ctl00_cphHeading_drpLeaveType');
                                        if (sel) {
                                            if (sel.value === '$leaveTypeCode') {
                                                window.SkedLeaveBridge.onLeaveTypeReady();
                                            } else {
                                                sel.value = '$leaveTypeCode';
                                                if (typeof __doPostBack === 'function') {
                                                    __doPostBack('ctl00${'$'}cphHeading${'$'}drpLeaveType', '');
                                                } else {
                                                    sel.dispatchEvent(new Event('change'));
                                                }
                                            }
                                        } else {
                                            window.SkedLeaveBridge.onLeaveTypeReady();
                                        }
                                    } catch(e) {
                                        window.SkedLeaveBridge.onError('Failed to set leave type: ' + e.toString());
                                    }
                                })();
                            """.trimIndent()
                            webView.evaluateJavascript(jsSetType, null)
                        }

                        1 -> {
                            performSubmit()
                        }

                        2 -> {
                            // Step 3: After submit postback, wait for DOM and alert events to settle, then verify table
                            onStatus("Verifying leave submission...")
                            webView.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                                try {
                                    val unquoted = if (rawHtml.startsWith("\"") && rawHtml.endsWith("\"")) {
                                        org.json.JSONObject("{\"v\":$rawHtml}").getString("v")
                                    } else rawHtml
                                    val file = context.getExternalFilesDir(null)?.resolve("ums_postback_dump.html")
                                    file?.writeText(unquoted)
                                } catch (_: Exception) {}
                            }
                            mainHandler.postDelayed({
                                val safeInitialId = org.json.JSONObject.quote(initialTopLeaveId ?: "")
                                val jsCheckResult = """
                                    (function() {
                                        try {
                                            var initId = $safeInitialId;

                                            // 1. First check if a new Leave record appeared in the table
                                            var firstRow = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                            if (firstRow && firstRow.cells[0]) {
                                                var curLeaveId = (firstRow.cells[0].textContent || firstRow.cells[0].innerText || '').trim();
                                                var curStatus = firstRow.cells[6] ? (firstRow.cells[6].textContent || firstRow.cells[6].innerText || '').trim() : '';
                                                var curApplyDate = firstRow.cells[2] ? (firstRow.cells[2].textContent || firstRow.cells[2].innerText || '').trim() : '';
                                                // If leave ID changed, it is definitely a new leave!
                                                if ((initId && curLeaveId && curLeaveId !== initId) || (!initId && curLeaveId)) {
                                                    return JSON.stringify({
                                                        status: 'table_check',
                                                        leaveId: curLeaveId,
                                                        leaveType: firstRow.cells[1] ? (firstRow.cells[1].textContent || '').trim() : '',
                                                        applyDate: curApplyDate,
                                                        leaveStatus: curStatus
                                                    });
                                                }
                                            }

                                            // 2. Check for OTP modal (RadToolTip1 / txtOTP / OTP Validation)
                                            var otpTip = document.getElementById('ctl00_cphHeading_RadToolTip1') || document.querySelector('[id*="RadToolTip"]');
                                            var txtOtp = document.getElementById('ctl00_cphHeading_txtOTP') || document.querySelector('input[id*="txtOTP" i], input[id*="otp" i]');
                                            var isOtpVisible = false;
                                            if (otpTip && (otpTip.style.display !== 'none' && otpTip.style.visibility !== 'hidden')) {
                                                isOtpVisible = true;
                                            }
                                            if (txtOtp && txtOtp.offsetParent !== null) {
                                                isOtpVisible = true;
                                            }
                                            var bodyText = document.body ? (document.body.innerText || document.body.textContent || '') : '';
                                            if (bodyText.indexOf('OTP Validation') !== -1 || bodyText.indexOf('registered mobile number') !== -1) {
                                                isOtpVisible = true;
                                            }
                                            if (isOtpVisible) {
                                                var searchScope = otpTip ? (otpTip.innerText || otpTip.textContent || '') : bodyText;
                                                var maskedMatch = searchScope.match(/([xX\*]{3,}\d{2,6})/);
                                                if (!maskedMatch) {
                                                    maskedMatch = bodyText.match(/([xX\*]{3,}\d{2,6})/);
                                                }
                                                var phonePrefixMatch = searchScope.match(/(?:mobile(?:\s*number)?|registered\s*mobile(?:\s*number)?)\s*[:\-]?\s*([xX\*\d]{8,15})/i) ||
                                                                       bodyText.match(/(?:mobile(?:\s*number)?|registered\s*mobile(?:\s*number)?)\s*[:\-]?\s*([xX\*\d]{8,15})/i);

                                                var maskedPhone = '';
                                                if (maskedMatch && maskedMatch[1]) {
                                                    maskedPhone = maskedMatch[1].trim();
                                                } else if (phonePrefixMatch && phonePrefixMatch[1]) {
                                                    var candidate = phonePrefixMatch[1].trim();
                                                    if (/[xX\*]/.test(candidate)) {
                                                        maskedPhone = candidate;
                                                    }
                                                }

                                                var canResend = (function() {
                                                    var inputs = document.querySelectorAll('input[type="submit"], input[type="button"], button, a');
                                                    for (var bi = 0; bi < inputs.length; bi++) {
                                                        var val = (inputs[bi].value || inputs[bi].textContent || '').trim().toLowerCase();
                                                        if (val.indexOf('resend') !== -1) return true;
                                                    }
                                                    return !!(document.getElementById('ctl00_cphHeading_btnResendOTP') || 
                                                              document.getElementById('ctl00_cphHeading_btnResend') ||
                                                              document.querySelector('[id*="btnResend" i]') ||
                                                              document.querySelector('[id*="resend" i]'));
                                                })();

                                                var otpError = '';
                                                var otpContainer = otpTip || document;
                                                var redEls = otpContainer.querySelectorAll('span, font, div, p, label');
                                                for (var r = 0; r < redEls.length; r++) {
                                                    var el = redEls[r];
                                                    var txt = (el.textContent || el.innerText || '').trim();
                                                    var tLow = txt.toLowerCase();
                                                    if (tLow.indexOf('invalid') !== -1 || tLow.indexOf('mismatch') !== -1 || tLow.indexOf('wrong') !== -1 || tLow.indexOf('expired') !== -1 || tLow.indexOf('incorrect') !== -1) {
                                                        otpError = txt;
                                                        break;
                                                    }
                                                }

                                                return JSON.stringify({
                                                    status: 'otp_required',
                                                    maskedPhone: maskedPhone,
                                                    canResend: canResend,
                                                    otpError: otpError
                                                });
                                            }

                                            // 3. Check for Telerik RadAlert or dialog content
                                            var radAlert = document.querySelector('.rwDialogText, .rwDialogMessage, .rwContent, .rwWindowContent, .rwDialogPopup, [id*="RadAlert" i], [id*="radalert" i], [id*="RadWindow" i], .RadWindow');
                                            if (radAlert) {
                                                var rt = (radAlert.textContent || radAlert.innerText || '').trim();
                                                if (rt.length > 3 && rt.toLowerCase().indexOf('confirm') === -1) {
                                                    return JSON.stringify({ status: 'error', message: rt });
                                                }
                                            }

                                            // 4. Check all iframes for Telerik dialog content
                                            var iframes = document.querySelectorAll('iframe');
                                            for (var fi = 0; fi < iframes.length; fi++) {
                                                try {
                                                    var idoc = iframes[fi].contentDocument || iframes[fi].contentWindow.document;
                                                    if (idoc && idoc.body) {
                                                        var itxt = (idoc.body.textContent || idoc.body.innerText || '').trim();
                                                        if (itxt && itxt.length > 3 && (itxt.toLowerCase().indexOf('leave') !== -1 || itxt.toLowerCase().indexOf('cannot') !== -1 || itxt.toLowerCase().indexOf('curfew') !== -1 || itxt.toLowerCase().indexOf('not permissible') !== -1 || itxt.toLowerCase().indexOf('greater') !== -1)) {
                                                            return JSON.stringify({ status: 'error', message: itxt });
                                                        }
                                                    }
                                                } catch(e) {}
                                            }

                                            // Helper to ignore global UMS header/Idea Hub elements
                                            function isIgnoredHeaderEl(el) {
                                                if (!el) return false;
                                                if (el.closest && el.closest('#ctl00_WcHeaderforStudent1, [id*="WcHeader" i], [id*="WUCStudentIdea" i], .feedback_heading, #nav, #header, header')) {
                                                    return true;
                                                }
                                                return false;
                                            }

                                            // 4b. Extract verbatim alert messages from server-injected <script> tags
                                            var allScripts = document.querySelectorAll('script');
                                            for (var sci = allScripts.length - 1; sci >= 0; sci--) {
                                                var sc = allScripts[sci];
                                                if (isIgnoredHeaderEl(sc)) continue;
                                                var scRaw = sc.textContent || sc.innerText || '';
                                                if (!/alert\s*\(/i.test(scRaw)) continue;

                                                // Strip single-line and multi-line comments
                                                var scTxt = scRaw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^\:])\/\/.*$/gm, '$1');
                                                if (!/alert\s*\(/i.test(scTxt)) continue;

                                                // Match any alert(...) or radalert(...) with any number of parameters
                                                var alertRegex = /(?:window\.)?(?:rad)?alert\s*\(\s*(['"])((?:(?!\1)[\s\S]|\\.)*?)\1/gi;
                                                var m;
                                                while ((m = alertRegex.exec(scTxt)) !== null) {
                                                    if (m[2]) {
                                                        var alertContent = m[2].replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\n/g, '\n').trim();
                                                        var alLower = alertContent.toLowerCase();
                                                        if (alLower.indexOf('concept note') === -1 &&
                                                            alLower.indexOf('idea hub') === -1 &&
                                                            alLower.indexOf('window.skedleavebridge') === -1 &&
                                                            alLower !== 'hello' &&
                                                            alLower !== 'error' &&
                                                            alertContent.length > 5) {
                                                            if (alLower.indexOf('success') !== -1 || (alLower.indexOf('applied') !== -1 && alLower.indexOf('leave id') !== -1)) {
                                                                return JSON.stringify({ status: 'success', message: alertContent });
                                                            } else {
                                                                return JSON.stringify({ status: 'error', message: alertContent });
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 4c. Check for explicit leave timing / curfew error anywhere in body or scripts
                                            var fullPageText = (document.body ? (document.body.innerText || document.body.textContent || '') : '');
                                            var lrMatch = fullPageText.match(/[^\.\<\>\n\r]{0,60}(?:end time should not greater than|cannot be greater than|should not greater than 09:45|should not greater than 9:45|end time cannot be greater)[^\.\<\>\n\r]{0,60}/i);
                                            if (lrMatch && lrMatch[0]) {
                                                var cleanLr = lrMatch[0].trim();
                                                if (cleanLr.length > 5) {
                                                    return JSON.stringify({ status: 'error', message: cleanLr });
                                                }
                                            }

                                            // 4d. Explicitly check UMS main error label
                                            var mainLblErr = document.getElementById('ctl00_cphHeading_lblerror');
                                            if (mainLblErr) {
                                                var mlt = (mainLblErr.textContent || mainLblErr.innerText || '').trim();
                                                if (mlt.length > 3 && mlt !== '*' && mlt.toLowerCase().indexOf('concept note') === -1) {
                                                    return JSON.stringify({ status: 'error', message: mlt });
                                                }
                                            }

                                            // 5. Check for standard ASP.NET server error spans and labels (scoped outside header)
                                            var errSelectors = [
                                                '[id*="lblerror" i]',
                                                '[id*="lbl_error" i]',
                                                '[id*="lblmsg" i]',
                                                '[id*="lbl_msg" i]',
                                                '[id*="lblmessage" i]',
                                                '[id*="lblwarning" i]',
                                                '[id*="lblalert" i]',
                                                '[id*="lblinfo" i]',
                                                '[id*="lblcurfew" i]',
                                                '[id*="lbltime" i]',
                                                '.text-danger',
                                                '.error-message',
                                                '.alert-danger'
                                            ];
                                            for (var si = 0; si < errSelectors.length; si++) {
                                                var matchingEls = document.querySelectorAll(errSelectors[si]);
                                                for (var mi = 0; mi < matchingEls.length; mi++) {
                                                    var el = matchingEls[mi];
                                                    if (el.tagName === 'A' || el.closest('a') || isIgnoredHeaderEl(el)) continue;
                                                    var t = (el.textContent || el.innerText || '').trim();
                                                    var tLower = t.toLowerCase();
                                                    if (tLower.indexOf('concept note') !== -1 || tLower.indexOf('idea hub') !== -1 ||
                                                        tLower.indexOf('disciplinary') !== -1 || tLower.indexOf('misconduct') !== -1 ||
                                                        tLower.indexOf('rules and regulations') !== -1 || tLower.indexOf('acknowledge') !== -1 ||
                                                        tLower.indexOf('select term') !== -1 || tLower.indexOf('vid') !== -1 || t === '*') {
                                                        continue;
                                                    }
                                                    if (t.length > 3) {
                                                        return JSON.stringify({ status: 'error', message: t });
                                                    }
                                                }
                                            }

                                            // 6. Check for ASP.NET validation summaries (scoped outside header)
                                            var valSummaries = document.querySelectorAll('[id*="ValidationSummary"]');
                                            for (var vs = 0; vs < valSummaries.length; vs++) {
                                                var vSumEl = valSummaries[vs];
                                                if (isIgnoredHeaderEl(vSumEl)) continue;
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

                                            // 7. Check for any failed ASP.NET validators (scoped to leave form group '123')
                                            if (typeof Page_Validators !== 'undefined') {
                                                var failedVals = [];
                                                for (var pv = 0; pv < Page_Validators.length; pv++) {
                                                    var valObj = Page_Validators[pv];
                                                    if (valObj && (!valObj.validationGroup || valObj.validationGroup === '123') && valObj.isvalid === false && valObj.errormessage) {
                                                        failedVals.push(valObj.errormessage);
                                                    }
                                                }
                                                if (failedVals.length > 0) {
                                                    return JSON.stringify({ status: 'error', message: failedVals.join('\n') });
                                                }
                                            }

                                            // 8. Check for any true red text spans or fonts using computed style
                                            function isRealRed(cStr, fCol) {
                                                if (fCol && fCol.toLowerCase() === 'red') return true;
                                                if (!cStr) return false;
                                                if (cStr === 'red') return true;
                                                var m = cStr.match(/rgba?\((\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
                                                if (m) {
                                                    var r = parseInt(m[1], 10);
                                                    var g = parseInt(m[2], 10);
                                                    var b = parseInt(m[3], 10);
                                                    // White is rgb(255, 255, 255). True red must have low green and blue channels!
                                                    return (r >= 160 && g < 100 && b < 100);
                                                }
                                                return false;
                                            }

                                            var allRedEls = document.querySelectorAll('span, div, p, font, td, label');
                                            for (var r = 0; r < allRedEls.length; r++) {
                                                var redEl = allRedEls[r];
                                                if (redEl.tagName === 'A' || redEl.closest('a') || isIgnoredHeaderEl(redEl)) continue;
                                                if (redEl.children.length > 0) continue;
                                                var cStyle = window.getComputedStyle(redEl).color || '';
                                                var fCol = redEl.getAttribute('color') || '';
                                                if (isRealRed(cStyle, fCol)) {
                                                    var redTxt = (redEl.textContent || redEl.innerText || '').trim();
                                                    var rLower = redTxt.toLowerCase();
                                                    if (rLower.indexOf('concept note') !== -1 ||
                                                        rLower.indexOf('idea hub') !== -1 ||
                                                        rLower.indexOf('disciplinary') !== -1 ||
                                                        rLower.indexOf('misconduct') !== -1 ||
                                                        rLower.indexOf('hostel leave application') !== -1 ||
                                                        rLower.indexOf('submitting this') !== -1 ||
                                                        rLower.indexOf('acknowledge') !== -1 ||
                                                        rLower.indexOf('rules and regulations') !== -1 ||
                                                        rLower.indexOf('click here') !== -1 ||
                                                        rLower.indexOf('download') !== -1) {
                                                        continue;
                                                    }
                                                    if (redTxt && redTxt.length > 3 && redTxt !== '*' && !redTxt.includes('VID') && !redTxt.includes('Select')) {
                                                        return JSON.stringify({ status: 'error', message: redTxt });
                                                    }
                                                }
                                            }

                                            // 9. Leave table check if unchanged
                                            if (firstRow) {
                                                var leaveId = firstRow.cells[0] ? (firstRow.cells[0].textContent || '').trim() : '';
                                                var leaveType = firstRow.cells[1] ? (firstRow.cells[1].textContent || '').trim() : '';
                                                var applyDate = firstRow.cells[2] ? (firstRow.cells[2].textContent || '').trim() : '';
                                                var status = firstRow.cells[6] ? (firstRow.cells[6].textContent || '').trim() : 'Approved';
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

                                webView.evaluateJavascript(jsCheckResult) { res ->
                                    try {
                                        val cleanJson = if (res.startsWith("\"") && res.endsWith("\"")) {
                                            org.json.JSONObject("{\"v\":$res}").getString("v")
                                        } else res
                                        val obj = org.json.JSONObject(cleanJson)
                                        val status = obj.optString("status")

                                        when (status) {
                                            "success" -> {
                                                val msg = obj.optString("message", "Leave Applied Successfully")
                                                finishWith(Result.success(msg))
                                            }
                                            "error" -> {
                                                val msg = obj.optString("message", "Submission rejected by UMS")
                                                finishWith(Result.failure(Exception(msg)))
                                            }
                                            "otp_required" -> {
                                                val maskedPhone = obj.optString("maskedPhone", "")
                                                val canResend = obj.optBoolean("canResend", false)
                                                val otpError = obj.optString("otpError", "")

                                                if (maskedPhone.isNotBlank()) {
                                                    savedMaskedPhone = maskedPhone
                                                }
                                                val phoneToReport = if (savedMaskedPhone.isNotBlank()) savedMaskedPhone else maskedPhone

                                                stage = 3 // Stage 3: Waiting for user OTP entry

                                                if (otpError.isNotBlank()) {
                                                    onOtpError?.invoke(otpError, canResend)
                                                }

                                                if (onOtpRequired != null) {
                                                    onStatus("Parent verification OTP required...")
                                                    timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                                                    timeoutRunnable = Runnable {
                                                        finishWith(Result.failure(Exception("Parent OTP entry timed out. Please try again.")))
                                                    }
                                                    mainHandler.postDelayed(timeoutRunnable!!, 180000)

                                                    val submitOtpFn: (String) -> Unit = { enteredOtp ->
                                                        stage = 4 // Stage 4: Submitting OTP postback
                                                        onStatus("Validating OTP with UMS...")
                                                        val safeOtp = org.json.JSONObject.quote(enteredOtp.trim().take(4))
                                                        val jsSubmitOtp = """
                                                            (function() {
                                                                try {
                                                                    var txt = document.getElementById('ctl00_cphHeading_txtOTP') || 
                                                                              document.querySelector('input[id*="txtOTP" i], input[id*="otp" i]');
                                                                    if (txt) {
                                                                        txt.value = $safeOtp;
                                                                        txt.dispatchEvent(new Event('input', { bubbles: true }));
                                                                        txt.dispatchEvent(new Event('change', { bubbles: true }));
                                                                    }

                                                                    var theForm = document.forms['aspnetForm'] || document.querySelector('form');
                                                                    if (theForm) {
                                                                        if (typeof Page_BlockSubmit !== 'undefined') Page_BlockSubmit = false;
                                                                        if (typeof Page_ValidationActive !== 'undefined') Page_ValidationActive = false;

                                                                        var ex = document.getElementById('__sked_btnValidate');
                                                                        if (!ex) {
                                                                            var h = document.createElement('input');
                                                                            h.type = 'hidden';
                                                                            h.id = '__sked_btnValidate';
                                                                            h.name = 'ctl00${'$'}cphHeading${'$'}btnValidate';
                                                                            h.value = 'Validate OTP';
                                                                            theForm.appendChild(h);
                                                                        }

                                                                        if (theForm.__EVENTTARGET) {
                                                                            theForm.__EVENTTARGET.value = 'ctl00${'$'}cphHeading${'$'}btnValidate';
                                                                        }
                                                                        if (theForm.__EVENTARGUMENT) {
                                                                            theForm.__EVENTARGUMENT.value = '';
                                                                        }

                                                                        var btn = document.getElementById('ctl00_cphHeading_btnValidate');
                                                                        if (btn) {
                                                                            try { btn.click(); } catch(e) {}
                                                                        }
                                                                        theForm.submit();
                                                                        return 'submitted';
                                                                    }
                                                                    return 'no_form';
                                                                } catch(e) {
                                                                    return 'error: ' + e.toString();
                                                                }
                                                            })();
                                                        """.trimIndent()
                                                        webView.evaluateJavascript(jsSubmitOtp) { subRes ->
                                                            android.util.Log.i("SkedLeave", "Submit OTP result: $subRes")
                                                        }

                                                        var otpPollCount = 0
                                                        val pollOtpRunnable = object : Runnable {
                                                            override fun run() {
                                                                if (isResumed || stage != 4) return
                                                                otpPollCount++
                                                                webView.evaluateJavascript(jsCheckResult) { checkRes ->
                                                                    if (isResumed || stage != 4) return@evaluateJavascript
                                                                    try {
                                                                        val cJson = if (checkRes.startsWith("\"") && checkRes.endsWith("\"")) {
                                                                            org.json.JSONObject("{\"v\":$checkRes}").getString("v")
                                                                        } else checkRes
                                                                        val cObj = org.json.JSONObject(cJson)
                                                                        val curStatus = cObj.optString("status")
                                                                        if (curStatus == "table_check") {
                                                                            val curLeaveId = cObj.optString("leaveId")
                                                                            val curLStatus = cObj.optString("leaveStatus", "Existing")
                                                                            val isDiff = initialTopLeaveId != null && curLeaveId.isNotBlank() && curLeaveId != initialTopLeaveId
                                                                            val isFirst = initialTopLeaveId.isNullOrBlank() && curLeaveId.isNotBlank()
                                                                            if (isDiff || isFirst || curLStatus.equals("Applied", true) || curLStatus.equals("Approved", true)) {
                                                                                finishWith(Result.success("Leave ID #$curLeaveId"))
                                                                                return@evaluateJavascript
                                                                            }
                                                                        } else if (curStatus == "otp_required") {
                                                                            val err = cObj.optString("otpError")
                                                                            val resendAvail = cObj.optBoolean("canResend", false)
                                                                            if (err.isNotBlank() || resendAvail) {
                                                                                stage = 3
                                                                                val finalErr = if (err.isNotBlank()) err else "Incorrect OTP. Please enter valid OTP or tap Resend OTP."
                                                                                onOtpError?.invoke(finalErr, resendAvail)
                                                                                return@evaluateJavascript
                                                                            }
                                                                        } else if (curStatus == "error") {
                                                                            val errMsg = cObj.optString("message")
                                                                            if (isOtpError(errMsg)) {
                                                                                stage = 3
                                                                                onOtpError?.invoke(errMsg, true)
                                                                                return@evaluateJavascript
                                                                            } else {
                                                                                finishWith(Result.failure(Exception(errMsg)))
                                                                                return@evaluateJavascript
                                                                            }
                                                                        }
                                                                    } catch (_: Exception) {}
                                                                    if (!isResumed && stage == 4 && otpPollCount < 15) {
                                                                        mainHandler.postDelayed(this, 1500)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        mainHandler.postDelayed(pollOtpRunnable, 2000)
                                                    }

                                                    val resendOtpFn: () -> Unit = {
                                                        stage = 5 // Stage 5: Resending OTP
                                                        onStatus("Requesting new OTP from UMS...")
                                                        val jsResend = """
                                                            (function() {
                                                                try {
                                                                    var theForm = document.forms['aspnetForm'] || document.querySelector('form');
                                                                    if (typeof Page_BlockSubmit !== 'undefined') Page_BlockSubmit = false;
                                                                    if (typeof Page_ValidationActive !== 'undefined') Page_ValidationActive = false;

                                                                    var resendEl = (function() {
                                                                        var inputs = document.querySelectorAll('input[type="submit"], input[type="button"], button, a');
                                                                        for (var bi = 0; bi < inputs.length; bi++) {
                                                                            var val = (inputs[bi].value || inputs[bi].textContent || '').trim().toLowerCase();
                                                                            if (val.indexOf('resend') !== -1) return inputs[bi];
                                                                        }
                                                                        return document.getElementById('ctl00_cphHeading_btnResendOTP') ||
                                                                               document.getElementById('ctl00_cphHeading_btnResend') ||
                                                                               document.querySelector('[id*="btnResend" i]') ||
                                                                               document.querySelector('[id*="resend" i]');
                                                                    })();

                                                                    if (resendEl) {
                                                                        var targetName = resendEl.name || resendEl.id || 'ctl00${'$'}cphHeading${'$'}btnResendOTP';
                                                                        if (theForm) {
                                                                            var ex = document.getElementById('__sked_btnResend');
                                                                            if (!ex) {
                                                                                var h = document.createElement('input');
                                                                                h.type = 'hidden';
                                                                                h.id = '__sked_btnResend';
                                                                                h.name = targetName;
                                                                                h.value = resendEl.value || 'Resend OTP';
                                                                                theForm.appendChild(h);
                                                                            }
                                                                            if (theForm.__EVENTTARGET) {
                                                                                theForm.__EVENTTARGET.value = targetName;
                                                                            }
                                                                            if (theForm.__EVENTARGUMENT) {
                                                                                theForm.__EVENTARGUMENT.value = '';
                                                                            }
                                                                        }
                                                                        try { resendEl.click(); } catch(e) {}
                                                                        if (theForm) theForm.submit();
                                                                        return 'resend_submitted';
                                                                    }
                                                                    return 'resend_btn_not_found';
                                                                } catch(e) {
                                                                    return 'error: ' + e.toString();
                                                                }
                                                            })();
                                                        """.trimIndent()
                                                        webView.evaluateJavascript(jsResend) { r ->
                                                            android.util.Log.i("SkedLeave", "Resend OTP executed: $r")
                                                        }

                                                        var resendPollCount = 0
                                                        val pollResendRunnable = object : Runnable {
                                                            override fun run() {
                                                                if (isResumed || stage != 5) return
                                                                resendPollCount++
                                                                webView.evaluateJavascript(jsCheckResult) { checkRes ->
                                                                    if (isResumed || stage != 5) return@evaluateJavascript
                                                                    try {
                                                                        val cJson = if (checkRes.startsWith("\"") && checkRes.endsWith("\"")) {
                                                                            org.json.JSONObject("{\"v\":$checkRes}").getString("v")
                                                                        } else checkRes
                                                                        val cObj = org.json.JSONObject(cJson)
                                                                        val curStatus = cObj.optString("status")
                                                                        if (curStatus == "otp_required") {
                                                                            stage = 3
                                                                            onOtpResent?.invoke()
                                                                            onStatus("Parent verification OTP required...")
                                                                            return@evaluateJavascript
                                                                        }
                                                                    } catch (_: Exception) {}
                                                                    if (!isResumed && stage == 5 && resendPollCount < 10) {
                                                                        mainHandler.postDelayed(this, 1500)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        mainHandler.postDelayed(pollResendRunnable, 2000)
                                                    }

                                                    onOtpRequired.invoke(
                                                        phoneToReport,
                                                        submitOtpFn,
                                                        resendOtpFn,
                                                        {
                                                            finishWith(Result.failure(Exception("Leave application cancelled by user.")))
                                                        }
                                                    )
                                                } else {
                                                    finishWith(Result.failure(Exception("UMS requires Parent OTP verification for this leave. Please enter the OTP sent to your parent's phone.")))
                                                }
                                            }
                                            "table_check" -> {
                                                val leaveId = obj.optString("leaveId")
                                                val leaveStatus = obj.optString("leaveStatus", "Existing")
                                                val isDifferentId = initialTopLeaveId != null && leaveId.isNotBlank() && leaveId != initialTopLeaveId
                                                val isFirstEverLeave = initialTopLeaveId.isNullOrBlank() && leaveId.isNotBlank()

                                                val dfYmd = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                val dfMdy = java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
                                                val dfDmy = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                                                val todayDate = java.util.Date()
                                                val applyDateRaw = obj.optString("applyDate", "").trim()
                                                val dateMatches = applyDateRaw.isNotBlank() && (
                                                    applyDateRaw.contains(dfYmd.format(todayDate)) ||
                                                    applyDateRaw.contains(dfMdy.format(todayDate)) ||
                                                    applyDateRaw.contains(dfDmy.format(todayDate))
                                                )
                                                val isStatusNew = leaveStatus.equals("Applied", true) || leaveStatus.equals("Approved", true)

                                                if (isDifferentId || isFirstEverLeave || (dateMatches && isStatusNew)) {
                                                    // New leave ID successfully posted to UMS
                                                    finishWith(Result.success("Leave ID #$leaveId"))
                                                } else {
                                                    // Leave ID is unchanged: submission was NOT accepted by UMS
                                                    val alertMsg = capturedAlert
                                                    if (!alertMsg.isNullOrBlank()) {
                                                        finishWith(Result.failure(Exception(alertMsg)))
                                                    } else {
                                                        val nowMillis = System.currentTimeMillis()
                                                        val isPastDeparture = startCal.timeInMillis < (nowMillis - 60_000)
                                                        val dfYmd = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                        val dfMdy = java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
                                                        val dfDmy = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
                                                        val todayDate = java.util.Date()
                                                        val applyDateRaw = obj.optString("applyDate", "").trim()
                                                        val hasExistingLeaveToday = applyDateRaw.contains(dfYmd.format(todayDate)) ||
                                                                applyDateRaw.contains(dfMdy.format(todayDate)) ||
                                                                applyDateRaw.contains(dfDmy.format(todayDate))

                                                        val isWinter = isWinterCurfew()
                                                        val endH = endCal.get(java.util.Calendar.HOUR_OF_DAY)
                                                        val endM = endCal.get(java.util.Calendar.MINUTE)
                                                        val isDayLeave = leaveTypeCode == "6" || leaveTypeCode == "3"
                                                        val exceedsCurfew = if (isDayLeave) {
                                                            if (leaveTypeCode == "6") { // Extended
                                                                if (isWinter) (endH > 21 || (endH == 21 && endM > 45)) else (endH > 22 || (endH == 22 && endM > 45))
                                                            } else { // Standard
                                                                (endH > 20 || (endH == 20 && endM > 0))
                                                            }
                                                        } else false

                                                        val detailedReason = when {
                                                            isPastDeparture -> {
                                                                val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(startCal.time)
                                                                "Departure time ($timeStr) is in the past. UMS rules require departure time to be later than current time."
                                                            }
                                                            exceedsCurfew -> {
                                                                val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(endCal.time)
                                                                val curfewLimit = if (leaveTypeCode == "6") {
                                                                    if (isWinter) "09:45 PM (Winter timing)" else "10:45 PM"
                                                                } else "08:00 PM"
                                                                "End Time Should Not Greater than $curfewLimit. Your selected return time was $timeStr."
                                                            }
                                                            hasExistingLeaveToday && leaveId.isNotBlank() -> {
                                                                "A leave application (#$leaveId) already exists for today with status '$leaveStatus'. UMS does not allow multiple leave applications on the same date."
                                                            }
                                                            else -> {
                                                                "Leave was not applied by UMS. Please verify departure time is in the future, return time is within hostel curfew, and no conflicting leave exists for this date."
                                                            }
                                                        }
                                                        finishWith(Result.failure(Exception(detailedReason)))
                                                    }
                                                }
                                            }
                                            else -> {
                                                val alertMsg = capturedAlert
                                                if (!alertMsg.isNullOrBlank()) {
                                                    if (isSuccessMessage(alertMsg)) {
                                                        finishWith(Result.success(alertMsg))
                                                    } else {
                                                        finishWith(Result.failure(Exception(alertMsg)))
                                                    }
                                                } else {
                                                    finishWith(Result.failure(Exception(
                                                        "Leave submission could not be confirmed by UMS. Please check your leave history."
                                                    )))
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        finishWith(Result.failure(Exception("Failed to verify submission: ${e.message}")))
                                    }
                                }
                            }, 600)
                        }

                        3, 4, 5 -> {
                            // OTP interaction in progress; verification handled by pollOtpRunnable / pollResendRunnable
                        }
                    }
                }
            }

            // Launch initial load
            onStatus("Connecting to UMS hostel portal...")
            webView.loadUrl(LEAVE_URL)

            // 25-second timeout safety net via main looper
            timeoutRunnable = Runnable {
                finishWith(Result.failure(Exception("Submission timed out. Please check your connection or UMS portal.")))
            }
            mainHandler.postDelayed(timeoutRunnable!!, 25000)
        }
    }
}
