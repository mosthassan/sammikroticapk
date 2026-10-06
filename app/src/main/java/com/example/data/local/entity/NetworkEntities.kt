package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "network_devices",
    indices = [
        Index(value = ["ipAddress"], unique = false),
        Index(value = ["deviceType"]),
        Index(value = ["status"])
    ]
)
data class NetworkDeviceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val deviceType: String, // "MikroTik RouterBOARD", "Access Point", "Sector Antenna", "Switch", "CPE", "OLT"
    val ipAddress: String,
    val macAddress: String = "",
    val locationArea: String,
    val portOrInterface: String = "",
    val frequencyOrSsid: String = "",
    val model: String = "",
    val username: String = "admin",
    val status: String = "ONLINE", // "ONLINE", "WARNING", "OFFLINE"
    val signalDbm: Int = -60,
    val uptimeHours: Int = 24,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_subnet_settings")
data class NetworkSubnetSettingsEntity(
    @PrimaryKey
    val id: String = "GLOBAL_SUBNET_SETTINGS",
    val approvedDeviceSubnet: String = "192.168.88.0/24",
    val gatewayIp: String = "192.168.88.1",
    val ipRangeStart: String = "192.168.88.2",
    val ipRangeEnd: String = "192.168.88.254",
    val hotspotSubnet: String = "10.5.50.0/24",
    val hotspotGatewayIp: String = "10.5.50.1",
    val dnsServers: String = "8.8.8.8, 1.1.1.1",
    val isProtectionEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
