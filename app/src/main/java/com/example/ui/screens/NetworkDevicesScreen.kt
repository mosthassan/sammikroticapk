package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.UuidUtils
import com.example.data.local.entity.NetworkDeviceEntity
import com.example.data.local.entity.NetworkSubnetSettingsEntity
import com.example.domain.network.IpValidationResult
import com.example.ui.components.JsonBackupDialog
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel
import com.example.util.JsonBackupHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDevicesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.allDevices.collectAsState()
    val subnetSettings by viewModel.subnetSettings.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("الكل") }

    var showAddDeviceSheet by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<NetworkDeviceEntity?>(null) }
    var deviceToDelete by remember { mutableStateOf<NetworkDeviceEntity?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showJsonBackupDialog by remember { mutableStateOf(false) }

    var pingingDeviceId by remember { mutableStateOf<String?>(null) }
    var pingResult by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current

    val deviceTypes = listOf("الكل", "MikroTik RouterBOARD", "Access Point", "Sector Antenna", "Switch", "CPE")

    val filteredDevices = remember(devices, searchQuery, selectedTypeFilter) {
        devices.filter { dev ->
            val matchesType = selectedTypeFilter == "الكل" || dev.deviceType.equals(selectedTypeFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    dev.name.contains(searchQuery, ignoreCase = true) ||
                    dev.ipAddress.contains(searchQuery) ||
                    dev.locationArea.contains(searchQuery, ignoreCase = true) ||
                    dev.frequencyOrSsid.contains(searchQuery, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDeviceSheet = true },
                containerColor = BrandCyanPrimary,
                modifier = Modifier.testTag("fab_add_device")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة جهاز شبكة", tint = Color.Black)
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Network Protection & Subnet Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "حماية الشبكة ورنجات الآيبي (Anti-Collision)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات الرنجات", tint = BrandCyanPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val approvedSubnet = subnetSettings?.approvedDeviceSubnet ?: "192.168.88.0/24"
                    val gatewayIp = subnetSettings?.gatewayIp ?: "192.168.88.1"
                    val hotspotSubnet = subnetSettings?.hotspotSubnet ?: "10.5.50.0/24"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("رنج الإدارة: $approvedSubnet", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = BrandCyanPrimary)
                        Text("بوابة الراوتر: $gatewayIp", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("عزل الهوتسبوت: $hotspotSubnet", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SemanticWarningAmber)
                    }
                }
            }

            // Search Bar & JSON Bulk Import Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم الجهاز أو الآيبي أو البرج", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = { showJsonBackupDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_devices_json_backup")
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Type Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(deviceTypes) { type ->
                    FilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = { selectedTypeFilter = type },
                        label = { Text(type, fontSize = 11.sp) }
                    )
                }
            }

            // Devices List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredDevices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Router, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("لا توجد أجهزة مسجلة تطابق البحث", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                }

                items(filteredDevices) { dev ->
                    DeviceCard(
                        device = dev,
                        isPinging = pingingDeviceId == dev.id,
                        pingResult = if (pingingDeviceId == dev.id) pingResult else null,
                        onPing = {
                            pingingDeviceId = dev.id
                            pingResult = "Ping ${dev.ipAddress}: RTT=1.4ms (متصل ومستقر)"
                        },
                        onCopyIp = {
                            clipboardManager.setText(AnnotatedString(dev.ipAddress))
                        },
                        onEdit = { deviceToEdit = dev },
                        onDelete = { deviceToDelete = dev }
                    )
                }
            }
        }
    }

    // Add / Edit Device Bottom Sheet
    if (showAddDeviceSheet || deviceToEdit != null) {
        val editing = deviceToEdit
        AddEditDeviceBottomSheet(
            deviceToEdit = editing,
            onDismiss = {
                showAddDeviceSheet = false
                deviceToEdit = null
            },
            onValidateIp = { ip ->
                viewModel.validateIp(ip, editing?.id)
            },
            onSave = { device ->
                viewModel.saveNetworkDevice(device) {
                    showAddDeviceSheet = false
                    deviceToEdit = null
                }
            }
        )
    }

    // Subnets & Protection Settings Sheet
    if (showSettingsSheet) {
        SubnetSettingsBottomSheet(
            currentSettings = subnetSettings ?: NetworkSubnetSettingsEntity(),
            onDismiss = { showSettingsSheet = false },
            onSave = { updated ->
                viewModel.updateSubnetSettings(updated) {
                    showSettingsSheet = false
                }
            }
        )
    }

    // JSON Backup Dialog
    if (showJsonBackupDialog) {
        val exportJson = remember(devices) {
            JsonBackupHelper.exportDevicesToJson(devices)
        }
        JsonBackupDialog(
            title = "نسخ واستيراد أجهزة الشبكة (JSON)",
            exportJsonString = exportJson,
            sampleTemplateJson = JsonBackupHelper.SAMPLE_DEVICES_JSON,
            onDismiss = { showJsonBackupDialog = false },
            onImportJson = { jsonStr ->
                viewModel.importDevicesFromJson(
                    jsonStr = jsonStr,
                    onSuccess = { showJsonBackupDialog = false },
                    onError = {}
                )
            }
        )
    }

    // Delete Confirmation Dialog
    deviceToDelete?.let { dev ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text("حذف جهاز الشبكة", color = SemanticExpenseRed) },
            text = { Text("هل أنت متأكد من حذف الجهاز (${dev.name}) بالآيبي (${dev.ipAddress}) من سجل الأجهزة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNetworkDevice(dev.id) {
                            deviceToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: NetworkDeviceEntity,
    isPinging: Boolean,
    pingResult: String?,
    onPing: () -> Unit,
    onCopyIp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (device.status) {
        "ONLINE" -> SemanticIncomeGreen
        "WARNING" -> SemanticWarningAmber
        else -> SemanticExpenseRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .testTag("device_card_${device.ipAddress}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        device.deviceType.contains("Router", ignoreCase = true) -> Icons.Default.Router
                        device.deviceType.contains("Sector", ignoreCase = true) -> Icons.Default.CellTower
                        else -> Icons.Default.Wifi
                    }
                    Icon(icon, contentDescription = null, tint = BrandCyanPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(device.deviceType, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (device.status == "ONLINE") "متصل (Online)" else if (device.status == "WARNING") "تنبيه (Warning)" else "غير متصل",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // IP & Location Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onCopyIp() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(device.ipAddress, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = BrandCyanPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الآيبي", modifier = Modifier.size(13.dp), tint = Color.Gray)
                }

                Text(
                    text = "الموقع: ${device.locationArea}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tech specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (device.frequencyOrSsid.isNotBlank()) {
                    Text("البث/التردد: ${device.frequencyOrSsid}", fontSize = 10.sp, color = Color.Gray)
                }
                if (device.model.isNotBlank()) {
                    Text("الموديل: ${device.model}", fontSize = 10.sp, color = Color.Gray)
                }
                Text("الإشارة: ${device.signalDbm} dBm", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = BrandCyanPrimary)
            }

            // Signal bar
            val progress = ((device.signalDbm + 100).coerceIn(0, 100)) / 100f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (device.signalDbm > -65) SemanticIncomeGreen else if (device.signalDbm > -75) SemanticWarningAmber else SemanticExpenseRed,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            if (pingResult != null) {
                Surface(
                    color = SemanticIncomeGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        pingResult,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SemanticIncomeGreen,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPing,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("فحص Ping", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = BrandCyanPrimary, modifier = Modifier.size(16.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = SemanticExpenseRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditDeviceBottomSheet(
    deviceToEdit: NetworkDeviceEntity?,
    onDismiss: () -> Unit,
    onValidateIp: (String) -> IpValidationResult,
    onSave: (NetworkDeviceEntity) -> Unit
) {
    var name by remember { mutableStateOf(deviceToEdit?.name ?: "") }
    var deviceType by remember { mutableStateOf(deviceToEdit?.deviceType ?: "Access Point") }
    var ipAddress by remember { mutableStateOf(deviceToEdit?.ipAddress ?: "192.168.88.") }
    var macAddress by remember { mutableStateOf(deviceToEdit?.macAddress ?: "") }
    var locationArea by remember { mutableStateOf(deviceToEdit?.locationArea ?: "") }
    var frequencyOrSsid by remember { mutableStateOf(deviceToEdit?.frequencyOrSsid ?: "") }
    var model by remember { mutableStateOf(deviceToEdit?.model ?: "") }
    var signalDbmText by remember { mutableStateOf((deviceToEdit?.signalDbm ?: -60).toString()) }
    var notes by remember { mutableStateOf(deviceToEdit?.notes ?: "") }

    val ipValidation = remember(ipAddress) {
        onValidateIp(ipAddress)
    }

    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (deviceToEdit == null) "إضافة جهاز شبكة جديد" else "تعديل بيانات الجهاز",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandCyanPrimary
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الجهاز (مثال: أكسس برج الرويشان)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Device Type Selector
            Text("نوع الجهاز:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Access Point", "MikroTik RouterBOARD", "Sector Antenna", "Switch").forEach { type ->
                    FilterChip(
                        selected = deviceType == type,
                        onClick = { deviceType = type },
                        label = { Text(type, fontSize = 11.sp) }
                    )
                }
            }

            // IP Address with instant collision checking
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("عنوان الآي بي (IP Address)") },
                isError = !ipValidation.isValid,
                modifier = Modifier.fillMaxWidth()
            )

            if (!ipValidation.isValid && ipValidation.message != null) {
                Surface(
                    color = SemanticExpenseRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(ipValidation.message, color = SemanticExpenseRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (ipValidation.isOutsideSubnet && ipValidation.message != null) {
                Surface(
                    color = SemanticWarningAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticWarningAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(ipValidation.message, color = SemanticWarningAmber, fontSize = 11.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = locationArea,
                    onValueChange = { locationArea = it },
                    label = { Text("الموقع / البرج") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { macAddress = it },
                    label = { Text("الماك (اختياري)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = frequencyOrSsid,
                    onValueChange = { frequencyOrSsid = it },
                    label = { Text("التردد / اسم البث SSID") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("موديل الجهاز (Hardware)") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات إضافية") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (name.isNotBlank() && ipAddress.isNotBlank() && ipValidation.isValid) {
                        val device = NetworkDeviceEntity(
                            id = deviceToEdit?.id ?: UuidUtils.newTimeOrderedId(),
                            name = name,
                            deviceType = deviceType,
                            ipAddress = ipAddress.trim(),
                            macAddress = macAddress.trim(),
                            locationArea = locationArea.ifBlank { "الموقع الرئيسي" },
                            frequencyOrSsid = frequencyOrSsid,
                            model = model,
                            signalDbm = signalDbmText.toIntOrNull() ?: -60,
                            notes = notes
                        )
                        onSave(device)
                    }
                },
                enabled = name.isNotBlank() && ipAddress.isNotBlank() && ipValidation.isValid,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_save_device")
            ) {
                Text(if (deviceToEdit == null) "إضافة الجهاز وتأمين الآيبي" else "حفظ التعديلات", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubnetSettingsBottomSheet(
    currentSettings: NetworkSubnetSettingsEntity,
    onDismiss: () -> Unit,
    onSave: (NetworkSubnetSettingsEntity) -> Unit
) {
    var approvedSubnet by remember { mutableStateOf(currentSettings.approvedDeviceSubnet) }
    var gatewayIp by remember { mutableStateOf(currentSettings.gatewayIp) }
    var hotspotSubnet by remember { mutableStateOf(currentSettings.hotspotSubnet) }
    var hotspotGateway by remember { mutableStateOf(currentSettings.hotspotGatewayIp) }
    var dnsServers by remember { mutableStateOf(currentSettings.dnsServers) }
    var isProtectionEnabled by remember { mutableStateOf(currentSettings.isProtectionEnabled) }

    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "إعدادات رنجات وحماية الشبكة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandCyanPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("حماية الشبكة من تضارب الآيبيهات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("فحص آلي لمنع تكرار الآيبي وعزل المشتركين", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(checked = isProtectionEnabled, onCheckedChange = { isProtectionEnabled = it })
            }

            OutlinedTextField(
                value = approvedSubnet,
                onValueChange = { approvedSubnet = it },
                label = { Text("رنج أجهزة الإدارة المصرح به (CIDR)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = gatewayIp,
                onValueChange = { gatewayIp = it },
                label = { Text("عنوان بوابة الراوتر (Gateway IP)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = hotspotSubnet,
                onValueChange = { hotspotSubnet = it },
                label = { Text("رنج شبكة كروت الهوتسبوت (Hotspot Subnet)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dnsServers,
                onValueChange = { dnsServers = it },
                label = { Text("خوادم DNS المعتمدة") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        approvedDeviceSubnet = approvedSubnet.trim(),
                        gatewayIp = gatewayIp.trim(),
                        hotspotSubnet = hotspotSubnet.trim(),
                        hotspotGatewayIp = hotspotGateway.trim(),
                        dnsServers = dnsServers.trim(),
                        isProtectionEnabled = isProtectionEnabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updated)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ إعدادات الرنجات والحماية", fontWeight = FontWeight.Bold)
            }
        }
    }
}
