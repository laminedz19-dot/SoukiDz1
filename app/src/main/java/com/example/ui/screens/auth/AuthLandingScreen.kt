package com.example.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SouqiDzFullBrandCard
import com.example.ui.theme.EmeraldPrimary

@Composable
fun AuthLandingScreen(
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onGuest: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SouqiDzFullBrandCard(
            logoSize = 82.dp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "مرحبًا بك في سوقي DZ",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "أنشئ حسابًا جديدًا أو سجّل الدخول للمتابعة",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(
            onClick = onRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HowToReg,
                contentDescription = null,
                tint = EmeraldPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "التسجيل / إنشاء حساب",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = null,
                tint = EmeraldPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تسجيل الدخول بحساب موجود",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        androidx.compose.material3.TextButton(
            onClick = onGuest,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "متابعة التصفح كزائر",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "بيع وشراء الأشياء المستعملة بسهولة وأمان",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
