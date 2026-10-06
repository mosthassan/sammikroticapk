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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ledger.AccountConstants
import com.example.core.model.Money
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.PartyEntity
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
import com.example.ui.viewmodel.SalesItemDraftState

/**
 * نافذة إصدار فاتورة مبيعات كروت جديدة (مطابقة لتصميم 2026 في الصورة 1)
 * - تحتفظ بالحالة وتستمر مفتوحة عند التبديل إلى واتساب أو أي تطبيق آخر
 * - قائمة عملاء تفاعلية مع شارات الأرصدة (خالص الحساب / مديونية)
 * - عداد بطاقات ذكي مع أزرار سريعة (10، 20، 50، 100 كرت، كامل المخزن)
 * - مؤشر فحص المخزن وتنبيه عند تجاوز الرصيد المتاح
 * - خيارات سداد (آجل، نقداً)
 */
@Composable
fun CreateCardSalesInvoiceDialog(
    viewModel: AppViewModel,
    parties: List<PartyEntity>,
    packages: List<CardPackageEntity>,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val stockMap = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(packages) {
        packages.forEach { pkg ->
            val count = viewModel.db.cardPackageDao().getStockBalance(pkg.id)
            stockMap[pkg.id] = count
        }
    }

    val customerParties = remember(parties) {
        parties.filter { it.isCustomer || it.id == AppDatabase.WALK_IN_CASH_PARTY_ID }
    }

    val selectedPartyId = viewModel.draftSalesPartyId ?: customerParties.firstOrNull()?.id ?: AppDatabase.WALK_IN_CASH_PARTY_ID

    val filteredCustomers = remember(viewModel.draftSalesCustomerSearch, customerParties) {
        if (viewModel.draftSalesCustomerSearch.isBlank()) {
            customerParties
        } else {
            customerParties.filter {
                it.name.contains(viewModel.draftSalesCustomerSearch, ignoreCase = true) ||
                it.phone.contains(viewModel.draftSalesCustomerSearch)
            }
        }
    }

    // Party balances calculation for status display (e.g. خالص الحساب)
    val partyBalances = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(customerParties) {
        customerParties.forEach { p ->
            val balance = viewModel.db.journalDao().getNetDebitBalanceForParty(AccountConstants.ACCOUNTS_RECEIVABLE, p.id)
            partyBalances[p.id] = balance
        }
    }

    val totalInvoiceMinor = viewModel.draftSalesItems.sumOf { it.lineTotalMinor }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
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
                    .fillMaxHeight(0.94f)
                    .testTag("dialog_create_sales_invoice")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header (matching image 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkSurface)
                            .border(BorderStroke(0.8.dp, CyberBorder))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MikroTikCyan.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, MikroTikCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onDismiss() }
                            ) {
                                Text(
                                    text = "حفظ ومتابعة لاحقاً",
                                    color = MikroTikCyan,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "فاتورة مبيعات كروت جديدة",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MikroTikPrimary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MikroTikPrimary.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MikroTikCyan,
                                        modifier = Modifier.padding(5.dp).size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "يتم حفظ الأصناف تلقائياً حتى عند الانتقال للواتساب",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Scrollable content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Customer Search & Selection
                        item {
                            OutlinedTextField(
                                value = viewModel.draftSalesCustomerSearch,
                                onValueChange = { viewModel.draftSalesCustomerSearch = it },
                                placeholder = { Text("بحث باسم العميل أو البقالة أو الهاتف...", fontSize = 11.5.sp, color = TextSecondaryDark) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MikroTikCyan,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedContainerColor = CyberDarkCardElevated,
                                    unfocusedContainerColor = CyberDarkCardElevated
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sales_customer_search")
                            )
                        }

                        // List of customers as selectable cards (matching image 1)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredCustomers.take(5).forEach { party ->
                                    val isSelected = selectedPartyId == party.id
                                    val bal = partyBalances[party.id] ?: 0L
                                    val balanceText = if (bal <= 0L) "خالص الحساب" else "عليه: ${bal / 100} ر.ي"
                                    val balanceColor = if (bal <= 0L) Color(0xFF38BDF8) else Color(0xFFF59E0B)

                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) MikroTikPrimary.copy(alpha = 0.18f) else CyberDarkCardElevated,
                                        border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.draftSalesPartyId = party.id }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = balanceText,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = balanceColor
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = party.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else TextPrimaryDark
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { viewModel.draftSalesPartyId = party.id },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = MikroTikCyan,
                                                        unfocusedColor = TextSecondaryDark
                                                    ),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Line Items Header: أصناف الفاتورة + زر إضافة صنف
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val firstPkg = packages.firstOrNull()
                                        viewModel.draftSalesItems.add(
                                            SalesItemDraftState(
                                                packageId = firstPkg?.id,
                                                packageName = firstPkg?.name ?: "باقة كروت إضافية",
                                                quantity = 10,
                                                unitPriceMinor = firstPkg?.wholesalePriceMinor ?: 10000L,
                                                retailPriceMinor = firstPkg?.retailPriceMinor ?: 20000L
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ProfitEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_add_line_item")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ إضافة صنف آخر", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "أصناف الفاتورة (${viewModel.draftSalesItems.size} أصناف):",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        // Line Items Cards (matching image 1 with counter and price inputs)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                viewModel.draftSalesItems.forEachIndexed { index, item ->
                                    val currentPkg = packages.find { it.id == item.packageId } ?: packages.firstOrNull()
                                    val inStock = currentPkg?.let { stockMap[it.id] } ?: 0
                                    val isExceeding = item.quantity > inStock

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CyberDarkCardElevated,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isExceeding) Color(0xFFEF4444) else CyberBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            // Top Row: Item Header & Stock Badge
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "المخزن: $inStock كرت",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (inStock > 0) ProfitEmerald else Color(0xFFEF4444)
                                                    )
                                                    if (viewModel.draftSalesItems.size > 1) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        IconButton(
                                                            onClick = { viewModel.draftSalesItems.removeAt(index) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "حذف الصنف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.packageName.ifBlank { currentPkg?.name ?: "باقة كروت" },
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MikroTikPrimary.copy(alpha = 0.3f)
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            color = MikroTikCyan,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Package switcher chips if multiple packages
                                            if (packages.size > 1) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    packages.take(4).forEach { pkg ->
                                                        val isPkgSelected = item.packageId == pkg.id
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isPkgSelected) MikroTikPrimary.copy(alpha = 0.25f) else CyberDarkSurface,
                                                            border = BorderStroke(0.8.dp, if (isPkgSelected) MikroTikCyan else CyberBorder),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable {
                                                                    item.packageId = pkg.id
                                                                    item.packageName = pkg.name
                                                                    item.unitPriceMinor = pkg.wholesalePriceMinor
                                                                    item.retailPriceMinor = pkg.retailPriceMinor
                                                                }
                                                        ) {
                                                            Text(
                                                                text = pkg.name,
                                                                fontSize = 9.5.sp,
                                                                color = if (isPkgSelected) Color.White else TextSecondaryDark,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.padding(vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // 3 Columns: Counter | Unit Price | Total (matching image 1)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Total Preview Box
                                                Column(modifier = Modifier.weight(1.1f)) {
                                                    Text("الإجمالي:", fontSize = 9.5.sp, color = TextSecondaryDark)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MikroTikPrimary.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, MikroTikPrimary.copy(alpha = 0.4f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "${item.lineTotalMinor / 100} ر.ي",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ProfitEmerald,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(vertical = 7.dp)
                                                        )
                                                    }
                                                }

                                                // Unit Price
                                                Column(modifier = Modifier.weight(1.05f)) {
                                                    Text("سعر الكرت (ر.ي):", fontSize = 9.5.sp, color = TextSecondaryDark)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    OutlinedTextField(
                                                        value = (item.unitPriceMinor / 100).toString(),
                                                        onValueChange = { str ->
                                                            val p = str.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                                            item.unitPriceMinor = p * 100L
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = MikroTikCyan,
                                                            unfocusedBorderColor = CyberBorder,
                                                            focusedContainerColor = CyberDarkSurface,
                                                            unfocusedContainerColor = CyberDarkSurface
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                // Counter: [-] [ Qty ] [+]
                                                Column(modifier = Modifier.weight(1.25f)) {
                                                    Text("العدد (اكتب يدوياً):", fontSize = 9.5.sp, color = TextSecondaryDark)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        IconButton(
                                                            onClick = { if (item.quantity > 1) item.quantity-- },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Remove, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                        OutlinedTextField(
                                                            value = item.quantity.toString(),
                                                            onValueChange = { qStr ->
                                                                val q = qStr.filter { it.isDigit() }.toIntOrNull() ?: 1
                                                                item.quantity = q
                                                            },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            singleLine = true,
                                                            shape = RoundedCornerShape(8.dp),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = MikroTikCyan,
                                                                unfocusedBorderColor = CyberBorder,
                                                                focusedContainerColor = CyberDarkSurface,
                                                                unfocusedContainerColor = CyberDarkSurface
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(
                                                            onClick = { item.quantity++ },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            // Quick preset quantity chips (matching image 1)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(10, 20, 50, 100).forEach { preset ->
                                                    val isSelectedPreset = item.quantity == preset
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isSelectedPreset) MikroTikPrimary.copy(alpha = 0.3f) else CyberDarkSurface,
                                                        border = BorderStroke(0.8.dp, if (isSelectedPreset) MikroTikCyan else CyberBorder),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { item.quantity = preset }
                                                    ) {
                                                        Text(
                                                            text = "$preset كرت",
                                                            fontSize = 9.sp,
                                                            color = if (isSelectedPreset) Color(0xFF38BDF8) else TextSecondaryDark,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                                if (inStock > 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ProfitEmerald.copy(alpha = 0.2f),
                                                        border = BorderStroke(0.8.dp, ProfitEmerald),
                                                        modifier = Modifier
                                                            .weight(1.3f)
                                                            .clickable { item.quantity = inStock }
                                                    ) {
                                                        Text(
                                                            text = "كامل المخزن",
                                                            fontSize = 9.sp,
                                                            color = ProfitEmerald,
                                                            fontWeight = FontWeight.Bold,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Exceeding stock warning banner
                                            if (isExceeding) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "تنبيه: الكمية المطلوبة (${item.quantity}) تتجاوز الرصيد المتوفر في المخزن ($inStock)!",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Payment Type & Notes
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("طريقة السداد:", fontSize = 11.sp, color = TextSecondaryDark)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (viewModel.draftSalesPaymentType == "CREDIT") MikroTikPrimary.copy(alpha = 0.2f) else CyberDarkSurface,
                                        border = BorderStroke(1.dp, if (viewModel.draftSalesPaymentType == "CREDIT") MikroTikCyan else CyberBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.draftSalesPaymentType = "CREDIT" }
                                    ) {
                                        Text(
                                            text = "آجل (ذمة العميل)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.draftSalesPaymentType == "CREDIT") Color.White else TextSecondaryDark,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (viewModel.draftSalesPaymentType == "CASH") ProfitEmerald.copy(alpha = 0.2f) else CyberDarkSurface,
                                        border = BorderStroke(1.dp, if (viewModel.draftSalesPaymentType == "CASH") ProfitEmerald else CyberBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.draftSalesPaymentType = "CASH" }
                                    ) {
                                        Text(
                                            text = "نقداً (صندوق المبيعات)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.draftSalesPaymentType == "CASH") Color.White else TextSecondaryDark,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = viewModel.draftSalesNotes,
                                    onValueChange = { viewModel.draftSalesNotes = it },
                                    label = { Text("ملاحظات الفاتورة", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MikroTikCyan,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedContainerColor = CyberDarkSurface,
                                        unfocusedContainerColor = CyberDarkSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Bottom Bar (matching image 1)
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
                            onClick = {
                                viewModel.clearSalesInvoiceDraft()
                                onDismiss()
                            },
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Text("تفريغ وبدء جديد", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.6f)
                        ) {
                            Text("إغلاق", color = TextSecondaryDark, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (viewModel.draftSalesItems.isNotEmpty()) {
                                    val specs = viewModel.draftSalesItems.map { itm ->
                                        SalesItemSpec(
                                            description = itm.packageName.ifBlank { "كروت إنترنت" },
                                            packageId = itm.packageId,
                                            quantity = itm.quantity,
                                            unitPriceMinor = itm.unitPriceMinor
                                        )
                                    }
                                    val isCash = viewModel.draftSalesPaymentType == "CASH"
                                    if (isCash && specs.size == 1) {
                                        // Quick sale flow
                                        val single = specs.first()
                                        viewModel.postQuickSale(
                                            partyId = selectedPartyId,
                                            packageId = single.packageId ?: packages.firstOrNull()?.id ?: "PKG_DEF",
                                            description = single.description,
                                            quantity = single.quantity,
                                            unitPriceMinor = single.unitPriceMinor,
                                            cashPaidMinor = single.quantity * single.unitPriceMinor,
                                            treasuryId = "TR_MAIN_YER",
                                            onSuccess = {
                                                viewModel.clearSalesInvoiceDraft()
                                                onSubmitSuccess()
                                            }
                                        )
                                    } else {
                                        viewModel.postSalesInvoice(
                                            partyId = selectedPartyId,
                                            cardItems = specs,
                                            serviceItems = emptyList(),
                                            currency = com.example.core.model.CurrencyCode.YER,
                                            exchangeRate = com.example.core.model.ExchangeRate.parity(com.example.core.model.CurrencyCode.YER),
                                            notes = viewModel.draftSalesNotes,
                                            onSuccess = {
                                                viewModel.clearSalesInvoiceDraft()
                                                onSubmitSuccess()
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = viewModel.draftSalesItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MikroTikPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_confirm_sales_invoice")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "إصدار الفاتورة (${totalInvoiceMinor / 100} ر.ي)",
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
