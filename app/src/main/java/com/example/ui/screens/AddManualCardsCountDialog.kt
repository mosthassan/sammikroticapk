package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CardPackageEntity
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.MikroTikPrimary
import com.example.ui.theme.ProfitEmerald
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel

/**
 * نافذة إضافة رصيد كروت يدوياً بالعدد (مطابقة لتصميم 2026 في الصورة 3)
 * - تحتفظ بالحالة وتستمر مفتوحة عند التبديل إلى واتساب
 * - تحديد فئة الكرت من الباقات المعرفة مع أسعار الجملة والبيع
 * - أزرار سريعة للكميات (50، 100، 200، 500، 1 ألف كرت)
 * - ملخص فوري لقيمة الجملة، البيع النهائي، وهامش ربح البقالات
 */
@Composable
fun AddManualCardsCountDialog(
    viewModel: AppViewModel,
    packages: List<CardPackageEntity>,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val initialPkg = remember(packages) {
        packages.find { it.id == viewModel.draftAddCardsPackageId } ?: packages.firstOrNull()
    }

    var selectedPkgId by remember {
        mutableStateOf(viewModel.draftAddCardsPackageId ?: initialPkg?.id ?: "")
    }

    var categoryName by remember {
        mutableStateOf(
            if (viewModel.draftAddCardsCategoryName.isNotBlank()) viewModel.draftAddCardsCategoryName
            else initialPkg?.name ?: "كرت إنترنت"
        )
    }

    var quantity by remember {
        mutableIntStateOf(if (viewModel.draftAddCardsQuantity > 0) viewModel.draftAddCardsQuantity else 100)
    }

    var wholesalePrice by remember {
        mutableDoubleStateOf(
            if (viewModel.draftAddCardsWholesalePriceMinor > 0) viewModel.draftAddCardsWholesalePriceMinor / 100.0
            else (initialPkg?.wholesalePriceMinor ?: 90000L) / 100.0
        )
    }

    var retailPrice by remember {
        mutableDoubleStateOf(
            if (viewModel.draftAddCardsRetailPriceMinor > 0) viewModel.draftAddCardsRetailPriceMinor / 100.0
            else (initialPkg?.retailPriceMinor ?: 100000L) / 100.0
        )
    }

    var notes by remember {
        mutableStateOf(viewModel.draftAddCardsNotes)
    }

    // Calculations (matching image 3)
    val totalWholesaleAmount = quantity * wholesalePrice
    val totalRetailAmount = quantity * retailPrice
    val totalRetailerProfit = (totalRetailAmount - totalWholesaleAmount).coerceAtLeast(0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CyberDarkCanvas,
                border = BorderStroke(1.2.dp, CyberBorder),
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f)
                    .testTag("dialog_add_manual_cards")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header (matching image 3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkSurface)
                            .border(BorderStroke(0.8.dp, CyberBorder))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "إضافة كروت يدوياً بالعدد",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ProfitEmerald.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, ProfitEmerald.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        Icons.Default.Numbers,
                                        contentDescription = null,
                                        tint = ProfitEmerald,
                                        modifier = Modifier.padding(5.dp).size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "إضافة رصيد كروت للمخزن بدون اشتراط أرقام الكروت",
                                color = TextSecondaryDark,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    // Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Package / Category Selection
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "1. فئة الكرت / الباقة المعتمدة:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                if (packages.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(packages) { pkg ->
                                            val isSelected = selectedPkgId == pkg.id
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) ProfitEmerald.copy(alpha = 0.2f) else CyberDarkCardElevated,
                                                border = BorderStroke(1.dp, if (isSelected) ProfitEmerald else CyberBorder),
                                                modifier = Modifier.clickable {
                                                    selectedPkgId = pkg.id
                                                    categoryName = pkg.name
                                                    wholesalePrice = pkg.wholesalePriceMinor / 100.0
                                                    retailPrice = pkg.retailPriceMinor / 100.0
                                                    viewModel.draftAddCardsPackageId = pkg.id
                                                    viewModel.draftAddCardsCategoryName = pkg.name
                                                    viewModel.draftAddCardsWholesalePriceMinor = pkg.wholesalePriceMinor
                                                    viewModel.draftAddCardsRetailPriceMinor = pkg.retailPriceMinor
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = ProfitEmerald, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = pkg.name,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) ProfitEmerald else TextSecondaryDark
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = categoryName,
                                    onValueChange = {
                                        categoryName = it
                                        viewModel.draftAddCardsCategoryName = it
                                    },
                                    label = { Text("اسم الفئة (مثلاً: كروت فئة 200 ريال)", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ProfitEmerald,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedContainerColor = CyberDarkCardElevated,
                                        unfocusedContainerColor = CyberDarkCardElevated
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 2. Quantity
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "2. عدد الكروت (الكمية الإجمالية):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                // Preset Quantity Chips (matching image 3)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(50, 100, 200, 500, 1000).forEach { preset ->
                                        val isSelected = quantity == preset
                                        val label = if (preset >= 1000) "1 ألف كرت" else "$preset كرت"
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MikroTikPrimary.copy(alpha = 0.3f) else CyberDarkCardElevated,
                                            border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    quantity = preset
                                                    viewModel.draftAddCardsQuantity = preset
                                                }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF38BDF8) else TextSecondaryDark,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = quantity.toString(),
                                    onValueChange = {
                                        val q = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1
                                        quantity = q
                                        viewModel.draftAddCardsQuantity = q
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("أدخل العدد يدوياً", fontSize = 11.sp) },
                                    trailingIcon = { Text("# كرت", fontSize = 11.sp, color = ProfitEmerald, modifier = Modifier.padding(end = 12.dp)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ProfitEmerald,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedContainerColor = CyberDarkCardElevated,
                                        unfocusedContainerColor = CyberDarkCardElevated
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 3. Accounting Prices (Wholesale & Retail)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "3. الأسعار المحاسبية (ريال يمني):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = wholesalePrice.toInt().toString(),
                                        onValueChange = {
                                            val p = it.filter { ch -> ch.isDigit() }.toDoubleOrNull() ?: 0.0
                                            wholesalePrice = p
                                        },
                                        label = { Text("سعر الجملة للبقالة", fontSize = 10.5.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ProfitEmerald,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedContainerColor = CyberDarkCardElevated,
                                            unfocusedContainerColor = CyberDarkCardElevated
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = retailPrice.toInt().toString(),
                                        onValueChange = {
                                            val p = it.filter { ch -> ch.isDigit() }.toDoubleOrNull() ?: 0.0
                                            retailPrice = p
                                        },
                                        label = { Text("سعر البيع للزبون", fontSize = 10.5.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ProfitEmerald,
                                            unfocusedBorderColor = CyberBorder,
                                            focusedContainerColor = CyberDarkCardElevated,
                                            unfocusedContainerColor = CyberDarkCardElevated
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 4. Calculations Summary Box (matching image 3)
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CyberDarkCardElevated,
                                border = BorderStroke(1.dp, MikroTikPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "ملخص الحسبة لهذه الكمية:",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("إجمالي عدد الكروت المضافة:", fontSize = 11.sp, color = TextSecondaryDark)
                                        Text("$quantity كرت", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("إجمالي قيمة الجملة (المستحقة):", fontSize = 11.sp, color = TextSecondaryDark)
                                        Text("${totalWholesaleAmount.toInt()} ريال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ProfitEmerald)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("إجمالي قيمة البيع النهائي:", fontSize = 11.sp, color = TextSecondaryDark)
                                        Text("${totalRetailAmount.toInt()} ريال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("هامش ربح البقالات المتوقع:", fontSize = 11.sp, color = TextSecondaryDark)
                                        Text("+${totalRetailerProfit.toInt()} ريال", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkSurface)
                            .border(BorderStroke(0.8.dp, CyberBorder))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Text("إلغاء", color = TextSecondaryDark, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val targetPkgId = selectedPkgId.ifBlank { packages.firstOrNull()?.id ?: "PKG_DEF" }
                                viewModel.receiveCardStock(
                                    packageId = targetPkgId,
                                    quantity = quantity,
                                    notes = "إضافة كروت يدوياً: $categoryName"
                                ) {
                                    viewModel.clearAddManualCardsDraft()
                                    onSubmitSuccess()
                                }
                            },
                            enabled = quantity > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = ProfitEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_confirm_add_manual_cards")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ إضافة $quantity كرت للمخزن",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
