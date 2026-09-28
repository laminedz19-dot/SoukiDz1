package com.example.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.TopUpRequestEntity
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ListingEntity
import com.example.ui.components.AdCard
import com.example.ui.components.formatDzd
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.VerifiedBlue
import com.example.ui.viewmodel.MarketplaceViewModel

@Composable
fun ProfileScreen(
    viewModel: MarketplaceViewModel,
    onAdClick: (String) -> Unit,
    onOpenLegal: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentLang by viewModel.language.collectAsState()
    val wallet by viewModel.currentWallet.collectAsState()
    val walletTransactions by viewModel.walletTransactions.collectAsState()
    val userTopUpRequests by viewModel.userTopUpRequests.collectAsState()
    val myListings by viewModel.myListings.collectAsState()
    val allListings by viewModel.adminListings.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: My Ads, 1: Favorites, 2: Wallet Transactions

    // Dialogs
    var showTopUpDialog by remember { mutableStateOf(false) }
    var topUpAmountText by remember { mutableStateOf("500") }
    var topUpProvider by remember { mutableStateOf("BARIDIMOB") }
    var topUpReferenceText by remember { mutableStateOf("") }
    var receiptImageUri by remember { mutableStateOf("") }
    var isAccountCopied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { receiptImageUri = it.toString() }
    }

    val platformSettings by viewModel.platformSettings.collectAsState()
    val officialRip = platformSettings.officialRip
    val officialKey = platformSettings.officialKey
    val officialProvider = platformSettings.officialProviderName
    val officialHolder = platformSettings.officialAccountHolder
    val officialInstructions = platformSettings.officialInstructions

    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Dialog: Top Up Wallet (Official BaridiMob / CCP Account)
    if (showTopUpDialog) {
        AlertDialog(
            onDismissRequest = {
                showTopUpDialog = false
                isAccountCopied = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("شحن رصيد المحفظة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        // Official Account Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = EmeraldPrimary.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "حساب الشحن الرسمي",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EmeraldPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GoldSecondary
                                    ) {
                                        Text(
                                            text = officialProvider,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (officialHolder.isNotBlank()) {
                                    Text(
                                        text = officialHolder,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("رقم الحساب الجاري (RIP):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = officialRip,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("المفتاح (Clé):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = officialKey,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = EmeraldPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(officialRip))
                                        isAccountCopied = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAccountCopied) EmeraldDark else EmeraldPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        if (isAccountCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isAccountCopied) "تم نسخ رقم الحساب بنجاح ✓" else "نسخ رقم الحساب (RIP)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Transfer instructions
                        Text(
                            text = "التعليمات:\n" +
                                    "1. حوّل المبلغ عبر تطبيق BaridiMob أو أقرب مكتب بريد للحساب أعلاه.\n" +
                                    "2. أدخل المبلغ ورقم إشعار/وصل التحويل لتأكيد الشحن الفوري.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Amount section
                        Text("مبلغ الشحن بالدينار (دج):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("500", "1000", "2000", "5000").forEach { preset ->
                                OutlinedButton(
                                    onClick = { topUpAmountText = preset },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (topUpAmountText == preset) EmeraldPrimary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = "$preset دج",
                                        fontSize = 10.sp,
                                        fontWeight = if (topUpAmountText == preset) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = topUpAmountText,
                            onValueChange = { input -> topUpAmountText = input.filter { it.isDigit() } },
                            label = { Text("المبلغ (دج)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Receipt / Reference ID
                        OutlinedTextField(
                            value = topUpReferenceText,
                            onValueChange = { topUpReferenceText = it },
                            label = { Text("رقم وصل العملية / مرجع التحويل (Ref)") },
                            placeholder = { Text("مثال: رقم الحوالة أو إشعار بريدي موب") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Receipt Photo Picker
                        Text("صورة وصل العملية أو لقطة الشاشة:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (receiptImageUri.isBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        receiptPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = EmeraldPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("إرفاق صورة الوصل من الهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = receiptImageUri,
                                            contentDescription = "وصل العملية",
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("تم إرفاق صورة الوصل ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                            Text("انقر للاستبدال أو الحذف", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    IconButton(onClick = { receiptImageUri = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف الصورة", tint = UrgentRed)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("وسيلة التحويل:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))

                        listOf(
                            "BARIDIMOB" to "تطبيق بريدي موب (BaridiMob)",
                            "CCP" to "حوالة بريد الجزائر CCP",
                            "EDAHABIA" to "البطاقة الذهبية بريد الجزائر",
                            "CIB" to "بطاقة بنكية CIB"
                        ).forEach { (code, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { topUpProvider = code }
                                    .padding(vertical = 3.dp)
                            ) {
                                RadioButton(selected = topUpProvider == code, onClick = { topUpProvider = code })
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = topUpAmountText.toIntOrNull() ?: 0
                        if (amount >= 200) {
                            viewModel.submitTopUpRequest(
                                amount = amount,
                                provider = topUpProvider,
                                reference = topUpReferenceText,
                                receiptImageUri = receiptImageUri,
                                onSuccess = {
                                    showTopUpDialog = false
                                    isAccountCopied = false
                                    topUpReferenceText = ""
                                    receiptImageUri = ""
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إرسال طلب الشحن للمراجعة")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTopUpDialog = false
                    isAccountCopied = false
                }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog: Delete Account
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("حذف الحساب نهائياً", fontWeight = FontWeight.Bold, color = UrgentRed) },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف الحساب؟ سيتم مسح بياناتك وإعلاناتك وفق سياسة الخصوصية وحماية البيانات.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteCurrentAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UrgentRed)
                ) {
                    Text("نعم، حذف الحساب")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("إلغاء") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 90.dp)
    ) {
        // User Profile Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (currentUser?.name ?: "م").take(1),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser?.name ?: "مستخدم سوقي",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (currentUser?.isVerified == true) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "موثوق",
                                        tint = VerifiedBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${currentUser?.phone ?: "+213 555 12 34 56"} • ${currentUser?.commune ?: "حيدرة"}، ${currentUser?.wilaya ?: "الجزائر العاصمة"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${currentUser?.sellerRating ?: 4.9} (${currentUser?.reviewsCount ?: 18} تقييم)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${myListings.size} إعلان",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (currentUser?.isVerified != true) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.requestVerification() },
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = VerifiedBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentUser?.verificationRequested == true) "طلب التوثيق قيد المراجعة ⏳" else "طلب توثيق الحساب بالشارة الزرقاء",
                                fontSize = 12.sp,
                                color = VerifiedBlue
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text("تعديل الملف الشخصي", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onChangePassword,
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تغيير كلمة المرور", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // SECURITY & TRUST CENTER CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSecurity),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مركز الأمان ومكافحة الاحتيال",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "حماية 98%",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "توثيق الهوية KYC، البصمة، الجلسات، وأرقام الطوارئ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("فتح", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // WALLET / BALANCE CARD (Exact user specification)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "محفظة سوقي DZ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            color = EmeraldPrimary,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "دفع رسوم الإعلانات",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "الرصيد المتاح:",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${wallet?.balanceDzd ?: 0} دج",
                        color = GoldLight,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Account RIP summary pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTopUpDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "شحن المحفظة مفعل يدويًا عبر تحويل بريدي موب وإرسال الوصل للمراجعة",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showTopUpDialog = true },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldSecondary)
                        ) {
                            Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شحن المحفظة (وصل)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("سجل العمليات", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabs: My Ads, Favorites, Transactions
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = EmeraldPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("إعلاناتي (${myListings.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("المفضلة (${favorites.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("سجل المحفظة", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                // My Listings
                if (myListings.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("لم تقم بنشر أي إعلان بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(myListings) { ad ->
                        AdCard(
                            listing = ad,
                            isFavorite = favorites.any { it.listingId == ad.id },
                            onFavoriteClick = { viewModel.toggleFavorite(ad.id, true) },
                            onClick = { onAdClick(ad.id) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            1 -> {
                // Favorites
                val favListings = allListings.filter { ad -> favorites.any { it.listingId == ad.id } }
                if (favListings.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("لم تحفظ أي إعلانات في المفضلة بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(favListings) { ad ->
                        AdCard(
                            listing = ad,
                            isFavorite = true,
                            onFavoriteClick = { viewModel.toggleFavorite(ad.id, true) },
                            onClick = { onAdClick(ad.id) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            2 -> {
                // Wallet Transactions & Top-Up Requests
                if (userTopUpRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = "طلبات شحن الرصيد المرسلة للإدارة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(userTopUpRequests) { req ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                when (req.status) {
                                    "PENDING" -> GoldSecondary.copy(alpha = 0.5f)
                                    "APPROVED" -> EmeraldPrimary.copy(alpha = 0.5f)
                                    else -> UrgentRed.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "طلب شحن: ${req.amountDzd} دج (${req.provider})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = formatTimeAgo(req.createdAt),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (req.status) {
                                            "PENDING" -> GoldSecondary.copy(alpha = 0.15f)
                                            "APPROVED" -> EmeraldPrimary.copy(alpha = 0.15f)
                                            else -> UrgentRed.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = when (req.status) {
                                                "PENDING" -> "قيد المراجعة ⏳"
                                                "APPROVED" -> "تم الشحن ✓"
                                                else -> "مرفوض ✕"
                                            },
                                            color = when (req.status) {
                                                "PENDING" -> GoldSecondary
                                                "APPROVED" -> EmeraldPrimary
                                                else -> UrgentRed
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (req.reference.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "رقم المرجع: ${req.reference}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (req.adminNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "ملاحظة الإدارة: ${req.adminNote}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (req.status == "REJECTED") UrgentRed else EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "سجل العمليات المكتملة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                if (walletTransactions.isEmpty() && userTopUpRequests.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("لا توجد عمليات مسجلة في المحفظة حتى الآن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(walletTransactions) { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(tx.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = "${tx.type} • ${formatTimeAgo(tx.timestamp)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = (if (tx.amount > 0) "+ " else "") + "${tx.amount} دج",
                                    color = if (tx.amount > 0) Color(0xFF2E7D32) else UrgentRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings & Legal Actions
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("الإعدادات والمعلومات القانونية:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Security Center Link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSecurity)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("مركز الأمان ومكافحة الاحتيال والتوثيق", fontSize = 13.sp)
                        }
                        Text("عرض", color = EmeraldPrimary, fontSize = 12.sp)
                    }

                    Divider()

                    // Legal Policies & Prohibited Items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenLegal)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gavel, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("شروط الاستخدام وقائمة المواد المحظورة", fontSize = 13.sp)
                        }
                        Text("عرض", color = EmeraldPrimary, fontSize = 12.sp)
                    }

                    Divider()

                    // Delete Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAccountDialog = true }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = UrgentRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("حذف الحساب وبيانات المستخدم", color = UrgentRed, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logout Button
            OutlinedButton(
                onClick = {
                    viewModel.logoutUser {
                        onLogout()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "تسجيل الخروج",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تسجيل الخروج من الحساب",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Official SouqiDz Brand Card
            com.example.ui.components.SouqiDzFullBrandCard(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Version Footer
            Text(
                text = "سوقي DZ • منصة التجارة الآمنة بالجزائر • v2.4.0",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
