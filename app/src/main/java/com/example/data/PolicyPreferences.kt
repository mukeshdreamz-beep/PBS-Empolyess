package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.policyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pbs_attendance_policy"
)

enum class AppThemeMode(val storageValue: String) {
    DARK("DARK"),
    LIGHT("LIGHT"),
    SYSTEM("SYSTEM");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DARK
    }
}

enum class AuthRole(val storageValue: String) {
    NONE("NONE"),
    ADMIN("ADMIN"),
    EMPLOYEE("EMPLOYEE");

    companion object {
        fun fromStorage(value: String?): AuthRole =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

data class AuthSession(
    val role: AuthRole = AuthRole.NONE,
    val userId: Long = 0L,
    val displayName: String = "",
    val identifier: String = "",
    val workspaceId: String = DEFAULT_WORKSPACE_ID
) {
    companion object {
        const val DEFAULT_WORKSPACE_ID = "PBS-MAIN"
    }
}

data class AttendancePolicy(
    val defaultInMinutes: Int = DEFAULT_IN_MINUTES,
    val defaultOutMinutes: Int = DEFAULT_OUT_MINUTES,
    val fullDayThresholdMinutes: Int = DEFAULT_FULL_DAY_THRESHOLD_MINUTES,
    val overtimeStartMinutes: Int = DEFAULT_OVERTIME_START_MINUTES,
    val breakDeductionMinutes: Int = DEFAULT_BREAK_DEDUCTION_MINUTES,
    val themeMode: AppThemeMode = AppThemeMode.DARK
) {
    companion object {
        const val DEFAULT_IN_MINUTES = 9 * 60 + 30 // 9:30 AM
        const val DEFAULT_OUT_MINUTES = 18 * 60 + 15 // 6:15 PM
        const val DEFAULT_FULL_DAY_THRESHOLD_MINUTES = 8 * 60 + 45 // 525 mins
        const val DEFAULT_OVERTIME_START_MINUTES = 19 * 60 // 7:00 PM
        const val DEFAULT_BREAK_DEDUCTION_MINUTES = 0
    }
}

class PolicyPreferences(private val context: Context) {

    private object Keys {
        val DEFAULT_IN = intPreferencesKey("default_in_minutes")
        val DEFAULT_OUT = intPreferencesKey("default_out_minutes")
        val FULL_DAY_THRESHOLD = intPreferencesKey("full_day_threshold_minutes")
        val OT_START = intPreferencesKey("overtime_start_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_deduction_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTH_ROLE = stringPreferencesKey("auth_role")
        val AUTH_USER_ID = longPreferencesKey("auth_user_id")
        val AUTH_DISPLAY_NAME = stringPreferencesKey("auth_display_name")
        val AUTH_IDENTIFIER = stringPreferencesKey("auth_identifier")
        val AUTH_WORKSPACE_ID = stringPreferencesKey("auth_workspace_id")
    }

    val policyFlow: Flow<AttendancePolicy> = context.policyDataStore.data.map { prefs ->
        AttendancePolicy(
            defaultInMinutes = prefs[Keys.DEFAULT_IN] ?: AttendancePolicy.DEFAULT_IN_MINUTES,
            defaultOutMinutes = prefs[Keys.DEFAULT_OUT] ?: AttendancePolicy.DEFAULT_OUT_MINUTES,
            fullDayThresholdMinutes = prefs[Keys.FULL_DAY_THRESHOLD]
                ?: AttendancePolicy.DEFAULT_FULL_DAY_THRESHOLD_MINUTES,
            overtimeStartMinutes = prefs[Keys.OT_START]
                ?: AttendancePolicy.DEFAULT_OVERTIME_START_MINUTES,
            breakDeductionMinutes = prefs[Keys.BREAK_MINUTES]
                ?: AttendancePolicy.DEFAULT_BREAK_DEDUCTION_MINUTES,
            themeMode = AppThemeMode.fromStorage(prefs[Keys.THEME_MODE])
        )
    }

    val authSessionFlow: Flow<AuthSession> = context.policyDataStore.data.map { prefs ->
        AuthSession(
            role = AuthRole.fromStorage(prefs[Keys.AUTH_ROLE]),
            userId = prefs[Keys.AUTH_USER_ID] ?: 0L,
            displayName = prefs[Keys.AUTH_DISPLAY_NAME] ?: "",
            identifier = prefs[Keys.AUTH_IDENTIFIER] ?: "",
            workspaceId = (prefs[Keys.AUTH_WORKSPACE_ID] ?: AuthSession.DEFAULT_WORKSPACE_ID)
                .ifBlank { AuthSession.DEFAULT_WORKSPACE_ID }
        )
    }

    suspend fun saveAuthSession(session: AuthSession) {
        val cleanWorkspace = sanitizeWorkspaceId(session.workspaceId)
        context.policyDataStore.edit { prefs ->
            prefs[Keys.AUTH_ROLE] = session.role.storageValue
            prefs[Keys.AUTH_USER_ID] = session.userId
            prefs[Keys.AUTH_DISPLAY_NAME] = session.displayName
            prefs[Keys.AUTH_IDENTIFIER] = session.identifier
            prefs[Keys.AUTH_WORKSPACE_ID] = cleanWorkspace
        }
    }

    suspend fun clearAuthSession() {
        context.policyDataStore.edit { prefs ->
            val currentWorkspace = prefs[Keys.AUTH_WORKSPACE_ID] ?: AuthSession.DEFAULT_WORKSPACE_ID
            prefs[Keys.AUTH_ROLE] = AuthRole.NONE.storageValue
            prefs[Keys.AUTH_USER_ID] = 0L
            prefs[Keys.AUTH_DISPLAY_NAME] = ""
            prefs[Keys.AUTH_IDENTIFIER] = ""
            prefs[Keys.AUTH_WORKSPACE_ID] = currentWorkspace
        }
    }

    companion object {
        fun sanitizeWorkspaceId(raw: String): String {
            val cleaned = raw.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-")
            return cleaned.ifBlank { AuthSession.DEFAULT_WORKSPACE_ID }.take(64)
        }
    }

    suspend fun updatePolicy(policy: AttendancePolicy) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_IN] = policy.defaultInMinutes
            prefs[Keys.DEFAULT_OUT] = policy.defaultOutMinutes
            prefs[Keys.FULL_DAY_THRESHOLD] = policy.fullDayThresholdMinutes.coerceIn(60, 16 * 60)
            prefs[Keys.OT_START] = policy.overtimeStartMinutes
            prefs[Keys.BREAK_MINUTES] = policy.breakDeductionMinutes.coerceIn(0, 180)
            prefs[Keys.THEME_MODE] = policy.themeMode.storageValue
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.storageValue
        }
    }

    suspend fun resetDefaults() {
        updatePolicy(AttendancePolicy())
    }
}
