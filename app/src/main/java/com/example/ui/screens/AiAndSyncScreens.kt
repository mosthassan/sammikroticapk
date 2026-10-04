package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.AnomalySeverity
import com.example.domain.security.UserRole
import com.example.domain.sync.SyncEngineStatus
import com.example.ui.theme.BrandBlueSecondary
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun AiAndSyncScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var mainSubTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("المساعد الذكي (Gemini)", "المزامنة السحابية والأمان")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = mainSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = BrandCyanPrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = mainSubTab == index,
                    onClick = { mainSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (mainSubTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (mainSubTab) {
            0 -> AiSection(viewModel)
            1 -> SyncAndSecuritySection(viewModel)
        }
    }
}

@Composable
fun AiSection(viewModel: AppViewModel) {
    var aiSubTab by rememberSaveable { mutableIntStateOf(0) }
    val aiTabs = listOf("الملخص المالي", "استفسارات المحاسبة", "مسودة الفاتورة (OCR)", "كاشف الشذوذ", "تذكير البقالات")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = aiSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            edgePadding = 8.dp
        ) {
            aiTabs.forEachIndexed { idx, title ->
                Tab(
                    selected = aiSubTab == idx,
                    onClick = { aiSubTab = idx },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        when (aiSubTab) {
            0 -> SmartSummaryTab(viewModel)
            1 -> AccountingAssistantTab(viewModel)
            2 -> InvoiceOcrTab(viewModel)
            3 -> AnomalyDetectorTab(viewModel)
            4 -> DebtRemindersTab(viewModel)
        }
    }
}

@Composable
fun SmartSummaryTab(viewModel: AppViewModel) {
    val summary by viewModel.smartSummary.collectAsState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (summary == null) {
            isLoading = true
            viewModel.loadSmartSummary(2026, 10)
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "الملخص المالي الذكي (Gemini)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandCyanPrimary
                        )
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                viewModel.loadSmartSummary(2026, 10)
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = BrandCyanPrimary)
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandCyanPrimary)
                        }
                    } else if (summary != null) {
                        val s = summary!!
                        Text(
                            text = s.executiveSummaryAr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("النقاط الجوهرية:", fontWeight = FontWeight.Bold, color = SemanticWarningAmber)
                        s.highlights.forEach { h ->
                            Text("• $h", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("المصادر والاستشهادات (Citations من الأستاذ):", fontWeight = FontWeight.Bold, color = BrandCyanPrimary)
                        s.citations.forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(c.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(c.sourceReport, fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Text(c.value, fontWeight = FontWeight.Bold, color = BrandCyanPrimary, fontSize = 12.sp)
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
fun AccountingAssistantTab(viewModel: AppViewModel) {
    val history by viewModel.assistantHistory.collectAsState()
    var inputQuestion by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "مساعد المحاسبة (قراءة فقط - Zero Write Access)",
                            fontWeight = FontWeight.Bold,
                            color = BrandCyanPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            "يمكنك الاستفسار عن أرصدة البقالات، تكلفة الإنترنت 5101، أرباح الفترة، أو أرصدة الصناديق. يعتمد النموذج على أدوات استعلام مشتقة مباشرة من قيود الأستاذ العام.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            items(history) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("السؤال: ${item.question}", fontWeight = FontWeight.Bold, color = SemanticWarningAmber, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.answerText, fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("البيانات المعتمدة: ${item.citedToolData}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputQuestion,
                onValueChange = { inputQuestion = it },
                placeholder = { Text("مثال: كم رصيد الصندوق؟ ما هي تكلفة الإنترنت؟") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("assistant_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputQuestion.isNotBlank()) {
                        val q = inputQuestion
                        inputQuestion = ""
                        scope.launch {
                            isSending = true
                            viewModel.askAssistant(q)
                            isSending = false
                        }
                    }
                },
                enabled = !isSending && inputQuestion.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                modifier = Modifier.testTag("assistant_send_btn")
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "إرسال")
                }
            }
        }
    }
}

@Composable
fun InvoiceOcrTab(viewModel: AppViewModel) {
    val draftInvoice by viewModel.draftOcrInvoice.collectAsState()
    var scannedText by remember {
        mutableStateOf(
            """
            Starlink Internet Services
            Date: 2026-10-04
            Bandwidth Subscription: 1 Gbps Priority
            Total Amount: $150.00 USD
            Paid by: Cash
            """.trimIndent()
        )
    }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "تحويل صورة / نص الفاتورة إلى مسودة مشتريات (OCR)",
                        fontWeight = FontWeight.Bold,
                        color = BrandCyanPrimary
                    )
                    Text(
                        "يقوم الذكاء الاصطناعي باستخراج بنود المشتريات وعرضها كمسودة فقط. لا يتم ترحيل أي قيد إلى دفتر الأستاذ إلا بعد مراجعة واعتماد المحاسب صراحة.",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = scannedText,
                        onValueChange = { scannedText = it },
                        label = { Text("النص المقروء من الفاتورة / السند") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.scanInvoiceText(scannedText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استخراج المسودة وتحليل البنود")
                    }
                }
            }
        }

        if (draftInvoice != null) {
            val draft = draftInvoice!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مسودة فاتورة المشتريات الناتجة", fontWeight = FontWeight.Bold, color = SemanticWarningAmber)
                            Text("درجة الثقة: ${(draft.totalConfidence * 100).toInt()}%", fontSize = 12.sp, color = SemanticIncomeGreen)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("المورد المقترح: ${draft.suggestedVendorName} (ثقة ${(draft.vendorConfidence * 100).toInt()}%)")
                        Text("المبلغ الإجمالي: ${draft.totalAmount.format()}")

                        Spacer(modifier = Modifier.height(8.dp))
                        draft.items.forEach { item ->
                            Text("• ${item.description} - حساب: ${item.accountCode} - ${item.totalMinor / 100}$", fontSize = 12.sp)
                        }

                        if (draft.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            draft.warnings.forEach { w ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(w, fontSize = 11.sp, color = SemanticExpenseRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.approveAndPostDraftInvoice(draft) {
                                    // posted
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SemanticIncomeGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اعتماد وترحيل الفاتورة رسمياً إلى الأستاذ")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnomalyDetectorTab(viewModel: AppViewModel) {
    val anomalies by viewModel.detectedAnomalies.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.scanAnomalies()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("كاشف الشذوذ المالي (Deterministic Rules)", fontWeight = FontWeight.Bold, color = BrandCyanPrimary)
                        Text("فحص فوري دون اتصال لرصد التكرار، السحب الزائد، أو قفزات المصاريف.", fontSize = 11.sp, color = Color.LightGray)
                    }
                    IconButton(onClick = { scope.launch { viewModel.scanAnomalies() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "فحص مجدداً", tint = BrandCyanPrimary)
                    }
                }
            }
        }

        if (anomalies.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لم يتم رصد أي شذوذ أو تكرار مالي في النظام. جميع الحركات سليمة 100%.", color = SemanticIncomeGreen, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(anomalies) { anomaly ->
                val badgeColor = when (anomaly.severity) {
                    AnomalySeverity.HIGH -> SemanticExpenseRed
                    AnomalySeverity.MEDIUM -> SemanticWarningAmber
                    AnomalySeverity.LOW -> BrandCyanPrimary
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = badgeColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(anomaly.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = badgeColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        anomaly.severity.labelAr,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(anomaly.description, fontSize = 12.sp, color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtRemindersTab(viewModel: AppViewModel) {
    val reminders by viewModel.debtReminders.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadDebtReminders()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مسودات تذكير ديون البقالات (واتساب)", fontWeight = FontWeight.Bold, color = BrandCyanPrimary)
                    Text(
                        "توليد رسائل تذكير مهذبة ومخصصة لكل بقالة. لا تُرسل الرسائل آلياً، بل تُتاح لك خيارات النسخ أو المشاركة المباشرة.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        if (reminders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد ديون مستحقة على أي وكيل أو بقالة حالياً!", color = SemanticIncomeGreen)
                }
            }
        } else {
            items(reminders) { rem ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rem.partyName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(rem.totalDue.format(), fontWeight = FontWeight.Bold, color = BrandCyanPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                rem.messageText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(rem.messageText))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ النص")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, rem.messageText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "إرسال التذكير عبر"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SemanticIncomeGreen)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncAndSecuritySection(viewModel: AppViewModel) {
    val syncState by viewModel.syncState.collectAsState()
    val currentRole by viewModel.currentRole.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (syncState.status == SyncEngineStatus.SYNCING) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = BrandCyanPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "المزامنة السحابية (Offline-First)",
                                fontWeight = FontWeight.Bold,
                                color = BrandCyanPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (syncState.status) {
                                SyncEngineStatus.IDLE -> SemanticIncomeGreen.copy(alpha = 0.2f)
                                SyncEngineStatus.SYNCING -> BrandCyanPrimary.copy(alpha = 0.2f)
                                SyncEngineStatus.ERROR -> SemanticExpenseRed.copy(alpha = 0.2f)
                                SyncEngineStatus.OFFLINE -> Color.Gray.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                syncState.status.labelAr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (syncState.status) {
                                    SyncEngineStatus.IDLE -> SemanticIncomeGreen
                                    SyncEngineStatus.SYNCING -> BrandCyanPrimary
                                    SyncEngineStatus.ERROR -> SemanticExpenseRed
                                    SyncEngineStatus.OFFLINE -> Color.Gray
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "يعمل التطبيق بمبدأ Offline-First؛ فقاعدة Room المحلية هي مصدر الحقيقة المستمر، ويتم رفع التغييرات إلى السحابة فور توفر الاتصال مع التحقق الدائم من توازن ميزان المراجعة قبل الاعتماد.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("العمليات المعلقة في الصندوق (Outbox): ${syncState.pendingOutboxCount}", fontSize = 12.sp)
                        Text("التعارضات المسجلة: ${syncState.conflictCount}", fontSize = 12.sp)
                    }

                    if (syncState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("تنبيه: ${syncState.errorMessage}", fontSize = 11.sp, color = SemanticExpenseRed)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { scope.launch { viewModel.triggerSync() } },
                        enabled = syncState.status != SyncEngineStatus.SYNCING,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (syncState.status == SyncEngineStatus.SYNCING) "جارٍ المزامنة..." else "مزامنة الآن مع السحابة")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SemanticWarningAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إدارة الأدوار والصلاحيات (RBAC Enforcement)", fontWeight = FontWeight.Bold, color = SemanticWarningAmber)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "تُفرَض الصلاحيات في طبقة UseCase وليس في الواجهة فقط. حدد الدور النشط لاختبار القيود الأمنية والتسجيل في سجل التدقيق:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    UserRole.values().forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentRole == role) SemanticWarningAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                .clickable { viewModel.switchRole(role) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(role.titleAr, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val desc = when (role) {
                                    UserRole.OWNER -> "صلاحيات كاملة + إقفال سنوي + إدارة المنشأة"
                                    UserRole.ACCOUNTANT -> "ترحيل فواتير، سندات، إهلاك، إقفال شهري"
                                    UserRole.CASHIER -> "فواتير وسندات قبض فقط"
                                    UserRole.VIEWER -> "قراءة واستعلام عن التقارير فقط"
                                }
                                Text(desc, fontSize = 10.sp, color = Color.Gray)
                            }
                            if (currentRole == role) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticWarningAmber)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BrandCyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("قفل التطبيق وحماية البيانات", fontWeight = FontWeight.Bold, color = BrandCyanPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("حماية سرية البيانات المالية برمز PIN أو البصمة البيومترية عند مغادرة التطبيق.", fontSize = 11.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.togglePinLock() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("تفعيل / تبديل رمز القفل (PIN 1234)")
                    }
                }
            }
        }
    }
}
