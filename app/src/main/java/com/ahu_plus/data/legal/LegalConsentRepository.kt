package com.ahu_plus.data.legal

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahu_plus.data.local.AppDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class LegalAcceptance(
    val privacyVersion: Int,
    val disclaimerVersion: Int,
    val acceptedAtEpochMillis: Long,
    val acceptedAppVersion: String,
)

sealed interface LegalGateState {
    data object Loading : LegalGateState
    data object RequiresConsent : LegalGateState
    data class Accepted(val acceptance: LegalAcceptance) : LegalGateState
}

object LegalDocumentVersions {
    const val PRIVACY = 1
    const val DISCLAIMER = 1
    const val EFFECTIVE_DATE = "2026-07-19"
}

enum class LegalRiskAcknowledgement(val storageId: String, val version: Int) {
    MARKET_AI("market_ai", 1),
    WELEARN_AUTOMATION("welearn_automation", 1),
}

/** Stores only the local click-through record needed before SessionManager is initialized. */
class LegalConsentRepository(
    private val appDataStore: AppDataStore,
) {
    val gateState: Flow<LegalGateState> = appDataStore.dataStore.data
        .map { preferences ->
            val acceptance = LegalAcceptance(
                privacyVersion = preferences[PRIVACY_VERSION_KEY] ?: 0,
                disclaimerVersion = preferences[DISCLAIMER_VERSION_KEY] ?: 0,
                acceptedAtEpochMillis = preferences[ACCEPTED_AT_KEY] ?: 0L,
                acceptedAppVersion = preferences[ACCEPTED_APP_VERSION_KEY].orEmpty(),
            )
            if (isCurrent(acceptance)) {
                LegalGateState.Accepted(acceptance)
            } else {
                LegalGateState.RequiresConsent
            }
        }
        .catch { emit(LegalGateState.RequiresConsent) }

    suspend fun acceptCurrent(
        appVersion: String,
        acceptedAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        appDataStore.dataStore.edit { preferences ->
            preferences[PRIVACY_VERSION_KEY] = LegalDocumentVersions.PRIVACY
            preferences[DISCLAIMER_VERSION_KEY] = LegalDocumentVersions.DISCLAIMER
            preferences[ACCEPTED_AT_KEY] = acceptedAtEpochMillis
            preferences[ACCEPTED_APP_VERSION_KEY] = appVersion
        }
    }

    suspend fun withdraw() {
        appDataStore.dataStore.edit { preferences ->
            preferences.remove(PRIVACY_VERSION_KEY)
            preferences.remove(DISCLAIMER_VERSION_KEY)
            preferences.remove(ACCEPTED_AT_KEY)
            preferences.remove(ACCEPTED_APP_VERSION_KEY)
        }
    }

    suspend fun hasAcceptedCurrent(): Boolean = when (gateState.first()) {
        is LegalGateState.Accepted -> true
        LegalGateState.Loading,
        LegalGateState.RequiresConsent -> false
    }

    suspend fun hasAcknowledged(risk: LegalRiskAcknowledgement): Boolean {
        val preferences = appDataStore.dataStore.data.first()
        return (preferences[riskVersionKey(risk)] ?: 0) >= risk.version
    }

    suspend fun acknowledge(risk: LegalRiskAcknowledgement) {
        appDataStore.dataStore.edit { preferences ->
            preferences[riskVersionKey(risk)] = risk.version
        }
    }

    companion object {
        private val PRIVACY_VERSION_KEY = intPreferencesKey("legal_privacy_version")
        private val DISCLAIMER_VERSION_KEY = intPreferencesKey("legal_disclaimer_version")
        private val ACCEPTED_AT_KEY = longPreferencesKey("legal_accepted_at")
        private val ACCEPTED_APP_VERSION_KEY = stringPreferencesKey("legal_accepted_app_version")

        private fun riskVersionKey(risk: LegalRiskAcknowledgement) =
            intPreferencesKey("legal_risk_${risk.storageId}_version")
        internal fun isCurrent(acceptance: LegalAcceptance): Boolean =
            acceptance.privacyVersion >= LegalDocumentVersions.PRIVACY &&
                acceptance.disclaimerVersion >= LegalDocumentVersions.DISCLAIMER &&
                acceptance.acceptedAtEpochMillis > 0L
    }
}
