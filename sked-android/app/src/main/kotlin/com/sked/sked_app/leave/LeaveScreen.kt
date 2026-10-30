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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.Dispatchers
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
    onReSync: (() -> Unit)? = null,
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
    var showApplyBridge by remember { mutableStateOf(false) }
    var showSyncBridge by remember { mutableStateOf(false) }

    // Dialog state for 15-minute time picker: null = hidden, "START" or "END"
    var timePickerTarget by remember { mutableStateOf<String?>(null) }
    // Dialog state for date picker: null = hidden, "START" or "END"
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    // Form States
    var selectedType by remember { mutableStateOf(LeaveTypeOption.DAY_EXTENDED) }
    var selectedPlace by remember { mutableStateOf(VisitPlaceOption.OTHER) }
    var isVisitPlaceExpanded by remember { mutableStateOf(false) }
    var stayAddress by remember { mutableStateOf(LeaveStorage.getLastStayAddress(context)) }
    var relativeMobile by remember {
        mutableStateOf(LeaveStorage.getLastRelativeMobile(context).ifBlank { profile.registeredMobile })
    }
    var reason by remember { mutableStateOf(LeaveStorage.getLastReason(context)) }
    var declarationAccepted by remember { mutableStateOf(true) }

    // Initial snapped Start & End Calendars
    var startCal by remember {
        mutableStateOf(Calendar.getInstance().apply {
            val nowH = get(Calendar.HOUR_OF_DAY)
            // If it's already 8:30 PM (20:30) or later, day leave for today has passed curfew.
            // Intelligently default to tomorrow 08:00 AM!
            if (nowH >= 20) {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            } else {
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
                }
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

    // Auto adjust return time/date and valid visit places on leave type change
    LaunchedEffect(selectedType) {
        val validPlaces = VisitPlaceOption.optionsFor(selectedType)
        if (validPlaces.none { it.code == selectedPlace.code }) {
            selectedPlace = validPlaces.firstOrNull() ?: VisitPlaceOption.OTHER
            if (selectedPlace == VisitPlaceOption.HOME && profile.homeAddress.isNotBlank()) {
                stayAddress = profile.homeAddress
            }
        }

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

    fun refreshLeaveData(isUserInitiated: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            when (val res = LeaveSubmitEngine.fetchLatestData(context)) {
                is LeaveSyncResult.Success -> {
                    balance = res.data.balance
                    profile = res.data.profile
                    records = res.data.records
                    if (relativeMobile.isBlank() && res.data.profile.registeredMobile.isNotBlank()) {
                        relativeMobile = res.data.profile.registeredMobile
                    }
                    if (isUserInitiated) {
                        Toast.makeText(context, "Hostel leave balances updated", Toast.LENGTH_SHORT).show()
                    }
                }
                is LeaveSyncResult.SessionExpired -> {
                    if (isUserInitiated) {
                        showSyncBridge = true
                    }
                }
                is LeaveSyncResult.Error -> {
                    if (isUserInitiated) {
                        Toast.makeText(context, "Sync failed: ${res.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLeaveData(isUserInitiated = false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val refreshRotation = if (isRefreshing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshRot"
        )
    } else null

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
                                .clickable(enabled = !isRefreshing) { refreshLeaveData(isUserInitiated = true) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Refresh",
                                tint = Blaze,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        rotationZ = refreshRotation?.value ?: 0f
                                    }
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
                                        text = if (balance.isLoaded) "${balance.dayAvailed}/${balance.dayTotal} USED" else "--",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (balance.isLoaded) "${balance.dayBalance}" else "--",
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
                                        text = if (balance.isLoaded) "${balance.nightAvailed}/${balance.nightTotal} USED" else "--",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (balance.isLoaded) "${balance.nightBalance}" else "--",
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

                        // Visit Place (Dropdown selector dynamically populated based on Leave Type)
                        Text("VISIT PLACE", fontSize = 11.sp, fontFamily = BarlowCondensed, color = Slate, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val currentPlaces = remember(selectedType) { VisitPlaceOption.optionsFor(selectedType) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlabElevated,
                                border = BorderStroke(1.dp, if (isVisitPlaceExpanded) Blaze else Rule),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isVisitPlaceExpanded = !isVisitPlaceExpanded }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = Blaze,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = selectedPlace.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Chalk
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isVisitPlaceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand Visit Place",
                                        tint = if (isVisitPlaceExpanded) Blaze else Slate,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isVisitPlaceExpanded,
                                onDismissRequest = { isVisitPlaceExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(SlabElevated)
                                    .border(BorderStroke(1.dp, Rule), RoundedCornerShape(8.dp))
                            ) {
                                currentPlaces.forEach { place ->
                                    val isSelected = place.code == selectedPlace.code
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = place.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Blaze else Chalk
                                            )
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Blaze,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            selectedPlace = place
                                            isVisitPlaceExpanded = false
                                            if (place == VisitPlaceOption.HOME && profile.homeAddress.isNotBlank()) {
                                                stayAddress = profile.homeAddress
                                            } else if (place.code in listOf("Phagwara", "Jalandhar", "Ludhiana")) {
                                                stayAddress = place.code
                                            }
                                        },
                                        modifier = Modifier.background(
                                            if (isSelected) Blaze.copy(alpha = 0.1f) else Color.Transparent
                                        )
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
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlabElevated)
                                    .border(1.dp, Rule, RoundedCornerShape(6.dp))
                                    .clickable { datePickerTarget = "START" }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Row(
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
                            Box(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlabElevated)
                                    .border(1.dp, Rule, RoundedCornerShape(6.dp))
                                    .clickable { timePickerTarget = "START" }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Row(
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
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDayLeave) SlabElevated.copy(alpha = 0.5f) else SlabElevated)
                                    .border(1.dp, Rule, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isDayLeave) { datePickerTarget = "END" }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Row(
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
                            Box(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlabElevated)
                                    .border(1.dp, Rule, RoundedCornerShape(6.dp))
                                    .clickable { timePickerTarget = "END" }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Row(
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
                                if (endCal.timeInMillis <= startCal.timeInMillis) {
                                    Toast.makeText(context, "Return date & time must be after departure time", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (startCal.timeInMillis < System.currentTimeMillis() - 15 * 60 * 1000) {
                                    Toast.makeText(context, "Departure time cannot be in the past. Please select an upcoming slot.", Toast.LENGTH_SHORT).show()
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
                                            showApplyBridge = true
                                        },
                                        onError = { err ->
                                            if (!err.contains("Cancelled", ignoreCase = true)) {
                                                Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                } else {
                                    showApplyBridge = true
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
                items(
                    items = records,
                    key = { it.leaveId },
                    contentType = { "leave_record" }
                ) { rec ->
                    LeaveRecordItemCard(record = rec)
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Blaze else Slab)
                                    .border(1.dp, if (isSelected) Blaze else Rule, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val newCal = Calendar.getInstance().apply {
                                            timeInMillis = targetCal.timeInMillis
                                            set(Calendar.HOUR_OF_DAY, slot.hour)
                                            set(Calendar.MINUTE, slot.minute)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        if (isStart) {
                                            startCal = newCal
                                            // Ensure return time is after start time
                                            if (endCal.timeInMillis <= newCal.timeInMillis) {
                                                endCal = Calendar.getInstance().apply {
                                                    timeInMillis = newCal.timeInMillis
                                                    if (selectedType == LeaveTypeOption.DAY_EXTENDED) {
                                                        set(Calendar.HOUR_OF_DAY, 22)
                                                        set(Calendar.MINUTE, 45)
                                                    } else if (selectedType == LeaveTypeOption.DAY_STANDARD) {
                                                        set(Calendar.HOUR_OF_DAY, 20)
                                                        set(Calendar.MINUTE, 0)
                                                    } else {
                                                        add(Calendar.HOUR_OF_DAY, 2)
                                                    }
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                            }
                                        } else {
                                            endCal = newCal
                                        }
                                        timePickerTarget = null
                                    }
                                    .padding(vertical = 8.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = slot.display,
                                    fontSize = 11.5.sp,
                                    fontFamily = BarlowCondensed,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Ink else Chalk,
                                    textAlign = TextAlign.Center
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

    // ── Dedicated Direct Leave Application Bridge Dialog ──────────────────
    if (showApplyBridge) {
        val finalStay = if (selectedPlace == VisitPlaceOption.HOME) profile.homeAddress else stayAddress

        LeaveApplyBridgeDialog(
            onDismiss = { showApplyBridge = false },
            selectedType = selectedType,
            selectedPlace = selectedPlace,
            stayAddress = finalStay,
            relativeMobile = relativeMobile,
            startCal = startCal,
            endCal = endCal,
            reason = reason,
            onSuccess = { leaveId ->
                showApplyBridge = false
                successLeaveIdDialog = if (leaveId.startsWith("Leave ID", ignoreCase = true)) leaveId else "Leave ID #$leaveId"
                refreshLeaveData(isUserInitiated = false)
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        LeaveSubmitEngine.fetchGatePass(context)
                    } catch (_: Exception) {}
                }
            },
            onError = { err ->
                showApplyBridge = false
                errorMessageDialog = err
            }
        )
    }

    // ── Dedicated Live Leave Sync Bridge Dialog ───────────────────────────
    if (showSyncBridge) {
        LeaveSyncBridgeDialog(
            onDismiss = { showSyncBridge = false },
            onSuccess = { parsedData ->
                showSyncBridge = false
                balance = parsedData.balance
                profile = parsedData.profile
                records = parsedData.records
                if (relativeMobile.isBlank() && parsedData.profile.registeredMobile.isNotBlank()) {
                    relativeMobile = parsedData.profile.registeredMobile
                }
                Toast.makeText(context, "Hostel leave data synced successfully", Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                showSyncBridge = false
                errorMessageDialog = err
            }
        )
    }
}

@Composable
fun LeaveRecordItemCard(
    record: LeaveRecord,
    modifier: Modifier = Modifier
) {
    val isApproved = remember(record.status) {
        record.status.contains("Approve", ignoreCase = true)
    }
    val statusBg = remember(isApproved) {
        if (isApproved) SuccessGreen.copy(alpha = 0.15f) else SlabElevated
    }
    val statusBorder = remember(isApproved) {
        BorderStroke(1.dp, if (isApproved) SuccessGreen.copy(alpha = 0.4f) else Rule)
    }
    val statusColor = remember(isApproved) {
        if (isApproved) SuccessGreen else Slate
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slab,
        border = BorderStroke(1.dp, Rule),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${record.leaveId}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Blaze
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.leaveType,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Chalk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusBg,
                    border = statusBorder
                ) {
                    Text(
                        text = record.status,
                        fontSize = 10.5.sp,
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
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
                    Text(record.leaveDate, fontSize = 12.sp, color = Chalk, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("RETURN", fontSize = 10.sp, color = Slate, fontFamily = BarlowCondensed)
                    Text(record.returnDate, fontSize = 12.sp, color = Chalk, fontWeight = FontWeight.Medium)
                }
            }

            if (record.checkOut.isNotBlank() || record.checkIn.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Rule)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Out: ${record.checkOut.ifBlank { "Not exited" }}",
                        fontSize = 10.5.sp,
                        color = Slate
                    )
                    Text(
                        text = "In: ${record.checkIn.ifBlank { "Pending" }}",
                        fontSize = 10.5.sp,
                        color = if (record.checkIn.isNotBlank()) SuccessGreen else Slate
                    )
                }
            }
        }
    }
}

