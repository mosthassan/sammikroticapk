package com.example.domain.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLockManager {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var pinHash: String? = null
    var isBiometricsEnabled: Boolean = false
    var isPinLockEnabled: Boolean = false

    fun setupPin(pin: String) {
        if (pin.length in 4..6 && pin.all { it.isDigit() }) {
            pinHash = hashPin(pin)
            isPinLockEnabled = true
        }
    }

    fun disablePin() {
        pinHash = null
        isPinLockEnabled = false
        _isLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinLockEnabled) return true
        val matched = pinHash == hashPin(pin)
        if (matched) {
            _isLocked.value = false
        }
        return matched
    }

    fun lockApp() {
        if (isPinLockEnabled) {
            _isLocked.value = true
        }
    }

    fun unlockWithBiometrics() {
        if (isBiometricsEnabled) {
            _isLocked.value = false
        }
    }

    private fun hashPin(pin: String): String {
        return pin.reversed().hashCode().toString()
    }
}
