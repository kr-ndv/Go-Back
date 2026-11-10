package com.goback.focus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ModalShape
import kotlinx.coroutines.delay

object SecurityPinUtil {
    const val PIN_SETTING_KEY = "security.pin"
    private const val PIN_SALT = "goback_pin_salt_v1_"

    /**
     * Salted hash matching src/lib/securityPin.ts Murmur-style 32-bit integer math.
     */
    fun hashPin(pin: String): String {
        val input = "$PIN_SALT$pin"
        var h1 = 0xdeadbeef.toInt()
        var h2 = 0x41c6ce57.toInt()
        for (i in input.indices) {
            val ch = input[i].code
            h1 = (h1 xor ch) * 2654435761.toInt()
            h2 = (h2 xor ch) * 1597334677.toInt()
        }
        h1 = (h1 xor (h1 ushr 16)) * 2246822507.toInt() xor ((h2 ushr 13) * 3266489909.toInt())
        h2 = (h2 xor (h2 ushr 16)) * 2246822507.toInt() xor ((h1 ushr 13) * 3266489909.toInt())

        val highPart = (h2.toLong() and 2097151L) * 4294967296L
        val lowPart = h1.toLong() and 0xFFFFFFFFL
        val combined = highPart + lowPart
        return combined.toString(16).padStart(16, '0')
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun matchesPin(storedPin: String?, candidate: String): Boolean {
        if (storedPin.isNullOrEmpty()) return true
        if (storedPin.length == 4 && storedPin.all { it.isDigit() }) {
            return candidate == storedPin
        }
        return hashPin(candidate) == storedPin
    }
}

enum class PinModalMode {
    VERIFY,
    SET,
    REMOVE
}

@Composable
fun SecurityPinDialog(
    mode: PinModalMode,
    storedPinHash: String?,
    onSuccess: (newPin: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredDigits by remember { mutableStateOf("") }
    var firstAttemptPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockoutSecondsRemaining by remember { mutableStateOf(0) }

    LaunchedEffect(lockoutSecondsRemaining) {
        if (lockoutSecondsRemaining > 0) {
            delay(1000L)
            lockoutSecondsRemaining -= 1
        }
    }

    val isStepTwoConfirm = mode == PinModalMode.SET && firstAttemptPin != null

    val title = when (mode) {
        PinModalMode.VERIFY -> "Enter Security PIN"
        PinModalMode.SET -> if (isStepTwoConfirm) "Confirm 4-Digit PIN" else "Create 4-Digit PIN"
        PinModalMode.REMOVE -> "Verify PIN to Remove"
    }

    val subtitle = when {
        lockoutSecondsRemaining > 0 -> "Too many incorrect attempts. Please wait $lockoutSecondsRemaining seconds."
        mode == PinModalMode.VERIFY -> "Enter your 4-digit PIN to access protection settings."
        mode == PinModalMode.SET -> if (isStepTwoConfirm) "Re-enter the exact same 4 digits to confirm." else "Choose a 4-digit PIN to prevent impulse bypasses."
        else -> "Confirm your current PIN to turn off security protection."
    }

    fun handleDigit(digit: String) {
        if (lockoutSecondsRemaining > 0) return
        if (enteredDigits.length < 4) {
            errorMessage = null
            val next = enteredDigits + digit
            enteredDigits = next
            if (next.length == 4) {
                when (mode) {
                    PinModalMode.VERIFY -> {
                        if (SecurityPinUtil.matchesPin(storedPinHash, next)) {
                            failedAttempts = 0
                            onSuccess(null)
                        } else {
                            failedAttempts++
                            if (failedAttempts >= 5) {
                                lockoutSecondsRemaining = 30
                                failedAttempts = 0
                                errorMessage = "Too many failed attempts. Locked for 30 seconds."
                            } else {
                                val remaining = 5 - failedAttempts
                                errorMessage = "Incorrect PIN. $remaining attempt${if (remaining == 1) "" else "s"} remaining."
                            }
                            enteredDigits = ""
                        }
                    }
                    PinModalMode.REMOVE -> {
                        if (SecurityPinUtil.matchesPin(storedPinHash, next)) {
                            failedAttempts = 0
                            onSuccess(null)
                        } else {
                            failedAttempts++
                            if (failedAttempts >= 5) {
                                lockoutSecondsRemaining = 30
                                failedAttempts = 0
                                errorMessage = "Too many failed attempts. Locked for 30 seconds."
                            } else {
                                val remaining = 5 - failedAttempts
                                errorMessage = "Incorrect PIN. $remaining attempt${if (remaining == 1) "" else "s"} remaining."
                            }
                            enteredDigits = ""
                        }
                    }
                    PinModalMode.SET -> {
                        if (firstAttemptPin == null) {
                            firstAttemptPin = next
                            enteredDigits = ""
                        } else {
                            if (next == firstAttemptPin) {
                                onSuccess(next)
                            } else {
                                errorMessage = "PINs did not match. Please start over."
                                firstAttemptPin = null
                                enteredDigits = ""
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (lockoutSecondsRemaining > 0) return
        if (enteredDigits.isNotEmpty()) {
            errorMessage = null
            enteredDigits = enteredDigits.dropLast(1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = ModalShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // 4-Dot Display Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredDigits.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .border(
                                    2.dp,
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                // Numeric Keypad Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val keypad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CANCEL", "0", "DEL")
                    )

                    keypad.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (key == "CANCEL" || key == "DEL") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable(enabled = lockoutSecondsRemaining == 0 || key == "CANCEL") {
                                            when (key) {
                                                "CANCEL" -> onDismiss()
                                                "DEL" -> handleBackspace()
                                                else -> handleDigit(key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "DEL") {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else if (key == "CANCEL") {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
