package com.sked.sked_app.leave

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.sked.sked_app.BarlowCondensed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Theme Tokens
private val Ink = Color(0xFF0D0D0D)
private val Slab = Color(0xFF141414)
private val SlabElevated = Color(0xFF1A1A1A)
private val Rule = Color(0xFF242424)
private val Blaze = Color(0xFFFF5722)
private val Chalk = Color(0xFFF2EFE9)
private val Slate = Color(0xFF8A8780)
private val SuccessGreen = Color(0xFF22C55E)
private val DangerRed = Color(0xFFEF4444)

data class UmsTimeSlot(val hour: Int, val minute: Int) {
    val display: String
        get() {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            return SimpleDateFormat("h:mm a", Locale.US).format(c.time)
        }
}

// Generate exact 15-minute slots matching UMS RadTimeView
// Departure: 72 slots (5:00 AM to 10:45 PM)
val departureTimeSlots: List<UmsTimeSlot> by lazy {
    val list = mutableListOf<UmsTimeSlot>()
    for (h in 5..22) {
        for (m in listOf(0, 15, 30, 45)) {
            list.add(UmsTimeSlot(h, m))
        }
    }
    list
}

// Return: 76 slots (5:00 AM to 11:45 PM)
val returnTimeSlots: List<UmsTimeSlot> by lazy {
    val list = mutableListOf<UmsTimeSlot>()
    for (h in 5..23) {
        for (m in listOf(0, 15, 30, 45)) {
            list.add(UmsTimeSlot(h, m))
        }
    }
    list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? FragmentActivity

    var balance by remember { mutableStateOf(LeaveStorage.loadBalance(context)) }
    var profile by remember { mutableStateOf(LeaveStorage.loadProfile(context)) }
    var records by remember { mutableStateOf(LeaveStorage.loadRecords(context)) }

    var isRefreshing by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitStatusText by remember { mutableStateOf("") }
    var errorMessageDialog by remember { mutableStateOf<String?>(null) }
    var successLeaveIdDialog by remember { mutableStateOf<String?>(null) }

    // Dialog state for 15-minute time picker: null = hidden, "START" or "END"
    var timePickerTarget by remember { mutableStateOf<String?>(null) }
    // Dialog state for date picker: null = hidden, "START" or "END"
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    // Form States
    var selectedType by remember { mutableStateOf(LeaveTypeOption.DAY_EXTENDED) }
    var selectedPlace by remember { mutableStateOf(VisitPlaceOption.OTHER) }
    var stayAddress by remember { mutableStateOf(LeaveStorage.getLastStayAddress(context)) }
    var relativeMobile by remember {
        mutableStateOf(LeaveStorage.getLastRelativeMobile(context).ifBlank { profile.registeredMobile })
    }
    var reason by remember { mutableStateOf(LeaveStorage.getLastReason(context)) }
    var declarationAccepted by remember { mutableStateOf(true) }

    // Initial snapped Start & End Calendars
    var startCal by remember {
        mutableStateOf(Calendar.getInstance().apply {
            val currentMin = get(Calendar.MINUTE)
            val snappedMin = when {
                currentMin < 15 -> 15
                currentMin < 30 -> 30
                currentMin < 45 -> 45
                else -> {
                    add(Calendar.HOUR_OF_DAY, 1)
                    0
                }
            }
            set(Calendar.MINUTE, snappedMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val h = get(Calendar.HOUR_OF_DAY)
            if (h < 5) {
                set(Calendar.HOUR_OF_DAY, 5)
                set(Calendar.MINUTE, 0)
            } else if (h > 22 || (h == 22 && snappedMin > 45)) {
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 45)
            }
        })
    }

    var endCal by remember {
        mutableStateOf((startCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    // Auto adjust return time/date on leave type change
    LaunchedEffect(selectedType) {
        val c = (startCal.clone() as Calendar)
        when (selectedType) {
            LeaveTypeOption.DAY_EXTENDED -> {
                c.set(Calendar.HOUR_OF_DAY, 22)
                c.set(Calendar.MINUTE, 45)
                c.set(Calendar.SECOND, 0)
                endCal = c
            }
            LeaveTypeOption.DAY_STANDARD -> {
                c.set(Calendar.HOUR_OF_DAY, 20)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                endCal = c
            }
            LeaveTypeOption.NIGHT_STANDARD -> {
                c.add(Calendar.DAY_OF_YEAR, 1)
                c.set(Calendar.HOUR_OF_DAY, 20)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                endCal = c
            }
            LeaveTypeOption.NIGHT_EXTENDED -> {
                c.add(Calendar.DAY_OF_YEAR, 1)
                c.set(Calendar.HOUR_OF_DAY, 22)
                c.set(Calendar.MINUTE, 45)
                c.set(Calendar.SECOND, 0)
                endCal = c
            }
            LeaveTypeOption.VACATION -> {
                c.add(Calendar.DAY_OF_YEAR, 5)
                c.set(Calendar.HOUR_OF_DAY, 18)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                endCal = c
            }
        }
    }

    fun refreshLeaveData() {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            val res = LeaveSubmitEngine.fetchLatestData(context)
            balance = res.balance
            profile = res.profile
            records = res.records
            if (relativeMobile.isBlank() && res.profile.registeredMobile.isNotBlank()) {
                relativeMobile = res.profile.registeredMobile
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLeaveData()
    }

    val refreshRotation by rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRot"
    )

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Header & Live Balance Summary Cards ────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOSTEL LEAVE BALANCE",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !isRefreshing) { refreshLeaveData() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Refresh",
                                tint = Blaze,
                                modifier = Modifier
                                    .size(14.dp)
                                    .then(if (isRefreshing) Modifier.rotate(refreshRotation) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRefreshing) "SYNCING..." else "SYNC",
                                fontSize = 11.sp,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                color = Blaze
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Day Leave Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slab,
                            border = BorderStroke(1.dp, Rule),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DAY LEAVE",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${balance.dayAvailed}/${balance.dayTotal} USED",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${balance.dayBalance}",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp,
                                        color = if (balance.dayBalance > 5) Blaze else DangerRed
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REMAINING",
                                        fontFamily = BarlowCondensed,
                                        fontSize = 11.sp,
                                        color = Slate,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }

                        // Night Leave Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slab,
                            border = BorderStroke(1.dp, Rule),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "NIGHT LEAVE",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Slate,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${balance.nightAvailed}/${balance.nightTotal} USED",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${balance.nightBalance}",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp,
                                        color = if (balance.nightBalance > 3) Chalk else DangerRed
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REMAINING",
                                        fontFamily = BarlowCondensed,
                                        fontSize = 11.sp,
                                        color = Slate,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. New Application Form ─────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slab,
                    border = BorderStroke(1.dp, Rule),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "APPLY HOSTEL LEAVE",
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Chalk,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Leave Type Selector
                        Text("LEAVE TYPE", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LeaveTypeOption.entries) { opt ->
                                val isSelected = opt == selectedType
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Blaze.copy(alpha = 0.15f) else SlabElevated,
                                    border = BorderStroke(1.dp, if (isSelected) Blaze else Rule),
                                    modifier = Modifier.clickable { selectedType = opt }
                                ) {
                                    Text(
                                        text = opt.displayName,
                                        fontSize = 12.sp,
                                        fontFamily = BarlowCondensed,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Blaze else Chalk,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        // Curfew Alert Pill
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SlabElevated,
                            border = BorderStroke(1.dp, Rule)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Blaze, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = selectedType.curfewNotice,
                                    fontSize = 11.sp,
                                    color = Slate,
                                    fontFamily = BarlowCondensed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visit Place
                        Text("VISIT PLACE", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VisitPlaceOption.entries.forEach { place ->
                                val isSelected = place == selectedPlace
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Blaze.copy(alpha = 0.15f) else SlabElevated,
                                    border = BorderStroke(1.dp, if (isSelected) Blaze else Rule),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedPlace = place
                                            if (place == VisitPlaceOption.HOME && profile.homeAddress.isNotBlank()) {
                                                stayAddress = profile.homeAddress
                                            }
                                        }
                                ) {
                                    Text(
                                        text = place.code,
                                        fontSize = 12.sp,
                                        fontFamily = BarlowCondensed,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Blaze else Chalk,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stay Address / Location
                        Text("STAY ADDRESS / LOCATION", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = if (selectedPlace == VisitPlaceOption.HOME && profile.homeAddress.isNotBlank()) profile.homeAddress else stayAddress,
                            onValueChange = { stayAddress = it },
                            placeholder = { Text("Enter destination address or city", color = Slate.copy(alpha = 0.5f), fontSize = 12.sp) },
                            enabled = selectedPlace != VisitPlaceOption.HOME,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SlabElevated,
                                unfocusedContainerColor = SlabElevated,
                                disabledContainerColor = SlabElevated.copy(alpha = 0.6f),
                                focusedBorderColor = Blaze,
                                unfocusedBorderColor = Rule,
                                focusedTextColor = Chalk,
                                unfocusedTextColor = Chalk,
                                disabledTextColor = Slate
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Quick Stay Address Chips (if not Home)
                        if (selectedPlace != VisitPlaceOption.HOME) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("Phagwara Market", "Law Gate", "Jalandhar", "Rama Mandi", "Deep Nagar")) { chip ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SlabElevated,
                                        border = BorderStroke(1.dp, Rule),
                                        modifier = Modifier.clickable { stayAddress = chip }
                                    ) {
                                        Text(chip, fontSize = 10.5.sp, color = Slate, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Relative Contact Mobile
                        Text("RELATIVE / CONTACT MOBILE", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = relativeMobile,
                            onValueChange = { if (it.length <= 10 && it.all { ch -> ch.isDigit() }) relativeMobile = it },
                            placeholder = { Text("10-digit mobile number", color = Slate.copy(alpha = 0.5f), fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SlabElevated,
                                unfocusedContainerColor = SlabElevated,
                                focusedBorderColor = Blaze,
                                unfocusedBorderColor = Rule,
                                focusedTextColor = Chalk,
                                unfocusedTextColor = Chalk
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── START DATE & TIME OF LEAVE (UMS RadDateTimePicker1) ──────────
                        Text(
                            text = "START DATE & TIME OF LEAVE",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            color = Slate,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Start Date Picker Box
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlabElevated,
                                border = BorderStroke(1.dp, Rule),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clickable { datePickerTarget = "START" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Blaze, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateFormat.format(startCal.time),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Chalk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Start Time Picker Box (Opens UMS 15-Minute Grid)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlabElevated,
                                border = BorderStroke(1.dp, Rule),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clickable { timePickerTarget = "START" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Blaze, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = timeFormat.format(startCal.time),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Chalk
                                    )
                                }
                            }
                        }

                        // Start Time Quick Chips
                        Spacer(modifier = Modifier.height(6.dp))
                        val startChips = remember {
                            listOf(
                                UmsTimeSlot(0, -1), // Represents "Now"
                                UmsTimeSlot(6, 0),
                                UmsTimeSlot(9, 0),
                                UmsTimeSlot(13, 0),
                                UmsTimeSlot(17, 0)
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(startChips) { chip ->
                                val label = if (chip.minute == -1) "Now" else chip.display
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SlabElevated,
                                    border = BorderStroke(1.dp, Rule),
                                    modifier = Modifier.clickable {
                                        if (chip.minute != -1) {
                                            startCal.set(Calendar.HOUR_OF_DAY, chip.hour)
                                            startCal.set(Calendar.MINUTE, chip.minute)
                                            startCal = (startCal.clone() as Calendar)
                                        } else {
                                            // Snap to next 15-min
                                            val c = Calendar.getInstance()
                                            val curM = c.get(Calendar.MINUTE)
                                            val nextM = when {
                                                curM < 15 -> 15
                                                curM < 30 -> 30
                                                curM < 45 -> 45
                                                else -> {
                                                    c.add(Calendar.HOUR_OF_DAY, 1)
                                                    0
                                                }
                                            }
                                            c.set(Calendar.MINUTE, nextM)
                                            val h = c.get(Calendar.HOUR_OF_DAY)
                                            if (h < 5) c.set(Calendar.HOUR_OF_DAY, 5)
                                            if (h > 22 || (h == 22 && nextM > 45)) {
                                                c.set(Calendar.HOUR_OF_DAY, 22)
                                                c.set(Calendar.MINUTE, 45)
                                            }
                                            startCal = c
                                        }
                                    }
                                ) {
                                    Text(label, fontSize = 10.5.sp, color = Slate, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── LEAVE ENDING DATE TIME (UMS RadDateTimePicker2) ────────────
                        Text(
                            text = "LEAVE ENDING DATE TIME",
                            fontSize = 11.sp,
                            fontFamily = BarlowCondensed,
                            color = Slate,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isDayLeave = selectedType == LeaveTypeOption.DAY_EXTENDED || selectedType == LeaveTypeOption.DAY_STANDARD

                            // Return Date Picker Box (Locked if Day Leave)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDayLeave) SlabElevated.copy(alpha = 0.5f) else SlabElevated,
                                border = BorderStroke(1.dp, Rule),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clickable(enabled = !isDayLeave) { datePickerTarget = "END" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = if (isDayLeave) Slate else Blaze,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateFormat.format(endCal.time),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDayLeave) Slate else Chalk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Return Time Picker Box (Opens UMS 15-Minute Grid)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlabElevated,
                                border = BorderStroke(1.dp, Rule),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clickable { timePickerTarget = "END" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Blaze, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = timeFormat.format(endCal.time),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Chalk
                                    )
                                }
                            }
                        }

                        // Return Time Quick Chips
                        Spacer(modifier = Modifier.height(6.dp))
                        data class QuickReturn(val label: String, val h: Int, val m: Int)
                        val returnChips = remember(selectedType) {
                            if (selectedType == LeaveTypeOption.DAY_STANDARD || selectedType == LeaveTypeOption.NIGHT_STANDARD) {
                                listOf(
                                    QuickReturn("8:00 PM (Curfew)", 20, 0),
                                    QuickReturn("7:00 PM", 19, 0),
                                    QuickReturn("7:30 PM", 19, 30),
                                    QuickReturn("7:45 PM", 19, 45)
                                )
                            } else {
                                listOf(
                                    QuickReturn("10:45 PM (Curfew)", 22, 45),
                                    QuickReturn("10:00 PM", 22, 0),
                                    QuickReturn("9:30 PM", 21, 30),
                                    QuickReturn("8:00 PM", 20, 0)
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(returnChips) { chip ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SlabElevated,
                                    border = BorderStroke(1.dp, Rule),
                                    modifier = Modifier.clickable {
                                        endCal.set(Calendar.HOUR_OF_DAY, chip.h)
                                        endCal.set(Calendar.MINUTE, chip.m)
                                        endCal = (endCal.clone() as Calendar)
                                    }
                                ) {
                                    Text(chip.label, fontSize = 10.5.sp, color = Slate, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Reason
                        Text("REASON FOR LEAVING", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            placeholder = { Text("e.g. Personal work, Buying books, Market", color = Slate.copy(alpha = 0.5f), fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SlabElevated,
                                unfocusedContainerColor = SlabElevated,
                                focusedBorderColor = Blaze,
                                unfocusedBorderColor = Rule,
                                focusedTextColor = Chalk,
                                unfocusedTextColor = Chalk
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Quick Reason Chips
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("Personal work", "Market & Groceries", "Buying Books", "Outing", "Medical")) { chip ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SlabElevated,
                                    border = BorderStroke(1.dp, Rule),
                                    modifier = Modifier.clickable { reason = chip }
                                ) {
                                    Text(chip, fontSize = 10.5.sp, color = Slate, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Declaration Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { declarationAccepted = !declarationAccepted }
                        ) {
                            Checkbox(
                                checked = declarationAccepted,
                                onCheckedChange = { declarationAccepted = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Blaze,
                                    uncheckedColor = Rule,
                                    checkmarkColor = Ink
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "I acknowledge hostel leave rules & agree to return before curfew.",
                                fontSize = 11.sp,
                                color = Slate,
                                lineHeight = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Button with Biometric Scan Icon
                        Button(
                            onClick = {
                                if (relativeMobile.length < 10) {
                                    Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (reason.isBlank()) {
                                    Toast.makeText(context, "Please enter a reason for leaving", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!declarationAccepted) {
                                    Toast.makeText(context, "Please accept the leave declaration", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                LeaveStorage.saveFormDefaults(context, stayAddress, reason, relativeMobile)

                                if (activity != null) {
                                    BiometricHelper.promptBiometric(
                                        activity = activity,
                                        title = "Authorize Leave Application",
                                        subtitle = "${selectedType.displayName} • ${timeFormat.format(startCal.time)} to ${timeFormat.format(endCal.time)}",
                                        onSuccess = {
                                            isSubmitting = true
                                            submitStatusText = "Connecting to UMS..."
                                            coroutineScope.launch {
                                                val finalStay = if (selectedPlace == VisitPlaceOption.HOME) profile.homeAddress else stayAddress

                                                val res = LeaveSubmitEngine.submitLeave(
                                                    context = context,
                                                    leaveTypeCode = selectedType.code,
                                                    visitPlace = selectedPlace.code,
                                                    stayAddress = finalStay,
                                                    relativeMobile = relativeMobile,
                                                    startCal = startCal,
                                                    endCal = endCal,
                                                    reason = reason,
                                                    onStatus = { status -> submitStatusText = status }
                                                )

                                                isSubmitting = false
                                                if (res.isSuccess) {
                                                    successLeaveIdDialog = res.getOrNull() ?: "Leave Applied"
                                                    refreshLeaveData()
                                                } else {
                                                    val err = res.exceptionOrNull()?.message ?: "Submission rejected by UMS"
                                                    errorMessageDialog = err
                                                }
                                            }
                                        },
                                        onError = { err ->
                                            if (!err.contains("Cancelled", ignoreCase = true)) {
                                                Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTHORIZE & APPLY LEAVE",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Ink,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // ── 3. Recent Applications List ────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT LEAVE APPLICATIONS",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${records.size} RECORDS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate
                    )
                }
            }

            if (records.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slab,
                        border = BorderStroke(1.dp, Rule),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No recent leave applications found.", color = Slate, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(records) { rec ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slab,
                        border = BorderStroke(1.dp, Rule),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${rec.leaveId}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Blaze
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rec.leaveType,
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Chalk
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (rec.status.contains("Approve", ignoreCase = true)) SuccessGreen.copy(alpha = 0.15f) else SlabElevated,
                                    border = BorderStroke(1.dp, if (rec.status.contains("Approve", ignoreCase = true)) SuccessGreen.copy(alpha = 0.4f) else Rule)
                                ) {
                                    Text(
                                        text = rec.status,
                                        fontSize = 10.5.sp,
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rec.status.contains("Approve", ignoreCase = true)) SuccessGreen else Slate,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("DEPARTURE", fontSize = 10.sp, color = Slate, fontFamily = BarlowCondensed)
                                    Text(rec.leaveDate, fontSize = 12.sp, color = Chalk, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RETURN", fontSize = 10.sp, color = Slate, fontFamily = BarlowCondensed)
                                    Text(rec.returnDate, fontSize = 12.sp, color = Chalk, fontWeight = FontWeight.Medium)
                                }
                            }

                            if (rec.checkOut.isNotBlank() || rec.checkIn.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Rule)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Out: ${rec.checkOut.ifBlank { "Not exited" }}",
                                        fontSize = 10.5.sp,
                                        color = Slate
                                    )
                                    Text(
                                        text = "In: ${rec.checkIn.ifBlank { "Pending" }}",
                                        fontSize = 10.5.sp,
                                        color = if (rec.checkIn.isNotBlank()) SuccessGreen else Slate
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── UMS 15-Minute Time Picker Dialog ─────────────────────────────────
    if (timePickerTarget != null) {
        val isStart = timePickerTarget == "START"
        val targetCal = if (isStart) startCal else endCal
        val currentH = targetCal.get(Calendar.HOUR_OF_DAY)
        val currentM = targetCal.get(Calendar.MINUTE)

        Dialog(
            onDismissRequest = { timePickerTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SlabElevated,
                border = BorderStroke(1.dp, Rule),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.75f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isStart) "SELECT DEPARTURE TIME" else "SELECT RETURN TIME",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Chalk,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isStart) "15-minute slots • 5:00 AM to 10:45 PM" else "15-minute slots • 5:00 AM to 11:45 PM",
                                fontSize = 11.sp,
                                color = Slate
                            )
                        }
                        IconButton(onClick = { timePickerTarget = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Rule)
                    Spacer(modifier = Modifier.height(12.dp))

                    val activeSlots = if (isStart) departureTimeSlots else returnTimeSlots
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(activeSlots) { slot ->
                            val isSelected = slot.hour == currentH && slot.minute == currentM
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Blaze else Slab,
                                border = BorderStroke(1.dp, if (isSelected) Blaze else Rule),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetCal.set(Calendar.HOUR_OF_DAY, slot.hour)
                                        targetCal.set(Calendar.MINUTE, slot.minute)
                                        if (isStart) {
                                            startCal = (targetCal.clone() as Calendar)
                                        } else {
                                            endCal = (targetCal.clone() as Calendar)
                                        }
                                        timePickerTarget = null
                                    }
                            ) {
                                Text(
                                    text = slot.display,
                                    fontSize = 11.5.sp,
                                    fontFamily = BarlowCondensed,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Ink else Chalk,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Material 3 Date Picker Dialog ────────────────────────────────────
    if (datePickerTarget != null) {
        val isStart = datePickerTarget == "START"
        val targetCal = if (isStart) startCal else endCal
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetCal.timeInMillis
        )

        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            val y = utcCal.get(Calendar.YEAR)
                            val m = utcCal.get(Calendar.MONTH)
                            val d = utcCal.get(Calendar.DAY_OF_MONTH)

                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = targetCal.timeInMillis
                                set(Calendar.YEAR, y)
                                set(Calendar.MONTH, m)
                                set(Calendar.DAY_OF_MONTH, d)
                            }

                            if (isStart) {
                                startCal = newCal
                                if (selectedType == LeaveTypeOption.DAY_EXTENDED || selectedType == LeaveTypeOption.DAY_STANDARD) {
                                    endCal = Calendar.getInstance().apply {
                                        timeInMillis = endCal.timeInMillis
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                    }
                                } else if (endCal.before(newCal)) {
                                    endCal = Calendar.getInstance().apply {
                                        timeInMillis = endCal.timeInMillis
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d + 1)
                                    }
                                }
                            } else {
                                endCal = newCal
                            }
                        }
                        datePickerTarget = null
                    }
                ) {
                    Text("SELECT", color = Blaze, fontWeight = FontWeight.Bold, fontFamily = BarlowCondensed, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) {
                    Text("CANCEL", color = Slate, fontFamily = BarlowCondensed, fontSize = 14.sp)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = SlabElevated,
                titleContentColor = Chalk,
                headlineContentColor = Blaze,
                weekdayContentColor = Slate,
                subheadContentColor = Slate,
                yearContentColor = Chalk,
                currentYearContentColor = Blaze,
                selectedYearContentColor = Ink,
                selectedYearContainerColor = Blaze,
                dayContentColor = Chalk,
                selectedDayContentColor = Ink,
                selectedDayContainerColor = Blaze,
                todayContentColor = Blaze,
                todayDateBorderColor = Blaze
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = SlabElevated,
                    titleContentColor = Chalk,
                    headlineContentColor = Blaze,
                    weekdayContentColor = Slate,
                    subheadContentColor = Slate,
                    yearContentColor = Chalk,
                    currentYearContentColor = Blaze,
                    selectedYearContentColor = Ink,
                    selectedYearContainerColor = Blaze,
                    dayContentColor = Chalk,
                    selectedDayContentColor = Ink,
                    selectedDayContainerColor = Blaze,
                    todayContentColor = Blaze,
                    todayDateBorderColor = Blaze
                )
            )
        }
    }

    // ── Submitting Progress Overlay ──────────────────────────────────────
    if (isSubmitting) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SlabElevated,
                border = BorderStroke(1.dp, Rule)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Blaze, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = submitStatusText.ifBlank { "Submitting to UMS..." },
                        fontFamily = BarlowCondensed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Chalk,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Do not close the app",
                        fontSize = 11.sp,
                        color = Slate
                    )
                }
            }
        }
    }

    // ── Error Message Dialog ─────────────────────────────────────────────
    if (errorMessageDialog != null) {
        AlertDialog(
            onDismissRequest = { errorMessageDialog = null },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(28.dp)) },
            title = {
                Text(
                    text = "APPLICATION REJECTED",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Chalk
                )
            },
            text = {
                Text(
                    text = errorMessageDialog ?: "Unknown error from UMS",
                    fontSize = 13.sp,
                    color = Slate,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { errorMessageDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Blaze),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("OK", color = Ink, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SlabElevated
        )
    }

    // ── Success Confirmation Dialog ──────────────────────────────────────
    if (successLeaveIdDialog != null) {
        AlertDialog(
            onDismissRequest = { successLeaveIdDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp)) },
            title = {
                Text(
                    text = "LEAVE APPLIED SUCCESSFULLY",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Chalk
                )
            },
            text = {
                Column {
                    Text(
                        text = "Your hostel leave application has been submitted to UMS.",
                        fontSize = 13.sp,
                        color = Slate
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = successLeaveIdDialog ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { successLeaveIdDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DONE", color = Ink, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SlabElevated
        )
    }
}
