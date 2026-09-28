package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.SouqiTheme
import com.example.ui.viewmodel.MarketplaceViewModel
import kotlinx.coroutines.flow.collectLatest

class AdminActivity : ComponentActivity() {

    private val viewModel: MarketplaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SouqiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val snackbarHostState = remember { SnackbarHostState() }
                    var isAuthenticated by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        viewModel.uiEvent.collectLatest { msg ->
                            snackbarHostState.showSnackbar(msg)
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AnimatedContent(
                                targetState = isAuthenticated,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "AdminAuthTransition"
                            ) { authed ->
                                if (authed) {
                                    BackHandler {
                                        isAuthenticated = false
                                    }
                                    AdminDashboardScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            isAuthenticated = false
                                            finish()
                                        }
                                    )
                                } else {
                                    AdminLoginGate(
                                        viewModel = viewModel,
                                        onSuccess = {
                                            isAuthenticated = true
                                            viewModel.activateAdminSession()
                                        },
                                        onExit = { finish() }
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

@Composable
fun AdminLoginGate(
    viewModel: MarketplaceViewModel,
    onSuccess: () -> Unit,
    onExit: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val currentPin = viewModel.getAdminPin()

    if (showChangePinDialog) {
        ChangeAdminPinDialog(
            viewModel = viewModel,
            onDismiss = { showChangePinDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("admin_login_gate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Shield & Icon
        Surface(
            shape = CircleShape,
            color = EmeraldPrimary.copy(alpha = 0.12f),
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "سوقي إشراف (Admin App)",
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "التطبيق المستقل للمشرفين وإدارة المنصة\nمراجعة وصولات الدفع وحسابات بريدي موب وCCP",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = GoldSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تسجيل دخول المشرف",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        enteredPin = it
                        errorMessage = null
                    },
                    label = { Text("رمز الدخول (PIN)") },
                    placeholder = { Text("أدخل رمز الإشراف") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val activePin = viewModel.getAdminPin()
                        if (enteredPin.trim() == activePin.trim()) {
                            onSuccess()
                        } else {
                            errorMessage = "رمز الإشراف غير صحيح! يرجى التأكد من الرمز."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح لوحة التحكم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تغيير رمز الدخول للإدارة", fontSize = 13.sp, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Security Footnote
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "قاعدة بيانات مشتركة مشفرة • اتصال محلي ومباشر",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChangeAdminPinDialog(
    viewModel: MarketplaceViewModel,
    onDismiss: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var successDone by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تغيير رمز الدخول للإدارة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (successDone) {
                    Text("تم تحديث رمز الدخول بنجاح! يمكنك الآن تسجيل الدخول بالرمز الجديد.", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        "أدخل الرمز الحالي متبوعاً بالرمز الجديد لتأمين لوحة الإشراف:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = {
                            oldPin = it
                            dialogError = null
                        },
                        label = { Text("الرمز السري الحالي") },
                        placeholder = { Text("الافتراضي: 2026") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it
                            dialogError = null
                        },
                        label = { Text("الرمز الجديد (4 أرقام أو أكثر)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it
                            dialogError = null
                        },
                        label = { Text("تأكيد الرمز الجديد") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (dialogError != null) {
                        Text(
                            text = dialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (successDone) {
                Button(onClick = onDismiss) {
                    Text("حسناً")
                }
            } else {
                Button(
                    onClick = {
                        if (newPin.trim().length < 4) {
                            dialogError = "الرمز الجديد يجب أن يتكون من 4 أرقام على الأقل"
                            return@Button
                        }
                        if (newPin.trim() != confirmPin.trim()) {
                            dialogError = "الرمز الجديد وتأكيده غير متطابقين!"
                            return@Button
                        }
                        val success = viewModel.updateAdminPin(oldPin, newPin)
                        if (success) {
                            successDone = true
                        } else {
                            dialogError = "الرمز الحالي غير صحيح!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("حفظ الرمز الجديد")
                }
            }
        },
        dismissButton = {
            if (!successDone) {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        }
    )
}
