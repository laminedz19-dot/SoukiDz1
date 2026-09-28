package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CategoriesData
import com.example.ui.components.formatDzd
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.UrgentRed
import com.example.ui.viewmodel.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MarketplaceViewModel,
    onAdClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSellClick: () -> Unit
) {
    val listings by viewModel.publishedListings.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Search & Wilaya Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(onClick = onSearchClick)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ابحث عن سيارات، عقارات، هواتف...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Promotional Banner / Fast CTA
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "بيع أي حاجة ما تحتاجهاش!",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "انشر إعلانك ووصل لملايين المشترين في 58 ولاية",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onSellClick,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldSecondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("انشر الآن", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Categories Row
        item {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = "الأقسام الرئيسية",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CategoriesData.allCategories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onCategoryClick(cat.id) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    CategoriesData.getCategoryIcon(cat.iconName),
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(cat.nameAr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Featured / Urgent Ads
        val urgentAds = listings.filter { it.isUrgent || it.isFeatured }
        if (urgentAds.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عروض مميزة وعاجلة 🔥", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(urgentAds, key = { it.id }) { ad ->
                        val firstImg = ad.imagesJson.split(",").firstOrNull()?.trim() ?: ""
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .clickable { onAdClick(ad.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    if (firstImg.isNotBlank()) {
                                        AsyncImage(
                                            model = firstImg,
                                            contentDescription = ad.title,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (ad.isUrgent) {
                                        Surface(
                                            color = UrgentRed,
                                            shape = RoundedCornerShape(bottomStart = 8.dp),
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Text("عاجل", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(ad.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text(formatDzd(ad.priceDzd), color = EmeraldPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    Text("${ad.wilayaName} • ${formatTimeAgo(ad.createdAt)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Latest Ads Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "أحدث الإعلانات (${listings.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Ads List
        items(listings, key = { it.id }) { ad ->
            val firstImg = ad.imagesJson.split(",").firstOrNull()?.trim() ?: ""
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onAdClick(ad.id) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (firstImg.isNotBlank()) {
                            AsyncImage(
                                model = firstImg,
                                contentDescription = ad.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(ad.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(formatDzd(ad.priceDzd), color = EmeraldPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ad.wilayaName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatTimeAgo(ad.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
