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

object LeaveSubmitEngine {

    const val LEAVE_URL = "https://ums.lpu.in/lpuums/frmStudentHostelLeaveApplicationTermWise.aspx"

    /**
     * Fast-path: Fetches leave balance & past history via OkHttpClient with session cookies.
     * Takes ~300ms, completely invisible.
     */
    suspend fun fetchLatestData(context: Context): LeaveParser.ParsedLeaveData = withContext(Dispatchers.IO) {
        try {
            val cm = CookieManager.getInstance()
            val cookies = cm.getCookie("https://ums.lpu.in") ?: ""
            if (cookies.isBlank()) return@withContext LeaveParser.ParsedLeaveData()

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url(LEAVE_URL)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                val html = resp.body?.string() ?: ""
                if (html.isNotBlank() && !html.contains("LoginNew.aspx")) {
                    val parsed = LeaveParser.parse(html)
                    LeaveStorage.saveBalance(context, parsed.balance)
                    LeaveStorage.saveProfile(context, parsed.profile)
                    LeaveStorage.saveRecords(context, parsed.records)
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SkedLeave", "fetchLatestData error: ${e.message}")
        }
        return@withContext LeaveParser.ParsedLeaveData(
            balance = LeaveStorage.loadBalance(context),
            profile = LeaveStorage.loadProfile(context),
            records = LeaveStorage.loadRecords(context)
        )
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

            var capturedAlert: String? = null
            var capturedError: String? = null
            var stage = 0 // 0: initial load, 1: leaveType set postback, 2: visitPlace set postback, 3: submitted

            class LeaveJsBridge {
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
                            // Step 1: Set Leave Type dropdown and trigger ASP.NET postback
                            onStatus("Configuring leave type...")
                            stage = 1
                            val jsSetType = """
                                (function() {
                                    try {
                                        window.alert = function(msg) { window.SkedLeaveBridge.onAlert(msg); };
                                        window.confirm = function() { return true; };

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

                                        // Stay Address (if textarea or input present)
                                        var addrInputs = document.querySelectorAll('textarea, input[type="text"]');
                                        for (var i = 0; i < addrInputs.length; i++) {
                                            var id = addrInputs[i].id || '';
                                            if (id.indexOf('txtStay') !== -1 || id.indexOf('Address') !== -1 || id.indexOf('Stay') !== -1) {
                                                addrInputs[i].value = $safeStayAddr;
                                                addrInputs[i].dispatchEvent(new Event('input'));
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
                            // Step 4: After submit postback, check table or error messages
                            val jsCheckResult = """
                                (function() {
                                    try {
                                        // 1. Check for server error message spans or validation summaries
                                        var errSpan = document.getElementById('ctl00_cphHeading_lblerror') ||
                                                      document.getElementById('ctl00_cphHeading_lblError') ||
                                                      document.getElementById('ctl00_cphHeading_ValidationSummary1') ||
                                                      document.querySelector('.error, .alert-danger, [style*="color:Red"], [style*="color: red"]');
                                        if (errSpan && errSpan.innerText && errSpan.innerText.trim().length > 0) {
                                            return JSON.stringify({ status: 'error', message: errSpan.innerText.trim() });
                                        }

                                        // 2. Check for latest leave ID in history table
                                        var firstRow = document.querySelector('#ctl00_cphHeading_grdLeaveDetail tr:nth-child(2)');
                                        if (firstRow) {
                                            var leaveId = firstRow.cells[0] ? firstRow.cells[0].innerText.trim() : '';
                                            var leaveType = firstRow.cells[1] ? firstRow.cells[1].innerText.trim() : '';
                                            var status = firstRow.cells[6] ? firstRow.cells[6].innerText.trim() : 'Approved';
                                            return JSON.stringify({ status: 'success', leaveId: leaveId, leaveType: leaveType, leaveStatus: status });
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
                                    if (status == "success") {
                                        val id = obj.optString("leaveId")
                                        finishWith(Result.success(if (id.isNotBlank()) "Leave ID #$id" else "Leave submitted"))
                                    } else if (status == "error") {
                                        val msg = obj.optString("message", "Submission rejected by UMS")
                                        finishWith(Result.failure(Exception(msg)))
                                    } else {
                                        // If an alert was captured, use it
                                        if (capturedAlert != null) {
                                            finishWith(Result.success(capturedAlert!!))
                                        } else {
                                            finishWith(Result.success("Leave application submitted!"))
                                        }
                                    }
                                } catch (e: Exception) {
                                    finishWith(Result.success("Leave application submitted!"))
                                }
                            }
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
