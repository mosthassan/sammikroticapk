package com.example.util

import android.content.Context
import com.example.ui.viewmodel.SalesItemDraftState
import org.json.JSONArray
import org.json.JSONObject

data class SalesDraftData(
    val isOpen: Boolean = false,
    val partyId: String? = null,
    val customerSearch: String = "",
    val items: List<SalesItemDraftState> = emptyList(),
    val notes: String = "",
    val paymentType: String = "CREDIT",
    val paidMinor: Long = 0L
)

object SalesDraftManager {
    private const val PREFS_NAME = "sammikrotik_draft_prefs"
    private const val KEY_SALES_DRAFT = "sales_invoice_draft_json"

    fun saveDraft(
        context: Context,
        isOpen: Boolean,
        partyId: String?,
        customerSearch: String,
        items: List<SalesItemDraftState>,
        notes: String,
        paymentType: String,
        paidMinor: Long
    ) {
        try {
            val json = JSONObject()
            json.put("isOpen", isOpen)
            json.put("partyId", partyId ?: "")
            json.put("customerSearch", customerSearch)
            json.put("notes", notes)
            json.put("paymentType", paymentType)
            json.put("paidMinor", paidMinor)

            val itemsArray = JSONArray()
            items.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("id", item.id)
                itemObj.put("packageId", item.packageId ?: "")
                itemObj.put("packageName", item.packageName)
                itemObj.put("quantity", item.quantity)
                itemObj.put("unitPriceMinor", item.unitPriceMinor)
                itemObj.put("retailPriceMinor", item.retailPriceMinor)
                itemObj.put("isService", item.isService)
                itemsArray.put(itemObj)
            }
            json.put("items", itemsArray)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SALES_DRAFT, json.toString()).apply()
        } catch (e: Exception) {
            // Non-fatal logging
        }
    }

    fun loadDraft(context: Context): SalesDraftData? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_SALES_DRAFT, null) ?: return null
            val json = JSONObject(jsonString)

            val isOpen = json.optBoolean("isOpen", false)
            val partyIdRaw = json.optString("partyId", "")
            val partyId = if (partyIdRaw.isBlank()) null else partyIdRaw
            val customerSearch = json.optString("customerSearch", "")
            val notes = json.optString("notes", "")
            val paymentType = json.optString("paymentType", "CREDIT")
            val paidMinor = json.optLong("paidMinor", 0L)

            val itemsList = mutableListOf<SalesItemDraftState>()
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itm = itemsArray.getJSONObject(i)
                    val pkgIdRaw = itm.optString("packageId", "")
                    itemsList.add(
                        SalesItemDraftState(
                            id = itm.optString("id", ""),
                            packageId = if (pkgIdRaw.isBlank()) null else pkgIdRaw,
                            packageName = itm.optString("packageName", ""),
                            quantity = itm.optInt("quantity", 10),
                            unitPriceMinor = itm.optLong("unitPriceMinor", 0L),
                            retailPriceMinor = itm.optLong("retailPriceMinor", 0L),
                            isService = itm.optBoolean("isService", false)
                        )
                    )
                }
            }

            SalesDraftData(
                isOpen = isOpen,
                partyId = partyId,
                customerSearch = customerSearch,
                items = itemsList,
                notes = notes,
                paymentType = paymentType,
                paidMinor = paidMinor
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearDraft(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SALES_DRAFT).apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
