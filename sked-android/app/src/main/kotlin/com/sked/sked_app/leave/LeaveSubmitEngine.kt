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

    /**
     * Fast-path: Fetches leave balance & past history via OkHttpClient with session cookies.
     * Takes ~300ms, completely invisible.
     */
    suspend fun fetchLatestData(context: Context): LeaveSyncResult = withContext(Dispatchers.IO) {
        try {
            val cm = CookieManager.getInstance()
            var cookies = cm.getCookie("https://ums.lpu.in") ?: ""
            if (cookies.isBlank()) {
                val prefs = context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
                cookies = prefs.getString(com.sked.sked_app.AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""
            }
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code in 300..399) {
                    val location = resp.header("Location") ?: ""
                    if (location.contains("Login", ignoreCase = true)) {
                        return@withContext LeaveSyncResult.SessionExpired
                    }
                }
                val html = resp.body?.string() ?: ""
                if (html.contains("LoginNew.aspx", ignoreCase = true) || html.contains("Login.aspx", ignoreCase = true)) {
                    return@withContext LeaveSyncResult.SessionExpired
                }
                if (html.isNotBlank()) {
                    val parsed = LeaveParser.parse(html)
                    if (parsed.balance.dayTotal > 0 || parsed.records.isNotEmpty()) {
                        LeaveStorage.saveBalance(context, parsed.balance)
                        LeaveStorage.saveProfile(context, parsed.profile)
                        LeaveStorage.saveRecords(context, parsed.records)
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
        onStatus: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isResumed = false
            fun finishWith(result: Result<String>) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(result)
                }
            }

            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            // Ensure CookieManager has the latest saved UMS session cookies
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            val prefs = context.getSharedPreferences(com.sked.sked_app.widget.PREFS_NAME, Context.MODE_PRIVATE)
            val savedCookies = prefs.getString(com.sked.sked_app.AutoSyncManager.KEY_UMS_COOKIES, "") ?: ""
            if (savedCookies.isNotBlank()) {
                for (cookie in savedCookies.split(";")) {
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
            var stage = 0 // 0: initial load, 1: leaveType set postback, 2: visitPlace set postback, 3: submitted

            class LeaveJsBridge {
                @JavascriptInterface
                fun onInitialTopLeaveId(id: String) {
                    if (initialTopLeaveId == null && id.isNotBlank()) {
                        initialTopLeaveId = id.trim()
                        android.util.Log.i("SkedLeave", "Recorded initial top leave ID: '$initialTopLeaveId'")
                    }
                }

                @JavascriptInterface
                fun onAlert(msg: String) {
                    android.util.Log.i("SkedLeave", "Intercepted UMS Alert via bridge: $msg")
                    capturedAlert = msg
                    if (stage >= 2) {
                        if (msg.contains("success", ignoreCase = true) || msg.contains("applied", ignoreCase = true)) {
                            finishWith(Result.success(msg))
                        } else {
                            finishWith(Result.failure(Exception(msg)))
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
                    val msg = message ?: "Unknown alert"
                    android.util.Log.i("SkedLeave", "WebChromeClient intercepted alert: $msg")
                    capturedAlert = msg
                    result?.confirm()
                    if (stage >= 2) {
                        if (msg.contains("success", ignoreCase = true) || msg.contains("applied", ignoreCase = true)) {
                            finishWith(Result.success(msg))
                        } else {
                            finishWith(Result.failure(Exception(msg)))
                        }
                    }
                    return true
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null && (url.contains("LoginNew.aspx", ignoreCase = true) || url.contains("Login.aspx", ignoreCase = true))) {
                        finishWith(Result.failure(Exception("UMS session expired. Please re-authenticate.")))
                        return
                    }
                    if (url == null || !url.contains("frmStudentHostelLeaveApplicationTermWise")) return

                    // Check if an alert was captured on submission
                    if (capturedAlert != null) {
                        val alertMsg = capturedAlert ?: ""
                        if (alertMsg.contains("success", ignoreCase = true) || alertMsg.contains("applied", ignoreCase = true)) {
                            finishWith(Result.success(alertMsg))
                            return
                        } else {
                            finishWith(Result.failure(Exception(alertMsg)))
                            return
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
                                        window.confirm = function() { return true; };

                                        // Capture existing top leave ID
                                        try {
                                            var fr = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                            if (fr && fr.cells[0]) {
                                                window.SkedLeaveBridge.onInitialTopLeaveId(fr.cells[0].innerText.trim());
                                            }
                                        } catch(e) {}

                                        var sel = document.getElementById('ctl00_cphHeading_drpLeaveType');
                                        if (sel) {
                                            sel.value = '$leaveTypeCode';
                                            if (typeof __doPostBack === 'function') {
                                                __doPostBack('ctl00${'$'}cphHeading${'$'}drpLeaveType', '');
                                            } else {
                                                sel.dispatchEvent(new Event('change'));
                                            }
                                        }
                                    } catch(e) {
                                        window.SkedLeaveBridge.onError('Failed to set leave type: ' + e.toString());
                                    }
                                })();
                            """.trimIndent()
                            webView.evaluateJavascript(jsSetType, null)
                        }

                        1 -> {
                            // Step 2: Set Visit Place dropdown and trigger ASP.NET postback
                            onStatus("Setting destination...")
                            stage = 2
                            val jsSetVisit = """
                                (function() {
                                    try {
                                        window.alert = function(msg) { window.SkedLeaveBridge.onAlert(msg); };
                                        window.confirm = function() { return true; };

                                        var vp = document.getElementById('ctl00_cphHeading_ddlVisitDay');
                                        if (vp) {
                                            vp.value = '$visitPlace';
                                            if (typeof __doPostBack === 'function') {
                                                __doPostBack('ctl00${'$'}cphHeading${'$'}ddlVisitDay', '');
                                            } else {
                                                vp.dispatchEvent(new Event('change'));
                                            }
                                        }
                                    } catch(e) {
                                        window.SkedLeaveBridge.onError('Failed to set visit place: ' + e.toString());
                                    }
                                })();
                            """.trimIndent()
                            webView.evaluateJavascript(jsSetVisit, null)
                        }

                        2 -> {
                            // Step 3: Populate all form fields, datepickers, declaration checkbox and submit
                            onStatus("Submitting leave application...")
                            stage = 3
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
                                        window.alert = function(msg) { window.SkedLeaveBridge.onAlert(msg); };
                                        // Leave Term Check
                                        var ddlTerm = document.getElementById('ctl00_cphHeading_ddlLeaveTerm');
                                        if (ddlTerm && (ddlTerm.value === '0' || !ddlTerm.value) && ddlTerm.options.length > 1) {
                                            ddlTerm.selectedIndex = 1;
                                        }

                                        // Relative Mobile
                                        var mob = document.getElementById('ctl00_cphHeading_txtVisitingMobile');
                                        if (mob) { mob.value = $safeMobile; mob.dispatchEvent(new Event('input')); }

                                        // Stay Address: Multiple robust matching strategies
                                        // 1. Check all validators for any mentioning 'stay' or 'address'
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

                                        // 2. Check any input/textarea in rows between ddlVisitDay and txtVisitingMobile
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

                                        // 3. Fallback: Check all inputs for common address keywords (case-insensitive)
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

                                        // Re-run validation on validators to clear client errors
                                        if (typeof Page_Validators !== 'undefined') {
                                            for (var vi = 0; vi < Page_Validators.length; vi++) {
                                                var valItem = Page_Validators[vi];
                                                if (valItem && typeof ValidatorValidate === 'function') {
                                                    ValidatorValidate(valItem);
                                                }
                                            }
                                        }

                                        // Dates & Times Helper (Telerik RadDateTimePicker + inputs + clientState)
                                        function setRadPicker(baseId, y, m, d, h, min, textVal, isoVal) {
                                            try {
                                                var p = (typeof ${'$'}find === 'function') ? ${'$'}find(baseId) : null;
                                                if (p && typeof p.set_selectedDate === 'function') {
                                                    p.set_selectedDate(new Date(y, m, d, h, min, 0));
                                                }
                                            } catch(e) {}

                                            var di = document.getElementById(baseId + '_dateInput');
                                            if (di) {
                                                di.value = textVal;
                                                di.dispatchEvent(new Event('input', { bubbles: true }));
                                                di.dispatchEvent(new Event('change', { bubbles: true }));
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

                                        // Reason
                                        var txtReason = document.getElementById('ctl00_cphHeading_txtLeaveReason');
                                        if (txtReason) { txtReason.value = $safeReason; txtReason.dispatchEvent(new Event('input')); }

                                        // Declaration Checkbox
                                        var chk = document.getElementById('ctl00_cphHeading_chkOption');
                                        if (chk) chk.checked = true;

                                        // Optional client-side validation check
                                        if (typeof Page_ClientValidate === 'function') {
                                            var valid = Page_ClientValidate('123');
                                            if (!valid) {
                                                var valMsgs = [];
                                                if (typeof Page_Validators !== 'undefined') {
                                                    for (var vi = 0; vi < Page_Validators.length; vi++) {
                                                        var vItem = Page_Validators[vi];
                                                        if (vItem && !vItem.isvalid && vItem.errormessage) {
                                                            valMsgs.push(vItem.errormessage);
                                                        }
                                                    }
                                                }
                                                var combined = valMsgs.join('\n');
                                                window.SkedLeaveBridge.onError(combined || 'Validation failed. Please verify form details.');
                                                return;
                                            }
                                        }

                                        // Click Submit Button
                                        var btn = document.getElementById('ctl00_cphHeading_btnSubmit');
                                        if (btn) {
                                            setTimeout(function() { btn.click(); }, 350);
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

                        3 -> {
                            // Step 4: After submit postback, wait for DOM and alert events to settle, then verify table
                            onStatus("Verifying leave submission...")
                            webView.postDelayed({
                                val jsCheckResult = """
                                    (function() {
                                        try {
                                            // 1. Check for server error message span
                                            var lblErr = document.getElementById('ctl00_cphHeading_lblerror') ||
                                                          document.getElementById('ctl00_cphHeading_lblError');
                                            if (lblErr && lblErr.innerText && lblErr.innerText.trim().length > 0) {
                                                return JSON.stringify({ status: 'error', message: lblErr.innerText.trim() });
                                            }

                                            // 2. Check for OTP modal (RadToolTip1)
                                            var otpTip = document.getElementById('ctl00_cphHeading_RadToolTip1');
                                            var txtOtp = document.getElementById('ctl00_cphHeading_txtOTP');
                                            var isOtpVisible = false;
                                            if (otpTip && (otpTip.style.display !== 'none' && otpTip.style.visibility !== 'hidden')) {
                                                isOtpVisible = true;
                                            }
                                            if (txtOtp && txtOtp.offsetParent !== null) {
                                                isOtpVisible = true;
                                            }
                                            if (isOtpVisible) {
                                                return JSON.stringify({ status: 'error', message: 'UMS requires OTP validation for this leave. Please apply directly through UMS portal.' });
                                            }

                                            // 3. Check for ASP.NET validation summary
                                            var valSummary = document.getElementById('ctl00_cphHeading_ValidationSummary1');
                                            if (valSummary && valSummary.innerText && valSummary.innerText.trim().length > 0 && valSummary.style.display !== 'none') {
                                                return JSON.stringify({ status: 'error', message: valSummary.innerText.trim() });
                                            }

                                            // 4. Check for top row in leave table
                                            var firstRow = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                            if (firstRow) {
                                                var leaveId = firstRow.cells[0] ? firstRow.cells[0].innerText.trim() : '';
                                                var leaveType = firstRow.cells[1] ? firstRow.cells[1].innerText.trim() : '';
                                                var applyDate = firstRow.cells[2] ? firstRow.cells[2].innerText.trim() : '';
                                                var status = firstRow.cells[6] ? firstRow.cells[6].innerText.trim() : 'Approved';
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
                                            "error" -> {
                                                val msg = obj.optString("message", "Submission rejected by UMS")
                                                finishWith(Result.failure(Exception(msg)))
                                            }
                                            "table_check" -> {
                                                val leaveId = obj.optString("leaveId")
                                                val isDifferentId = initialTopLeaveId != null && leaveId.isNotBlank() && leaveId != initialTopLeaveId
                                                val isFirstEverLeave = initialTopLeaveId.isNullOrBlank() && leaveId.isNotBlank()

                                                if (isDifferentId || isFirstEverLeave) {
                                                    // New leave ID successfully posted to UMS
                                                    finishWith(Result.success("Leave ID #$leaveId"))
                                                } else {
                                                    // Leave ID is unchanged: submission was NOT accepted by UMS
                                                    val alertMsg = capturedAlert
                                                    if (!alertMsg.isNullOrBlank()) {
                                                        finishWith(Result.failure(Exception(alertMsg)))
                                                    } else {
                                                        finishWith(Result.failure(Exception(
                                                            "Leave was not applied by UMS. Please verify departure and return timings are valid and within permissible hostel curfew hours."
                                                        )))
                                                    }
                                                }
                                            }
                                            else -> {
                                                val alertMsg = capturedAlert
                                                if (!alertMsg.isNullOrBlank()) {
                                                    if (alertMsg.contains("success", ignoreCase = true) || alertMsg.contains("applied", ignoreCase = true)) {
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
                    }
                }
            }

            // Launch initial load
            onStatus("Connecting to UMS hostel portal...")
            webView.loadUrl(LEAVE_URL)

            // 20-second timeout safety net
            webView.postDelayed({
                finishWith(Result.failure(Exception("Submission timed out. Please check your connection or UMS portal.")))
            }, 20000)
        }
    }
}
