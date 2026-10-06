package com.example.domain.network

import com.example.data.local.entity.NetworkDeviceEntity
import com.example.data.local.entity.NetworkSubnetSettingsEntity

data class IpValidationResult(
    val isValid: Boolean = true,
    val hasConflict: Boolean = false,
    val conflictingDeviceName: String? = null,
    val conflictingDeviceLocation: String? = null,
    val isOutsideSubnet: Boolean = false,
    val isGatewayConflict: Boolean = false,
    val isHotspotConflict: Boolean = false,
    val approvedSubnet: String = "192.168.88.0/24",
    val message: String? = null
)

object NetworkProtectionValidator {

    fun validateDeviceIp(
        targetIp: String,
        currentDeviceId: String?,
        existingDevices: List<NetworkDeviceEntity>,
        settings: NetworkSubnetSettingsEntity?
    ): IpValidationResult {
        val trimmedIp = targetIp.trim()
        if (trimmedIp.isBlank()) {
            return IpValidationResult(isValid = false, message = "عنوان الآي بي مطلوب")
        }

        // 1. Check IPv4 format
        val parts = trimmedIp.split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null || it.toInt() !in 0..255 }) {
            return IpValidationResult(isValid = false, message = "صيغة الآي بي غير صالحة (مثال: 192.168.88.10)")
        }

        val approvedSubnet = settings?.approvedDeviceSubnet ?: "192.168.88.0/24"
        val gatewayIp = settings?.gatewayIp ?: "192.168.88.1"
        val hotspotSubnet = settings?.hotspotSubnet ?: "10.5.50.0/24"
        val hotspotGateway = settings?.hotspotGatewayIp ?: "10.5.50.1"

        // 2. Check Gateway Collision
        if (trimmedIp == gatewayIp.trim()) {
            return IpValidationResult(
                isValid = false,
                isGatewayConflict = true,
                approvedSubnet = approvedSubnet,
                message = "تحذير: هذا العنوان محجوز للبوابة الرئيسية للراوتر (Gateway IP: $gatewayIp)"
            )
        }

        if (trimmedIp == hotspotGateway.trim()) {
            return IpValidationResult(
                isValid = false,
                isGatewayConflict = true,
                approvedSubnet = approvedSubnet,
                message = "تحذير: هذا العنوان محجوز لبوابة كروت الهوتسبوت ($hotspotGateway)"
            )
        }

        // 3. Check Collision with Other Network Devices
        val collision = existingDevices.firstOrNull { dev ->
            dev.id != currentDeviceId && dev.ipAddress.trim().equals(trimmedIp, ignoreCase = true)
        }

        if (collision != null) {
            return IpValidationResult(
                isValid = false,
                hasConflict = true,
                conflictingDeviceName = collision.name,
                conflictingDeviceLocation = collision.locationArea,
                approvedSubnet = approvedSubnet,
                message = "تضارب آي بي حرج! العنوان محجوز للجهاز (${collision.name}) في (${collision.locationArea}). يرجى تغييره لمنع توقف الشبكة."
            )
        }

        // 4. Check Hotspot Range Collision
        if (isIpInSubnet(trimmedIp, hotspotSubnet)) {
            return IpValidationResult(
                isValid = false,
                isHotspotConflict = true,
                approvedSubnet = approvedSubnet,
                message = "تنبيه أمني: هذا الآي بي يقع ضمن رنج كروت ومشتركي الهوتسبوت ($hotspotSubnet). يفضل عزل أجهزة الإدارة في رنج منفصل."
            )
        }

        // 5. Check if Outside Approved Subnet
        val outsideSubnet = !isIpInSubnet(trimmedIp, approvedSubnet)
        if (outsideSubnet) {
            return IpValidationResult(
                isValid = true,
                isOutsideSubnet = true,
                approvedSubnet = approvedSubnet,
                message = "تنبيه: الآي بي يقع خارج الرنج المعتمد للأجهزة ($approvedSubnet)"
            )
        }

        return IpValidationResult(
            isValid = true,
            hasConflict = false,
            approvedSubnet = approvedSubnet
        )
    }

    private fun isIpInSubnet(ip: String, subnet: String): Boolean {
        return try {
            val subnetParts = subnet.split("/")
            if (subnetParts.size != 2) return false
            val networkIp = subnetParts[0].trim()
            val prefixLen = subnetParts[1].trim().toIntOrNull() ?: 24

            val ipLong = ipToLong(ip)
            val networkLong = ipToLong(networkIp)
            val mask = (-1L shl (32 - prefixLen)) and 0xFFFFFFFFL

            (ipLong and mask) == (networkLong and mask)
        } catch (_: Exception) {
            false
        }
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".").map { it.toLong() }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }
}
