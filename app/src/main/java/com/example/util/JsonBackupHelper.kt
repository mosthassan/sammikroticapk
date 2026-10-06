package com.example.util

import com.example.core.model.UuidUtils
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.NetworkDeviceEntity
import com.example.data.local.entity.PartyEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedPurchaseItem(
    val description: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val isFixedAsset: Boolean = false,
    val usefulLifeMonths: Int = 24
)

data class ParsedPurchaseInvoice(
    val supplierName: String,
    val invoiceNumber: String,
    val currency: String,
    val totalMinor: Long,
    val notes: String,
    val items: List<ParsedPurchaseItem>
)

object JsonBackupHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    // ==========================================
    // 1. CUSTOMERS & PARTIES JSON BACKUP
    // ==========================================

    fun exportCustomersToJson(parties: List<PartyEntity>, networkName: String = "SamMikrotik"): String {
        val root = JSONObject()
        root.put("format", "SAM_CUSTOMERS_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("exportedAt", dateFormat.format(Date()))
        root.put("totalCount", parties.size)

        val array = JSONArray()
        parties.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("phone", p.phone)
            obj.put("isCustomer", p.isCustomer)
            obj.put("isVendor", p.isVendor)
            obj.put("isPartner", p.isPartner)
            obj.put("creditLimitMinor", p.creditLimitMinor)
            obj.put("creditLimitYemeniRial", p.creditLimitMinor / 100L)
            array.put(obj)
        }
        root.put("parties", array)
        return root.toString(2)
    }

    fun parseCustomersFromJson(jsonString: String): Result<List<PartyEntity>> {
        return runCatching {
            val trimmed = jsonString.trim()
            val list = mutableListOf<PartyEntity>()

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parsePartyObject(obj)?.let { list.add(it) }
                }
            } else {
                val root = JSONObject(trimmed)
                val array = when {
                    root.has("parties") -> root.getJSONArray("parties")
                    root.has("customers") -> root.getJSONArray("customers")
                    root.has("retailers") -> root.getJSONArray("retailers")
                    else -> null
                }
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        parsePartyObject(obj)?.let { list.add(it) }
                    }
                } else if (root.has("name")) {
                    parsePartyObject(root)?.let { list.add(it) }
                } else {
                    throw IllegalArgumentException("الملف لا يحتوي على مصفوفة أطراف أو عملاء صالحة (parties / customers)")
                }
            }

            if (list.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على أي عملاء صالحين داخل ملف JSON")
            }
            list
        }
    }

    private fun parsePartyObject(obj: JSONObject): PartyEntity? {
        val name = obj.optString("name", obj.optString("customerName", "")).trim()
        if (name.isBlank()) return null

        val phone = obj.optString("phone", obj.optString("mobile", "")).trim()
        val isCust = obj.optBoolean("isCustomer", true)
        val isVend = obj.optBoolean("isVendor", false)
        val isPart = obj.optBoolean("isPartner", false)

        val creditLimit = if (obj.has("creditLimitMinor")) {
            obj.optLong("creditLimitMinor", 0L)
        } else {
            (obj.optDouble("creditLimitYemeniRial", obj.optDouble("balanceOwed", 0.0)) * 100.0).toLong()
        }

        return PartyEntity(
            id = UuidUtils.newTimeOrderedId(),
            name = name,
            phone = phone,
            isCustomer = isCust,
            isVendor = isVend,
            isPartner = isPart,
            creditLimitMinor = creditLimit.coerceAtLeast(0L),
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
    }

    // ==========================================
    // 2. NETWORK DEVICES JSON BACKUP
    // ==========================================

    fun exportDevicesToJson(devices: List<NetworkDeviceEntity>, networkName: String = "SamMikrotik"): String {
        val root = JSONObject()
        root.put("format", "SAM_NETWORK_DEVICES_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("exportedAt", dateFormat.format(Date()))
        root.put("deviceCount", devices.size)

        val array = JSONArray()
        devices.forEach { dev ->
            val obj = JSONObject()
            obj.put("name", dev.name)
            obj.put("deviceType", dev.deviceType)
            obj.put("ipAddress", dev.ipAddress)
            obj.put("macAddress", dev.macAddress)
            obj.put("locationArea", dev.locationArea)
            obj.put("portOrInterface", dev.portOrInterface)
            obj.put("frequencyOrSsid", dev.frequencyOrSsid)
            obj.put("model", dev.model)
            obj.put("username", dev.username)
            obj.put("status", dev.status)
            obj.put("signalDbm", dev.signalDbm)
            obj.put("uptimeHours", dev.uptimeHours)
            obj.put("notes", dev.notes)
            array.put(obj)
        }
        root.put("devices", array)
        return root.toString(2)
    }

    fun parseDevicesFromJson(jsonString: String): Result<List<NetworkDeviceEntity>> {
        return runCatching {
            val trimmed = jsonString.trim()
            val list = mutableListOf<NetworkDeviceEntity>()

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseDeviceObject(obj)?.let { list.add(it) }
                }
            } else {
                val root = JSONObject(trimmed)
                if (root.has("devices")) {
                    val array = root.getJSONArray("devices")
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        parseDeviceObject(obj)?.let { list.add(it) }
                    }
                } else if (root.has("name") || root.has("ipAddress")) {
                    parseDeviceObject(root)?.let { list.add(it) }
                } else {
                    throw IllegalArgumentException("الملف لا يحتوي على مصفوفة أجهزة صالحة (devices)")
                }
            }

            if (list.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على أي أجهزة شبكة صالحة داخل ملف JSON")
            }
            list
        }
    }

    private fun parseDeviceObject(obj: JSONObject): NetworkDeviceEntity? {
        val name = obj.optString("name", obj.optString("deviceName", "")).trim()
        val ip = obj.optString("ipAddress", obj.optString("ip", "")).trim()
        if (name.isBlank() && ip.isBlank()) return null

        return NetworkDeviceEntity(
            id = UuidUtils.newTimeOrderedId(),
            name = if (name.isNotBlank()) name else "جهاز شبكة ($ip)",
            deviceType = obj.optString("deviceType", obj.optString("type", "Access Point")),
            ipAddress = if (ip.isNotBlank()) ip else "192.168.88.10",
            macAddress = obj.optString("macAddress", obj.optString("mac", "")),
            locationArea = obj.optString("locationArea", obj.optString("location", "الموقع الرئيسي")),
            portOrInterface = obj.optString("portOrInterface", obj.optString("interface", "")),
            frequencyOrSsid = obj.optString("frequencyOrSsid", obj.optString("ssid", "")),
            model = obj.optString("model", ""),
            username = obj.optString("username", "admin"),
            status = obj.optString("status", "ONLINE"),
            signalDbm = obj.optInt("signalDbm", obj.optInt("signal", -60)),
            uptimeHours = obj.optInt("uptimeHours", 24),
            notes = obj.optString("notes", ""),
            createdAt = System.currentTimeMillis()
        )
    }

    // ==========================================
    // 3. PURCHASE INVOICES JSON BACKUP
    // ==========================================

    fun exportPurchasesToJson(
        docs: List<DocumentEntity>,
        items: List<DocumentItemEntity>,
        partyMap: Map<String, PartyEntity>,
        networkName: String = "SamMikrotik"
    ): String {
        val root = JSONObject()
        root.put("format", "SAM_PURCHASES_BACKUP")
        root.put("version", 1)
        root.put("networkName", networkName)
        root.put("exportedAt", dateFormat.format(Date()))
        root.put("invoiceCount", docs.size)

        val itemsByDoc = items.groupBy { it.docId }
        val array = JSONArray()

        docs.filter { it.type == "PURCHASE_INVOICE" }.forEach { doc ->
            val obj = JSONObject()
            obj.put("docNumber", doc.docNumber)
            obj.put("supplierName", partyMap[doc.partyId]?.name ?: "مورد عام")
            obj.put("currency", doc.currency)
            obj.put("totalMinor", doc.totalMinor)
            obj.put("totalAmount", doc.totalMinor / 100.0)
            obj.put("notes", doc.notes)

            val itemsArray = JSONArray()
            val docItems = itemsByDoc[doc.id] ?: emptyList()
            docItems.forEach { item ->
                val itmObj = JSONObject()
                itmObj.put("description", item.description)
                itmObj.put("quantity", item.quantity)
                itmObj.put("unitPriceMinor", item.unitPriceMinor)
                itmObj.put("unitPrice", item.unitPriceMinor / 100.0)
                itemsArray.put(itmObj)
            }
            obj.put("items", itemsArray)
            array.put(obj)
        }
        root.put("purchaseInvoices", array)
        return root.toString(2)
    }

    fun parsePurchasesFromJson(jsonString: String): Result<List<ParsedPurchaseInvoice>> {
        return runCatching {
            val trimmed = jsonString.trim()
            val list = mutableListOf<ParsedPurchaseInvoice>()

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parsePurchaseInvoiceObject(obj)?.let { list.add(it) }
                }
            } else {
                val root = JSONObject(trimmed)
                val array = when {
                    root.has("purchaseInvoices") -> root.getJSONArray("purchaseInvoices")
                    root.has("invoices") -> root.getJSONArray("invoices")
                    else -> null
                }
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        parsePurchaseInvoiceObject(obj)?.let { list.add(it) }
                    }
                } else if (root.has("supplierName") || root.has("invoiceNumber")) {
                    parsePurchaseInvoiceObject(root)?.let { list.add(it) }
                } else {
                    throw IllegalArgumentException("الملف لا يحتوي على فواتير مشتريات صالحة (purchaseInvoices)")
                }
            }

            if (list.isEmpty()) {
                throw IllegalArgumentException("لم يتم العثور على أي فواتير مشتريات صالحة داخل ملف JSON")
            }
            list
        }
    }

    private fun parsePurchaseInvoiceObject(obj: JSONObject): ParsedPurchaseInvoice? {
        val supplier = obj.optString("supplierName", obj.optString("supplier", "")).trim()
        val num = obj.optString("invoiceNumber", obj.optString("docNumber", "")).trim()
        if (supplier.isBlank() && num.isBlank()) return null

        val currency = obj.optString("currency", "USD").uppercase()
        val totalMinor = if (obj.has("totalMinor")) {
            obj.optLong("totalMinor", 0L)
        } else {
            (obj.optDouble("totalAmount", 0.0) * 100.0).toLong()
        }
        val notes = obj.optString("notes", "")

        val itemsList = mutableListOf<ParsedPurchaseItem>()
        if (obj.has("items")) {
            val arr = obj.getJSONArray("items")
            for (i in 0 until arr.length()) {
                val itm = arr.optJSONObject(i) ?: continue
                val desc = itm.optString("description", "بند مشتريات")
                val qty = itm.optInt("quantity", 1)
                val priceMinor = if (itm.has("unitPriceMinor")) {
                    itm.optLong("unitPriceMinor", 0L)
                } else {
                    (itm.optDouble("unitPrice", 0.0) * 100.0).toLong()
                }
                val isAsset = itm.optBoolean("isFixedAsset", desc.contains("راوتر") || desc.contains("أصل") || desc.contains("برج"))
                val months = itm.optInt("usefulLifeMonths", 24)
                itemsList.add(ParsedPurchaseItem(desc, qty, priceMinor, isAsset, months))
            }
        }

        if (itemsList.isEmpty()) {
            itemsList.add(ParsedPurchaseItem("مشتريات عامة", 1, totalMinor, false, 24))
        }

        return ParsedPurchaseInvoice(
            supplierName = if (supplier.isNotBlank()) supplier else "مورد عام",
            invoiceNumber = if (num.isNotBlank()) num else "PUR-${System.currentTimeMillis() % 100000}",
            currency = currency,
            totalMinor = totalMinor,
            notes = notes,
            items = itemsList
        )
    }

    // ==========================================
    // SAMPLE TEMPLATES
    // ==========================================

    val SAMPLE_CUSTOMERS_JSON = """
    {
      "format": "SAM_CUSTOMERS_BACKUP",
      "version": 1,
      "parties": [
        {
          "name": "بقالة البركة والخير",
          "phone": "771234567",
          "isCustomer": true,
          "isVendor": false,
          "creditLimitYemeniRial": 50000.0
        },
        {
          "name": "سوبرماركت النخبة - شارع حدة",
          "phone": "777654321",
          "isCustomer": true,
          "isVendor": false,
          "creditLimitYemeniRial": 100000.0
        },
        {
          "name": "شركة التقنية للشبكات (مورد)",
          "phone": "770112233",
          "isCustomer": false,
          "isVendor": true,
          "creditLimitYemeniRial": 0.0
        }
      ]
    }
    """.trimIndent()

    val SAMPLE_DEVICES_JSON = """
    {
      "format": "SAM_NETWORK_DEVICES_BACKUP",
      "version": 1,
      "devices": [
        {
          "name": "سيرفر الميكروتك الرئيسي CCR2004",
          "deviceType": "MikroTik RouterBOARD",
          "ipAddress": "192.168.88.1",
          "macAddress": "6C:3B:6B:11:22:33",
          "locationArea": "غرفة السيرفرات - التحرير",
          "model": "MikroTik CCR2004-16G-2S+",
          "status": "ONLINE",
          "signalDbm": -45,
          "notes": "الراوتر الرئيسي للتحكم بالسرعات والباقات"
        },
        {
          "name": "أكسس بوينت برج الرويشان",
          "deviceType": "Access Point",
          "ipAddress": "192.168.88.10",
          "macAddress": "D4:CA:6D:4A:2B:1C",
          "locationArea": "برج الرويشان - الطابق الخامس",
          "model": "Ubiquiti Rocket M5",
          "frequencyOrSsid": "5180 MHz / SAM-WIFI-5G",
          "status": "ONLINE",
          "signalDbm": -62,
          "notes": "مرسل رئيسي للقطاع الشرقي"
        },
        {
          "name": "سكتر بث شمالي السبعين",
          "deviceType": "Sector Antenna",
          "ipAddress": "192.168.88.25",
          "macAddress": "DC:9F:DB:88:99:AA",
          "locationArea": "برج السبعين",
          "model": "Ubiquiti AirMax Sector 120",
          "frequencyOrSsid": "5745 MHz / SECTOR-NORTH",
          "status": "ONLINE",
          "signalDbm": -68,
          "notes": "تغطية حي السبعين الشمالي"
        }
      ]
    }
    """.trimIndent()

    val SAMPLE_PURCHASES_JSON = """
    {
      "format": "SAM_PURCHASES_BACKUP",
      "version": 1,
      "purchaseInvoices": [
        {
          "supplierName": "شركة التقنية للشبكات",
          "invoiceNumber": "PUR-2026-0101",
          "currency": "USD",
          "totalAmount": 1200.0,
          "notes": "توريد معدات لبرج جديد",
          "items": [
            {
              "description": "راوتر ميكروتك CCR2004",
              "quantity": 1,
              "unitPrice": 650.0,
              "isFixedAsset": true,
              "usefulLifeMonths": 36
            },
            {
              "description": "سكتر بث Ubiquiti 120",
              "quantity": 2,
              "unitPrice": 275.0,
              "isFixedAsset": true,
              "usefulLifeMonths": 24
            }
          ]
        }
      ]
    }
    """.trimIndent()
}
