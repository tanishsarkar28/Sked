package com.sked.sked_app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

object DeveloperLinks {
    const val DEV_NAME = "Tanish Sarkar"
    const val DEV_ROLE = "Creator & Developer • LPU"
    const val INSTA_USERNAME = "tanishsarkar28"
    const val INSTA_URL = "https://www.instagram.com/tanishsarkar28/"
    const val LINKEDIN_ID = "tanish-sarkar28"
    const val LINKEDIN_URL = "https://www.linkedin.com/in/tanish-sarkar28/"
    const val GITHUB_USERNAME = "tanishsarkar28"
    const val GITHUB_URL = "https://github.com/tanishsarkar28"

    /**
     * Opens Instagram app directly to the user profile.
     * Falls back to web browser if Instagram is not installed.
     */
    fun openInstagram(context: Context) {
        val appUri = Uri.parse("http://instagram.com/_u/$INSTA_USERNAME")
        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            setPackage("com.instagram.android")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTA_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens LinkedIn app directly to the profile.
     * Falls back to web browser if LinkedIn is not installed.
     */
    fun openLinkedIn(context: Context) {
        // Direct deep-link format for LinkedIn app
        val appUri = Uri.parse("linkedin://profile/$LINKEDIN_ID")
        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            setPackage("com.linkedin.android")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LINKEDIN_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens GitHub profile directly in GitHub app if installed or in browser.
     */
    fun openGitHub(context: Context) {
        val uri = Uri.parse(GITHUB_URL)
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.github.android")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun AboutDeveloperDialog(
    onDismiss: () -> Unit,
    onUpdateFound: ((com.sked.sked_app.update.UpdateInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val (_, currentVer) = remember { com.sked.sked_app.update.AppUpdateManager.getCurrentVersionInfo(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Rule, RoundedCornerShape(12.dp)),
                color = Slab,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Blaze, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ABOUT THE DEVELOPER.",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 0.8.sp,
                                color = Blaze
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Slate,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Developer Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                            .border(1.dp, Rule, RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monogram Avatar
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Blaze.copy(alpha = 0.15f))
                                .border(1.dp, Blaze.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "TS",
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = Blaze
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = DeveloperLinks.DEV_NAME,
                                fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Chalk
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = DeveloperLinks.DEV_ROLE,
                                fontSize = 12.sp,
                                color = Slate,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CONNECT & PROFILES",
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = Slate
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Instagram Row
                    SocialLinkRow(
                        title = "Instagram",
                        handle = "@${DeveloperLinks.INSTA_USERNAME}",
                        iconRes = R.drawable.ic_instagram,
                        accentColor = Color(0xFFE1306C),
                        onClick = { DeveloperLinks.openInstagram(context) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // LinkedIn Row
                    SocialLinkRow(
                        title = "LinkedIn",
                        handle = "in/${DeveloperLinks.LINKEDIN_ID}",
                        iconRes = R.drawable.ic_linkedin,
                        accentColor = Color(0xFF0A66C2),
                        onClick = { DeveloperLinks.openLinkedIn(context) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // GitHub Row
                    SocialLinkRow(
                        title = "GitHub",
                        handle = "@${DeveloperLinks.GITHUB_USERNAME}",
                        iconRes = R.drawable.ic_github,
                        accentColor = Color(0xFFFFFFFF),
                        onClick = { DeveloperLinks.openGitHub(context) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Share Sked Row
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slab,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Blaze.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Check out Sked — LPU Timetable, Exams & Seating Plan App: https://sked-gold.vercel.app/")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Sked"))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Blaze.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Sked",
                                        tint = Blaze,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "SHARE SKED WITH FRIENDS",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Chalk
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "https://sked-gold.vercel.app/",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Blaze
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    // Check for Updates Row
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slab,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCheckingUpdate) Blaze.copy(alpha = 0.5f) else Rule),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCheckingUpdate) {
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    try {
                                        val update = com.sked.sked_app.update.AppUpdateManager.checkForUpdate(context, force = true)
                                        isCheckingUpdate = false
                                        if (update != null) {
                                            onUpdateFound?.invoke(update)
                                        } else {
                                            val (_, currentVer) = com.sked.sked_app.update.AppUpdateManager.getCurrentVersionInfo(context)
                                            android.widget.Toast.makeText(
                                                context,
                                                "You're using the latest version of Sked (v$currentVer) ✓",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        isCheckingUpdate = false
                                        android.widget.Toast.makeText(context, "Update check: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Blaze.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Check for updates",
                                        tint = Blaze,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Check for Updates",
                                        fontFamily = BarlowCondensed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Chalk
                                    )
                                    Text(
                                        text = if (isCheckingUpdate) "Checking website..." else "Tap to check for new release",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate
                                    )
                                }
                            }

                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Blaze
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    HorizontalDivider(thickness = 1.dp, color = Rule)

                    Spacer(modifier = Modifier.height(12.dp))

                    // App signature / footer note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SKED FOR LPU • V$currentVer",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Slate
                        )
                        Text(
                            text = "BUILT WITH PASSION",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Blaze
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialLinkRow(
    title: String,
    handle: String,
    iconRes: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .border(1.dp, Rule, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Chalk
                )
                Text(
                    text = handle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open $title",
            tint = Slate,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun DeveloperFooterCard(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .border(1.dp, Rule, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Blaze.copy(alpha = 0.15f))
                    .border(1.dp, Blaze.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TS",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Blaze
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "BUILT BY TANISH SARKAR",
                    fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = Chalk
                )
                Text(
                    text = "Tap to view developer profiles",
                    fontSize = 10.sp,
                    color = Slate
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_instagram),
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(14.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_linkedin),
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(14.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
