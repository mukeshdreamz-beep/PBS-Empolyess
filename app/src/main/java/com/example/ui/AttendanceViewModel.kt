package com.example.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.ApprovalRequestEntity
import com.example.data.AttendancePolicy
import com.example.data.AttendanceRecordItem
import com.example.data.AttendanceRepository
import com.example.data.AuthErrorReason
import com.example.data.AuthResult
import com.example.data.AuthRole
import com.example.data.AuthSession
import com.example.data.EmployeeEntity
import com.example.data.ExpenseEntity
import com.example.data.FirestoreSyncRepository
import com.example.data.PolicyPreferences
import com.example.data.SelfPunchRejectReason
import com.example.data.SelfPunchResult
import com.example.data.WorkspaceRemoteConfig
import com.example.domain.AttendanceBreakdown
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceStatus
import com.example.domain.AuthUtils
import com.example.domain.EmployeeMonthlySummary
import com.example.domain.YearMonthKey
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppPersona {
    ADMIN,
    EMPLOYEE
}

enum class SyncStatus {
    LOCAL_ONLY,
    SYNCING,
    ONLINE,
    ERROR
}

enum class AdminSection(@param:StringRes val titleRes: Int) {
    DASHBOARD(R.string.section_dashboard),
    ATTENDANCE(R.string.section_attendance),
    EMPLOYEES(R.string.section_employees),
    EXPENSES(R.string.section_expenses),
    SALARY(R.string.section_salary),
    APPROVALS(R.string.section_approvals),
    REPORTS(R.string.section_reports),
    SETTINGS(R.string.section_settings)
}

enum class EntryMode {
    SINGLE,
    ROLL_CALL
}

data class SiteManpowerStat(
    val siteName: String,
    val presentToday: Int,
    val totalEmployees: Int
)

data class FeedbackMessage(
    @param:StringRes val messageRes: Int,
    val isError: Boolean = false,
    val showUndo: Boolean = false
)

data class GeneratedCredentialsDialogState(
    val employeeName: String,
    val site: String,
    val loginId: String,
    val plainPassword: String,
    val workspaceId: String = AuthSession.DEFAULT_WORKSPACE_ID,
    val isReset: Boolean = false
)

private data class NavAndFormState(
    val persona: AppPersona = AppPersona.ADMIN,
    val adminSection: AdminSection = AdminSection.DASHBOARD,
    val entryMode: EntryMode = EntryMode.SINGLE,
    val dashboardSiteFilter: String? = null,
    val selectedEmployeeId: Long? = null,
    val activeEmployeeAppId: Long? = null,
    val dateText: String = AttendanceCalculator.todayDateString(),
    val inMinutes: Int = AttendancePolicy.DEFAULT_IN_MINUTES,
    val outMinutes: Int = AttendancePolicy.DEFAULT_OUT_MINUTES,
    val entryStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val entryNote: String = "",
    val filterEmployeeId: Long? = null,
    val filterMonth: YearMonthKey? = null,
    val summaryMonth: YearMonthKey = AttendanceCalculator.currentYearMonth(),
    val searchQuery: String = "",
    val approvalFilter: String = "PENDING", // ALL, PENDING, RESOLVED
    val authErrorRes: Int? = null,
    val isAuthBusy: Boolean = false,
    val generatedCredentials: GeneratedCredentialsDialogState? = null,
    val feedback: FeedbackMessage? = null
)

data class AttendanceUiState(
    val authSession: AuthSession = AuthSession(),
    val authErrorRes: Int? = null,
    val isAuthBusy: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val workspaceConfig: WorkspaceRemoteConfig? = null,
    val googleUserEmail: String = "",
    val generatedCredentials: GeneratedCredentialsDialogState? = null,
    val persona: AppPersona = AppPersona.ADMIN,
    val adminSection: AdminSection = AdminSection.DASHBOARD,
    val entryMode: EntryMode = EntryMode.SINGLE,
    val policy: AttendancePolicy = AttendancePolicy(),
    val employees: List<EmployeeEntity> = emptyList(),
    val allRecords: List<AttendanceRecordItem> = emptyList(),
    val filteredRecords: List<AttendanceRecordItem> = emptyList(),
    val recordsForEntryDate: Map<Long, AttendanceRecordItem> = emptyMap(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val approvals: List<ApprovalRequestEntity> = emptyList(),
    val monthlySummaries: List<EmployeeMonthlySummary> = emptyList(),
    val siteStats: List<SiteManpowerStat> = emptyList(),
    val availableSites: List<String> = EmployeeEntity.PRESET_SITES,
    val dashboardSiteFilter: String? = null,
    val dashboardTotalEmployees: Int = 0,
    val dashboardPresentToday: Int = 0,
    val dashboardAbsentToday: Int = 0,
    val dashboardPendingCount: Int = 0,
    val selectedEmployeeId: Long? = null,
    val activeEmployeeAppId: Long? = null,
    val dateText: String = AttendanceCalculator.todayDateString(),
    val inMinutes: Int = AttendancePolicy.DEFAULT_IN_MINUTES,
    val outMinutes: Int = AttendancePolicy.DEFAULT_OUT_MINUTES,
    val entryStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val entryNote: String = "",
    val filterEmployeeId: Long? = null,
    val filterMonth: YearMonthKey? = null,
    val summaryMonth: YearMonthKey = AttendanceCalculator.currentYearMonth(),
    val searchQuery: String = "",
    val approvalFilter: String = "PENDING",
    val breakdown: AttendanceBreakdown = AttendanceCalculator.calculateBreakdown(
        AttendancePolicy.DEFAULT_IN_MINUTES,
        AttendancePolicy.DEFAULT_OUT_MINUTES
    ),
    val willReplaceExisting: Boolean = false,
    val totalWorkedMinutes: Int = 0,
    val totalOvertimeMinutes: Int = 0,
    val fullDayCount: Int = 0,
    val absentOrLeaveCount: Int = 0,
    val feedback: FeedbackMessage? = null
) {
    val selectedEmployee: EmployeeEntity?
        get() = employees.firstOrNull { it.id == selectedEmployeeId }

    val activeEmployeeAppProfile: EmployeeEntity?
        get() = employees.firstOrNull { it.id == activeEmployeeAppId }
}

private data class CombinedSources(
    val employees: List<EmployeeEntity>,
    val records: List<AttendanceRecordItem>,
    val expenses: List<ExpenseEntity>,
    val approvals: List<ApprovalRequestEntity>,
    val policy: AttendancePolicy,
    val authSession: AuthSession,
    val syncStatus: SyncStatus,
    val workspaceConfig: WorkspaceRemoteConfig?
)

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val policyPreferences: PolicyPreferences? = null,
    private val syncRepository: FirestoreSyncRepository? = null
) : ViewModel() {

    private val formState = MutableStateFlow(NavAndFormState())
    private var lastDeletedRecord: AttendanceRecordItem? = null

    private val policyFlow = policyPreferences?.policyFlow
        ?: MutableStateFlow(AttendancePolicy())

    private val localAuthSession = MutableStateFlow(AuthSession())
    private val authSessionFlow = policyPreferences?.authSessionFlow ?: localAuthSession

    private val syncStatusFlow = MutableStateFlow(
        if (syncRepository != null) SyncStatus.SYNCING else SyncStatus.LOCAL_ONLY
    )
    private val workspaceConfigFlow = MutableStateFlow<WorkspaceRemoteConfig?>(null)

    private var syncJob: Job? = null
    private var activeSyncKey: String? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaultAdminSeeded()
        }
        viewModelScope.launch {
            authSessionFlow
                .distinctUntilChanged()
                .collect { session ->
                    val sync = syncRepository
                    if (sync == null || sync.currentUid() == null || session.role == AuthRole.NONE) {
                        clearSyncListeners()
                        syncStatusFlow.value = if (sync == null) SyncStatus.LOCAL_ONLY else SyncStatus.SYNCING
                    } else {
                        val isAdmin = session.role == AuthRole.ADMIN
                        val wsId = PolicyPreferences.sanitizeWorkspaceId(session.workspaceId)
                        val key = "${sync.currentUid()}|$wsId|${session.role.name}"
                        if (activeSyncKey != key) {
                            startSyncListeners(wsId, isAdmin, key)
                        }
                    }
                }
        }
    }

    private fun clearSyncListeners() {
        syncJob?.cancel()
        syncJob = null
        activeSyncKey = null
    }

    private fun startSyncListeners(workspaceId: String, isAdmin: Boolean, key: String) {
        val sync = syncRepository ?: return
        clearSyncListeners()
        activeSyncKey = key
        syncStatusFlow.value = SyncStatus.SYNCING

        syncJob = viewModelScope.launch {
            if (isAdmin) {
                val cfgRes = sync.ensureAdminWorkspaceMembership(
                    workspaceId = workspaceId,
                    displayName = uiState.value.authSession.displayName,
                    policy = uiState.value.policy
                )
                cfgRes.onSuccess { cfg ->
                    workspaceConfigFlow.value = cfg
                    syncStatusFlow.value = SyncStatus.ONLINE
                }.onFailure {
                    syncStatusFlow.value = SyncStatus.ERROR
                }
            }

            launch {
                sync.observeWorkspaceConfig(workspaceId).collect { cfg ->
                    if (cfg != null) {
                        workspaceConfigFlow.value = cfg
                        syncStatusFlow.value = SyncStatus.ONLINE
                        val prefs = policyPreferences
                        val currentPolicy = uiState.value.policy
                        val remotePolicy = cfg.policy.copy(themeMode = currentPolicy.themeMode)
                        if (prefs != null && (
                                remotePolicy.defaultInMinutes != currentPolicy.defaultInMinutes ||
                                    remotePolicy.defaultOutMinutes != currentPolicy.defaultOutMinutes ||
                                    remotePolicy.fullDayThresholdMinutes != currentPolicy.fullDayThresholdMinutes ||
                                    remotePolicy.overtimeStartMinutes != currentPolicy.overtimeStartMinutes ||
                                    remotePolicy.breakDeductionMinutes != currentPolicy.breakDeductionMinutes
                                )
                        ) {
                            prefs.updatePolicy(remotePolicy)
                        }
                    }
                }
            }
            launch {
                sync.observeEmployees(workspaceId, isAdmin).collect { list ->
                    repository.reconcileRemoteEmployees(list)
                    syncStatusFlow.value = SyncStatus.ONLINE
                }
            }
            launch {
                sync.observeAttendance(workspaceId, isAdmin).collect { list ->
                    repository.reconcileRemoteAttendance(list)
                    syncStatusFlow.value = SyncStatus.ONLINE
                }
            }
            launch {
                sync.observeExpenses(workspaceId, isAdmin).collect { list ->
                    repository.reconcileRemoteExpenses(list)
                    syncStatusFlow.value = SyncStatus.ONLINE
                }
            }
            launch {
                sync.observeApprovals(workspaceId, isAdmin).collect { list ->
                    repository.reconcileRemoteApprovals(list)
                    syncStatusFlow.value = SyncStatus.ONLINE
                }
            }
            if (isAdmin) {
                launch {
                    sync.observeAdminAccounts(workspaceId).collect { admins ->
                        repository.upsertRemoteAdminAccounts(admins)
                    }
                }
            }
        }
    }

    private fun currentWorkspaceId(): String =
        PolicyPreferences.sanitizeWorkspaceId(uiState.value.authSession.workspaceId)

    private fun currentAdminUids(): List<String> {
        val cfgUids = workspaceConfigFlow.value?.adminUids.orEmpty()
        val me = syncRepository?.currentUid()
        return if (me != null) (cfgUids + me).distinct() else cfgUids
    }

    private val coreSources = combine(
        repository.employees,
        repository.records,
        repository.expenses,
        repository.approvals
    ) { employees, records, expenses, approvals ->
        QuadData(employees, records, expenses, approvals)
    }

    private val configSources = combine(
        policyFlow,
        authSessionFlow,
        syncStatusFlow,
        workspaceConfigFlow
    ) { policy, auth, syncStatus, wsConfig ->
        QuadConfig(policy, auth, syncStatus, wsConfig)
    }

    private val dataSources = combine(coreSources, configSources) { core, cfg ->
        CombinedSources(
            employees = core.employees,
            records = core.records,
            expenses = core.expenses,
            approvals = core.approvals,
            policy = cfg.policy,
            authSession = cfg.auth,
            syncStatus = cfg.syncStatus,
            workspaceConfig = cfg.wsConfig
        )
    }

    val uiState: StateFlow<AttendanceUiState> = combine(
        dataSources,
        formState
    ) { src, form ->
        val employees = src.employees
        val records = src.records
        val expenses = src.expenses
        val approvals = src.approvals
        val policy = src.policy
        val rawSession = src.authSession
        val effectiveSession = if (rawSession.role == AuthRole.EMPLOYEE &&
            employees.none { it.id == rawSession.userId }
        ) {
            AuthSession(workspaceId = rawSession.workspaceId)
        } else {
            rawSession
        }

        val resolvedSelectedId = when {
            employees.isEmpty() -> null
            form.selectedEmployeeId != null && employees.any { it.id == form.selectedEmployeeId } ->
                form.selectedEmployeeId
            else -> employees.first().id
        }

        val resolvedEmployeeAppId = when {
            effectiveSession.role == AuthRole.EMPLOYEE -> effectiveSession.userId
            employees.isEmpty() -> null
            form.activeEmployeeAppId != null && employees.any { it.id == form.activeEmployeeAppId } ->
                form.activeEmployeeAppId
            else -> employees.first().id
        }

        val effectivePersona = when (effectiveSession.role) {
            AuthRole.EMPLOYEE -> AppPersona.EMPLOYEE
            AuthRole.ADMIN -> AppPersona.ADMIN
            AuthRole.NONE -> form.persona
        }

        val resolvedFilterId = if (form.filterEmployeeId != null &&
            employees.any { it.id == form.filterEmployeeId }
        ) {
            form.filterEmployeeId
        } else {
            null
        }

        val todayStr = AttendanceCalculator.todayDateString()
        val todayRecordsByEmp = records.filter { it.date == todayStr }.associateBy { it.employeeId }

        val siteEmployees = if (form.dashboardSiteFilter == null) {
            employees
        } else {
            employees.filter { it.site.equals(form.dashboardSiteFilter, ignoreCase = true) }
        }
        val dashTotal = siteEmployees.size
        var dashPresent = 0
        var dashAbsent = 0
        for (emp in siteEmployees) {
            val rec = todayRecordsByEmp[emp.id]
            if (rec != null) {
                when (AttendanceStatus.fromStorage(rec.status)) {
                    AttendanceStatus.PRESENT -> dashPresent++
                    AttendanceStatus.ABSENT, AttendanceStatus.LEAVE -> dashAbsent++
                }
            }
        }
        val pendingCount = approvals.count { it.status == "PENDING" } +
            expenses.count { it.status == "PENDING" }

        val distinctSites = (EmployeeEntity.PRESET_SITES + employees.map { it.site })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val siteStats = employees
            .groupBy { it.site.ifBlank { EmployeeEntity.DEFAULT_SITE } }
            .map { (siteName, list) ->
                val presentAtSite = list.count { emp ->
                    val rec = todayRecordsByEmp[emp.id]
                    rec != null && AttendanceStatus.fromStorage(rec.status) == AttendanceStatus.PRESENT
                }
                SiteManpowerStat(
                    siteName = siteName,
                    presentToday = presentAtSite,
                    totalEmployees = list.size
                )
            }
            .sortedByDescending { it.totalEmployees }

        val query = form.searchQuery.trim().lowercase()
        val filtered = records.filter { item ->
            val matchesEmployee = resolvedFilterId == null || item.employeeId == resolvedFilterId
            val matchesMonth = form.filterMonth == null ||
                AttendanceCalculator.extractYearMonth(item.date) == form.filterMonth
            val matchesQuery = query.isEmpty() ||
                item.employeeName.lowercase().contains(query) ||
                item.employeeCode.lowercase().contains(query) ||
                item.employeeSite.lowercase().contains(query) ||
                item.date.contains(query) ||
                item.note.lowercase().contains(query) ||
                item.employeeRole.lowercase().contains(query)
            matchesEmployee && matchesMonth && matchesQuery
        }

        var workedSum = 0
        var otSum = 0
        var fullDays = 0
        var absentLeave = 0
        for (item in filtered) {
            val status = AttendanceStatus.fromStorage(item.status)
            if (status != AttendanceStatus.PRESENT) {
                absentLeave++
            } else {
                val b = AttendanceCalculator.calculateBreakdown(
                    inMinutes = item.inMinutes,
                    outMinutes = item.outMinutes,
                    status = status,
                    policy = policy
                )
                workedSum += b.workedMinutes
                otSum += b.overtimeMinutes
                if (b.isFullDay) fullDays++
            }
        }

        val normalizedDate = form.dateText.trim()
        val recordsForDate = records
            .filter { it.date == normalizedDate }
            .associateBy { it.employeeId }

        val replaces = resolvedSelectedId != null && recordsForDate.containsKey(resolvedSelectedId)

        val monthRecords = records.filter {
            AttendanceCalculator.extractYearMonth(it.date) == form.summaryMonth
        }
        val monthApprovedExpenses = expenses.filter {
            it.status == "APPROVED" &&
                AttendanceCalculator.extractYearMonth(it.date) == form.summaryMonth
        }

        val summaries = employees.map { emp ->
            val empMonthRecords = monthRecords.filter { it.employeeId == emp.id }
            var eFull = 0
            var eShort = 0
            var eAbsent = 0
            var eLeave = 0
            var eWorked = 0
            var eOt = 0
            for (rec in empMonthRecords) {
                when (val st = AttendanceStatus.fromStorage(rec.status)) {
                    AttendanceStatus.ABSENT -> eAbsent++
                    AttendanceStatus.LEAVE -> eLeave++
                    AttendanceStatus.PRESENT -> {
                        val b = AttendanceCalculator.calculateBreakdown(
                            inMinutes = rec.inMinutes,
                            outMinutes = rec.outMinutes,
                            status = st,
                            policy = policy
                        )
                        eWorked += b.workedMinutes
                        eOt += b.overtimeMinutes
                        if (b.isFullDay) eFull++ else eShort++
                    }
                }
            }
            val approvedExp = monthApprovedExpenses
                .filter { it.employeeId == emp.id }
                .sumOf { it.amount }
            val basePay = (eFull * emp.dailyWage) + (eShort * emp.dailyWage * 0.5)
            val otPay = (eOt / 60.0) * emp.otHourlyRate
            val netPay = basePay + otPay + approvedExp

            EmployeeMonthlySummary(
                employeeId = emp.id,
                employeeName = emp.name,
                employeeCode = emp.employeeCode,
                employeeSite = emp.site,
                employeeRole = emp.role,
                dailyWage = emp.dailyWage,
                otHourlyRate = emp.otHourlyRate,
                totalEntries = empMonthRecords.size,
                fullDays = eFull,
                shortDays = eShort,
                absentDays = eAbsent,
                leaveDays = eLeave,
                totalWorkedMinutes = eWorked,
                totalOvertimeMinutes = eOt,
                approvedExpenses = approvedExp,
                baseEarnings = basePay,
                overtimePay = otPay,
                netPayable = netPay
            )
        }

        AttendanceUiState(
            authSession = effectiveSession,
            authErrorRes = form.authErrorRes,
            isAuthBusy = form.isAuthBusy,
            syncStatus = src.syncStatus,
            workspaceConfig = src.workspaceConfig,
            googleUserEmail = syncRepository?.currentUserEmail().orEmpty(),
            generatedCredentials = form.generatedCredentials,
            persona = effectivePersona,
            adminSection = form.adminSection,
            entryMode = form.entryMode,
            policy = policy,
            employees = employees,
            allRecords = records,
            filteredRecords = filtered,
            recordsForEntryDate = recordsForDate,
            expenses = expenses,
            approvals = approvals,
            monthlySummaries = summaries,
            siteStats = siteStats,
            availableSites = distinctSites,
            dashboardSiteFilter = form.dashboardSiteFilter,
            dashboardTotalEmployees = dashTotal,
            dashboardPresentToday = dashPresent,
            dashboardAbsentToday = dashAbsent,
            dashboardPendingCount = pendingCount,
            selectedEmployeeId = resolvedSelectedId,
            activeEmployeeAppId = resolvedEmployeeAppId,
            dateText = form.dateText,
            inMinutes = form.inMinutes,
            outMinutes = form.outMinutes,
            entryStatus = form.entryStatus,
            entryNote = form.entryNote,
            filterEmployeeId = resolvedFilterId,
            filterMonth = form.filterMonth,
            summaryMonth = form.summaryMonth,
            searchQuery = form.searchQuery,
            approvalFilter = form.approvalFilter,
            breakdown = AttendanceCalculator.calculateBreakdown(
                inMinutes = form.inMinutes,
                outMinutes = form.outMinutes,
                status = form.entryStatus,
                policy = policy
            ),
            willReplaceExisting = replaces,
            totalWorkedMinutes = workedSum,
            totalOvertimeMinutes = otSum,
            fullDayCount = fullDays,
            absentOrLeaveCount = absentLeave,
            feedback = form.feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AttendanceUiState()
    )

    fun clearAuthError() {
        formState.update { it.copy(authErrorRes = null) }
    }

    fun dismissGeneratedCredentials() {
        formState.update { it.copy(generatedCredentials = null) }
    }

    fun loginAdmin(
        mobile: String,
        password: String,
        workspaceId: String = uiState.value.authSession.workspaceId
    ) {
        val cleanWs = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
        formState.update { it.copy(isAuthBusy = true, authErrorRes = null) }
        viewModelScope.launch {
            when (val res = repository.loginAdmin(mobile, password, cleanWs)) {
                is AuthResult.Success -> {
                    val sync = syncRepository
                    if (sync != null && sync.currentUid() != null) {
                        val wsResult = sync.ensureAdminWorkspaceMembership(
                            workspaceId = cleanWs,
                            displayName = res.session.displayName,
                            policy = uiState.value.policy
                        )
                        val failure = wsResult.exceptionOrNull()
                        if (failure is FirebaseFirestoreException &&
                            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            formState.update {
                                it.copy(
                                    isAuthBusy = false,
                                    authErrorRes = R.string.error_co_admin_unauthorized
                                )
                            }
                            return@launch
                        }
                        wsResult.onSuccess { cfg ->
                            workspaceConfigFlow.value = cfg
                            pushLocalAdminDataToCloud(cleanWs, cfg.adminUids)
                        }
                    }
                    saveSession(res.session)
                    formState.update {
                        it.copy(
                            persona = AppPersona.ADMIN,
                            adminSection = AdminSection.DASHBOARD,
                            authErrorRes = null,
                            isAuthBusy = false,
                            feedback = FeedbackMessage(R.string.msg_login_success)
                        )
                    }
                }
                is AuthResult.Error -> {
                    formState.update {
                        it.copy(
                            isAuthBusy = false,
                            authErrorRes = mapAuthError(res.reason)
                        )
                    }
                }
            }
        }
    }

    fun signUpAdmin(
        name: String,
        mobile: String,
        password: String,
        confirmPassword: String,
        workspaceId: String = uiState.value.authSession.workspaceId
    ) {
        if (password != confirmPassword) {
            formState.update { it.copy(authErrorRes = R.string.auth_error_password_mismatch) }
            return
        }
        val cleanWs = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
        formState.update { it.copy(isAuthBusy = true, authErrorRes = null) }
        viewModelScope.launch {
            when (val res = repository.signUpAdmin(name, mobile, password, cleanWs)) {
                is AuthResult.Success -> {
                    val sync = syncRepository
                    if (sync != null && sync.currentUid() != null) {
                        val wsResult = sync.ensureAdminWorkspaceMembership(
                            workspaceId = cleanWs,
                            displayName = res.session.displayName,
                            policy = uiState.value.policy
                        )
                        val failure = wsResult.exceptionOrNull()
                        if (failure is FirebaseFirestoreException &&
                            failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) {
                            formState.update {
                                it.copy(
                                    isAuthBusy = false,
                                    authErrorRes = R.string.error_co_admin_unauthorized
                                )
                            }
                            return@launch
                        }
                        wsResult.onSuccess { cfg ->
                            workspaceConfigFlow.value = cfg
                            pushLocalAdminDataToCloud(cleanWs, cfg.adminUids)
                        }
                    }
                    saveSession(res.session)
                    formState.update {
                        it.copy(
                            persona = AppPersona.ADMIN,
                            adminSection = AdminSection.DASHBOARD,
                            authErrorRes = null,
                            isAuthBusy = false,
                            feedback = FeedbackMessage(R.string.msg_signup_success)
                        )
                    }
                }
                is AuthResult.Error -> {
                    formState.update {
                        it.copy(
                            isAuthBusy = false,
                            authErrorRes = mapAuthError(res.reason)
                        )
                    }
                }
            }
        }
    }

    fun loginEmployee(
        loginId: String,
        password: String,
        workspaceId: String = uiState.value.authSession.workspaceId
    ) {
        val cleanWs = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
        formState.update { it.copy(isAuthBusy = true, authErrorRes = null) }
        viewModelScope.launch {
            val sync = syncRepository
            if (sync != null && sync.currentUid() != null) {
                val remoteResult = sync.joinWorkspaceAsEmployeeAndVerify(
                    workspaceId = cleanWs,
                    loginId = loginId,
                    plainPassword = password
                )
                val remoteEmp = remoteResult.getOrNull()
                if (remoteEmp != null) {
                    repository.reconcileRemoteEmployees(listOf(remoteEmp))
                    val localRes = repository.loginEmployee(loginId, password, cleanWs)
                    if (localRes is AuthResult.Success) {
                        saveSession(localRes.session)
                        formState.update {
                            it.copy(
                                persona = AppPersona.EMPLOYEE,
                                activeEmployeeAppId = localRes.session.userId,
                                authErrorRes = null,
                                isAuthBusy = false,
                                feedback = FeedbackMessage(R.string.msg_login_success)
                            )
                        }
                        return@launch
                    }
                } else {
                    val failure = remoteResult.exceptionOrNull()
                    if (failure is IllegalArgumentException && failure.message == "INVALID_CREDENTIALS") {
                        // Also check local cache in case Admin hasn't synced yet on same device
                        val localFallback = repository.loginEmployee(loginId, password, cleanWs)
                        if (localFallback !is AuthResult.Success) {
                            formState.update {
                                it.copy(
                                    isAuthBusy = false,
                                    authErrorRes = R.string.auth_error_invalid_credentials
                                )
                            }
                            return@launch
                        }
                    }
                }
            }

            when (val res = repository.loginEmployee(loginId, password, cleanWs)) {
                is AuthResult.Success -> {
                    saveSession(res.session)
                    formState.update {
                        it.copy(
                            persona = AppPersona.EMPLOYEE,
                            activeEmployeeAppId = res.session.userId,
                            authErrorRes = null,
                            isAuthBusy = false,
                            feedback = FeedbackMessage(R.string.msg_login_success)
                        )
                    }
                }
                is AuthResult.Error -> {
                    formState.update {
                        it.copy(
                            isAuthBusy = false,
                            authErrorRes = mapAuthError(res.reason)
                        )
                    }
                }
            }
        }
    }

    private suspend fun pushLocalAdminDataToCloud(workspaceId: String, adminUids: List<String>) {
        val sync = syncRepository ?: return
        val admins = repository.getAllAdminAccounts()
        admins.forEach { admin ->
            runCatching { sync.upsertAdminAccountRemote(workspaceId, admin, adminUids) }
        }
        val employees = repository.getAllEmployees()
        val empById = employees.associateBy { it.id }
        employees.forEach { emp ->
            runCatching { sync.upsertEmployeeRemote(workspaceId, emp, adminUids) }
        }
        val entries = repository.getAllAttendance()
        entries.forEach { entry ->
            val emp = empById[entry.employeeId] ?: return@forEach
            runCatching { sync.upsertAttendanceByAdmin(workspaceId, emp, entry, adminUids) }
        }
        val expenses = repository.getAllExpenses()
        expenses.forEach { exp ->
            val loginId = exp.employeeId?.let { empById[it]?.loginId } ?: ""
            runCatching {
                sync.upsertExpenseRemote(
                    workspaceId = workspaceId,
                    expense = exp,
                    employeeLoginId = loginId,
                    adminUids = adminUids,
                    isAdminCaller = true
                )
            }
        }
        val approvals = repository.getAllApprovals()
        approvals.forEach { apr ->
            val loginId = empById[apr.employeeId]?.loginId ?: ""
            runCatching {
                sync.upsertApprovalRemote(
                    workspaceId = workspaceId,
                    approval = apr,
                    employeeLoginId = loginId,
                    adminUids = adminUids,
                    isAdminCaller = true
                )
            }
        }
    }

    fun logout() {
        clearSyncListeners()
        viewModelScope.launch {
            if (policyPreferences != null) {
                policyPreferences.clearAuthSession()
            } else {
                localAuthSession.value = AuthSession()
            }
            formState.update {
                it.copy(
                    authErrorRes = null,
                    isAuthBusy = false,
                    generatedCredentials = null,
                    adminSection = AdminSection.DASHBOARD
                )
            }
        }
    }

    private suspend fun saveSession(session: AuthSession) {
        if (policyPreferences != null) {
            policyPreferences.saveAuthSession(session)
        } else {
            localAuthSession.value = session
        }
    }

    @StringRes
    private fun mapAuthError(reason: AuthErrorReason): Int = when (reason) {
        AuthErrorReason.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
        AuthErrorReason.MOBILE_ALREADY_EXISTS -> R.string.auth_error_mobile_exists
        AuthErrorReason.INVALID_MOBILE -> R.string.auth_error_invalid_mobile
        AuthErrorReason.WEAK_PASSWORD -> R.string.auth_error_weak_password
        AuthErrorReason.NAME_REQUIRED -> R.string.auth_error_name_required
        AuthErrorReason.LOGIN_ID_ALREADY_EXISTS -> R.string.auth_error_login_id_exists
        AuthErrorReason.NO_PASSWORD_SET -> R.string.auth_error_no_password_set
    }

    fun selectPersona(persona: AppPersona) {
        formState.update { it.copy(persona = persona) }
    }

    fun selectAdminSection(section: AdminSection) {
        formState.update { it.copy(persona = AppPersona.ADMIN, adminSection = section) }
    }

    fun setDashboardSiteFilter(site: String?) {
        formState.update { it.copy(dashboardSiteFilter = site) }
    }

    fun selectEntryMode(mode: EntryMode) {
        formState.update { it.copy(entryMode = mode) }
    }

    fun selectEmployee(employeeId: Long) {
        formState.update { it.copy(selectedEmployeeId = employeeId) }
    }

    fun selectEmployeeAppProfile(employeeId: Long) {
        formState.update { it.copy(activeEmployeeAppId = employeeId) }
    }

    fun updateDateText(date: String) {
        formState.update { it.copy(dateText = date) }
    }

    fun updateDateMillis(millis: Long) {
        formState.update { it.copy(dateText = AttendanceCalculator.formatDateMillis(millis)) }
    }

    fun updateInMinutes(minutes: Int) {
        formState.update { it.copy(inMinutes = minutes, entryStatus = AttendanceStatus.PRESENT) }
    }

    fun updateOutMinutes(minutes: Int) {
        formState.update { it.copy(outMinutes = minutes, entryStatus = AttendanceStatus.PRESENT) }
    }

    fun punchInNow() {
        updateInMinutes(AttendanceCalculator.currentMinutesOfDay())
    }

    fun punchOutNow() {
        updateOutMinutes(AttendanceCalculator.currentMinutesOfDay())
    }

    fun updateEntryStatus(status: AttendanceStatus) {
        formState.update { it.copy(entryStatus = status) }
    }

    fun updateEntryNote(note: String) {
        formState.update { it.copy(entryNote = note) }
    }

    fun resetStandardShift() {
        val policy = uiState.value.policy
        formState.update {
            it.copy(
                inMinutes = policy.defaultInMinutes,
                outMinutes = policy.defaultOutMinutes,
                entryStatus = AttendanceStatus.PRESENT
            )
        }
    }

    fun setFilterEmployee(employeeId: Long?) {
        formState.update { it.copy(filterEmployeeId = employeeId) }
    }

    fun setFilterMonth(month: YearMonthKey?) {
        formState.update { it.copy(filterMonth = month) }
    }

    fun shiftSummaryMonth(delta: Int) {
        formState.update { it.copy(summaryMonth = it.summaryMonth.shift(delta)) }
    }

    fun updateSearchQuery(query: String) {
        formState.update { it.copy(searchQuery = query) }
    }

    fun setApprovalFilter(filter: String) {
        formState.update { it.copy(approvalFilter = filter) }
    }

    fun clearFeedback() {
        formState.update { it.copy(feedback = null) }
    }

    fun showFeedback(@StringRes messageRes: Int, isError: Boolean = false) {
        formState.update { it.copy(feedback = FeedbackMessage(messageRes, isError = isError)) }
    }

    fun addEmployee(
        name: String,
        employeeCode: String = "",
        site: String = EmployeeEntity.DEFAULT_SITE,
        role: String = "",
        phone: String = "",
        dailyWage: Double = 0.0,
        otHourlyRate: Double = 0.0,
        loginId: String = "",
        password: String = ""
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val resolvedSite = site.trim().ifEmpty { EmployeeEntity.DEFAULT_SITE }
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            val created = repository.addEmployeeWithCredentials(
                name = trimmed,
                employeeCode = employeeCode,
                site = resolvedSite,
                role = role,
                phone = phone,
                dailyWage = dailyWage,
                otHourlyRate = otHourlyRate,
                requestedLoginId = loginId,
                requestedPassword = password
            )
            repository.getEmployeeById(created.employeeId)?.let { emp ->
                syncRepository?.upsertEmployeeRemote(wsId, emp, currentAdminUids())
            }
            formState.update {
                it.copy(
                    selectedEmployeeId = created.employeeId,
                    activeEmployeeAppId = it.activeEmployeeAppId ?: created.employeeId,
                    generatedCredentials = GeneratedCredentialsDialogState(
                        employeeName = trimmed,
                        site = resolvedSite,
                        loginId = created.loginId,
                        plainPassword = created.plainPassword,
                        workspaceId = wsId,
                        isReset = false
                    ),
                    feedback = FeedbackMessage(R.string.msg_employee_added)
                )
            }
        }
    }

    fun resetEmployeePassword(employee: EmployeeEntity, newPassword: String) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            val (resolvedLoginId, resolvedPassword) = repository.resetEmployeePassword(
                employee = employee,
                newPlainPassword = newPassword
            )
            repository.getEmployeeById(employee.id)?.let { updated ->
                syncRepository?.upsertEmployeeRemote(wsId, updated, currentAdminUids())
            }
            formState.update {
                it.copy(
                    generatedCredentials = GeneratedCredentialsDialogState(
                        employeeName = employee.name,
                        site = employee.site,
                        loginId = resolvedLoginId,
                        plainPassword = resolvedPassword,
                        workspaceId = wsId,
                        isReset = true
                    ),
                    feedback = FeedbackMessage(R.string.msg_password_reset)
                )
            }
        }
    }

    fun updateEmployee(
        employee: EmployeeEntity,
        name: String,
        employeeCode: String,
        site: String,
        role: String,
        phone: String,
        dailyWage: Double,
        otHourlyRate: Double
    ) {
        if (name.isBlank()) return
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.updateEmployee(
                employee = employee,
                name = name,
                employeeCode = employeeCode,
                site = site,
                role = role,
                phone = phone,
                dailyWage = dailyWage,
                otHourlyRate = otHourlyRate
            )
            repository.getEmployeeById(employee.id)?.let { updated ->
                syncRepository?.upsertEmployeeRemote(wsId, updated, currentAdminUids())
            }
            formState.update {
                it.copy(feedback = FeedbackMessage(R.string.msg_employee_updated))
            }
        }
    }

    fun deleteEmployee(employeeId: Long) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            val existing = repository.getEmployeeById(employeeId)
            repository.deleteEmployee(employeeId)
            if (existing != null) {
                syncRepository?.deleteEmployeeRemote(wsId, existing.remoteId, existing.loginId)
            }
            formState.update { state ->
                state.copy(
                    selectedEmployeeId = if (state.selectedEmployeeId == employeeId) null else state.selectedEmployeeId,
                    activeEmployeeAppId = if (state.activeEmployeeAppId == employeeId) null else state.activeEmployeeAppId,
                    filterEmployeeId = if (state.filterEmployeeId == employeeId) null else state.filterEmployeeId,
                    feedback = FeedbackMessage(R.string.msg_employee_deleted)
                )
            }
        }
    }

    fun saveAttendance() {
        val current = uiState.value
        val employeeId = current.selectedEmployeeId
        val date = current.dateText.trim()

        if (employeeId == null || current.employees.none { it.id == employeeId }) {
            showFeedback(R.string.error_select_employee, isError = true)
            return
        }
        if (!AttendanceCalculator.isValidDateString(date)) {
            showFeedback(R.string.error_invalid_date, isError = true)
            return
        }
        if (current.entryStatus == AttendanceStatus.PRESENT && current.breakdown.workedMinutes == 0) {
            showFeedback(R.string.error_same_in_out, isError = true)
            return
        }

        val wasReplacing = current.willReplaceExisting
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.saveAttendance(
                employeeId = employeeId,
                date = date,
                inMinutes = current.inMinutes,
                outMinutes = current.outMinutes,
                status = current.entryStatus,
                note = current.entryNote
            )
            syncAttendanceToCloudByAdmin(wsId, employeeId, date)
            formState.update {
                it.copy(
                    entryNote = "",
                    feedback = FeedbackMessage(
                        if (wasReplacing) R.string.msg_attendance_replaced else R.string.msg_attendance_saved
                    )
                )
            }
        }
    }

    fun selfCheckInOutWithProof(
        employeeId: Long,
        isCheckOut: Boolean,
        photoBase64: String,
        lat: Double,
        lng: Double,
        address: String
    ) {
        val today = AttendanceCalculator.todayDateString()
        val now = AttendanceCalculator.currentMinutesOfDay()
        val wsId = currentWorkspaceId()

        viewModelScope.launch {
            val result = if (isCheckOut) {
                repository.recordSelfCheckOutWithProof(
                    employeeId = employeeId,
                    date = today,
                    outMinutes = now,
                    photoBase64 = photoBase64,
                    lat = lat,
                    lng = lng,
                    address = address
                )
            } else {
                repository.recordSelfCheckInWithProof(
                    employeeId = employeeId,
                    date = today,
                    inMinutes = now,
                    photoBase64 = photoBase64,
                    lat = lat,
                    lng = lng,
                    address = address
                )
            }

            when (result) {
                is SelfPunchResult.Rejected -> {
                    val errRes = when (result.reason) {
                        SelfPunchRejectReason.ALREADY_CHECKED_IN -> R.string.error_already_checked_in
                        SelfPunchRejectReason.NOT_CHECKED_IN_YET -> R.string.error_not_checked_in_yet
                        SelfPunchRejectReason.ALREADY_CHECKED_OUT -> R.string.error_already_checked_out
                        SelfPunchRejectReason.LOCKED_BY_ADMIN -> R.string.error_locked_by_admin
                        SelfPunchRejectReason.SAME_IN_OUT_TIME -> R.string.error_same_in_out
                        SelfPunchRejectReason.EMPLOYEE_NOT_FOUND -> R.string.error_select_employee
                    }
                    showFeedback(errRes, isError = true)
                }
                is SelfPunchResult.Success -> {
                    val emp = repository.getEmployeeById(employeeId)
                    val sync = syncRepository
                    if (emp != null && sync != null && sync.currentUid() != null) {
                        val remoteRes = if (isCheckOut) {
                            sync.submitEmployeeCheckOutWithProof(
                                workspaceId = wsId,
                                entry = result.entry,
                                employeeRemoteId = emp.remoteId
                            )
                        } else {
                            sync.submitEmployeeCheckInWithProof(
                                workspaceId = wsId,
                                employee = emp,
                                entry = result.entry,
                                adminUids = currentAdminUids()
                            )
                        }
                        if (remoteRes.isFailure) {
                            syncStatusFlow.value = SyncStatus.ERROR
                        } else {
                            syncStatusFlow.value = SyncStatus.ONLINE
                        }
                    }
                    showFeedback(
                        if (isCheckOut) R.string.msg_selfie_checkout_saved else R.string.msg_selfie_checkin_saved
                    )
                }
            }
        }
    }

    private suspend fun syncAttendanceToCloudByAdmin(workspaceId: String, employeeId: Long, date: String) {
        val sync = syncRepository ?: return
        if (sync.currentUid() == null) return
        val emp = repository.getEmployeeById(employeeId) ?: return
        val entry = repository.getAllAttendance().firstOrNull {
            it.employeeId == employeeId && it.date == date
        } ?: return
        sync.upsertAttendanceByAdmin(workspaceId, emp, entry, currentAdminUids())
    }

    fun quickMarkRollCall(
        employeeId: Long,
        status: AttendanceStatus,
        inMinutes: Int = uiState.value.policy.defaultInMinutes,
        outMinutes: Int = uiState.value.policy.defaultOutMinutes
    ) {
        val date = uiState.value.dateText.trim()
        if (!AttendanceCalculator.isValidDateString(date)) {
            showFeedback(R.string.error_invalid_date, isError = true)
            return
        }
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.saveAttendance(
                employeeId = employeeId,
                date = date,
                inMinutes = inMinutes,
                outMinutes = outMinutes,
                status = status,
                note = ""
            )
            syncAttendanceToCloudByAdmin(wsId, employeeId, date)
            showFeedback(R.string.msg_roll_call_updated)
        }
    }

    fun markAllFullDayRollCall() {
        val current = uiState.value
        val date = current.dateText.trim()
        if (current.employees.isEmpty()) {
            showFeedback(R.string.error_select_employee, isError = true)
            return
        }
        if (!AttendanceCalculator.isValidDateString(date)) {
            showFeedback(R.string.error_invalid_date, isError = true)
            return
        }
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.saveRollCallBatch(
                employeeIds = current.employees.map { it.id },
                date = date,
                inMinutes = current.policy.defaultInMinutes,
                outMinutes = current.policy.defaultOutMinutes,
                status = AttendanceStatus.PRESENT
            )
            current.employees.forEach { emp ->
                syncAttendanceToCloudByAdmin(wsId, emp.id, date)
            }
            showFeedback(R.string.msg_roll_call_updated)
        }
    }

    fun editRecord(record: AttendanceRecordItem) {
        val status = AttendanceStatus.fromStorage(record.status)
        val policy = uiState.value.policy
        formState.update {
            it.copy(
                persona = AppPersona.ADMIN,
                adminSection = AdminSection.ATTENDANCE,
                entryMode = EntryMode.SINGLE,
                selectedEmployeeId = record.employeeId,
                dateText = record.date,
                inMinutes = if (status == AttendanceStatus.PRESENT) record.inMinutes else policy.defaultInMinutes,
                outMinutes = if (status == AttendanceStatus.PRESENT) record.outMinutes else policy.defaultOutMinutes,
                entryStatus = status,
                entryNote = record.note
            )
        }
    }

    fun deleteRecord(record: AttendanceRecordItem) {
        lastDeletedRecord = record
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.deleteAttendanceEntry(record.id)
            syncRepository?.deleteAttendanceRemote(wsId, record.remoteId)
            formState.update {
                it.copy(
                    feedback = FeedbackMessage(
                        messageRes = R.string.msg_record_deleted,
                        showUndo = true
                    )
                )
            }
        }
    }

    fun undoDeleteRecord() {
        val record = lastDeletedRecord ?: return
        lastDeletedRecord = null
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.restoreEntry(record)
            syncAttendanceToCloudByAdmin(wsId, record.employeeId, record.date)
        }
    }

    // Expenses
    fun addExpense(
        employeeId: Long?,
        site: String,
        title: String,
        category: String,
        amount: Double,
        date: String,
        note: String,
        autoApprove: Boolean
    ) {
        val current = uiState.value
        val emp = employeeId?.let { id -> current.employees.firstOrNull { it.id == id } }
        val empName = emp?.name ?: ""
        val wsId = currentWorkspaceId()
        val isAdminCaller = current.authSession.role == AuthRole.ADMIN
        val uid = syncRepository?.currentUid() ?: ""

        viewModelScope.launch {
            val id = repository.addExpense(
                employeeId = employeeId,
                employeeName = empName,
                site = site,
                title = title,
                category = category,
                amount = amount,
                date = date,
                status = if (autoApprove && isAdminCaller) "APPROVED" else "PENDING",
                note = note,
                employeeUid = uid
            )
            val inserted = repository.getExpenseById(id)
            if (inserted != null) {
                syncRepository?.upsertExpenseRemote(
                    workspaceId = wsId,
                    expense = inserted,
                    employeeLoginId = emp?.loginId ?: "",
                    adminUids = currentAdminUids(),
                    isAdminCaller = isAdminCaller
                )
            }
            showFeedback(R.string.msg_expense_added)
        }
    }

    fun setExpenseStatus(expenseId: Long, approved: Boolean) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.updateExpenseStatus(expenseId, if (approved) "APPROVED" else "REJECTED")
            val updated = repository.getExpenseById(expenseId)
            if (updated != null) {
                val loginId = updated.employeeId?.let { repository.getEmployeeById(it)?.loginId } ?: ""
                syncRepository?.upsertExpenseRemote(
                    workspaceId = wsId,
                    expense = updated,
                    employeeLoginId = loginId,
                    adminUids = currentAdminUids(),
                    isAdminCaller = true
                )
            }
            showFeedback(R.string.msg_approval_updated)
        }
    }

    fun setExpenseClaimed(expenseId: Long, isClaimed: Boolean) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.updateExpenseClaimed(expenseId, isClaimed)
            val updated = repository.getExpenseById(expenseId)
            if (updated != null) {
                val loginId = updated.employeeId?.let { repository.getEmployeeById(it)?.loginId } ?: ""
                syncRepository?.upsertExpenseRemote(
                    workspaceId = wsId,
                    expense = updated,
                    employeeLoginId = loginId,
                    adminUids = currentAdminUids(),
                    isAdminCaller = true
                )
            }
            showFeedback(R.string.msg_claim_updated)
        }
    }

    fun deleteExpense(expenseId: Long) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            val existing = repository.getExpenseById(expenseId)
            repository.deleteExpense(expenseId)
            if (existing != null) {
                syncRepository?.deleteExpenseRemote(wsId, existing.remoteId)
            }
        }
    }

    // Approvals
    fun submitApprovalRequest(
        employeeId: Long,
        type: String,
        date: String,
        inMinutes: Int,
        outMinutes: Int,
        reason: String
    ) {
        val current = uiState.value
        val emp = current.employees.firstOrNull { it.id == employeeId } ?: return
        val wsId = currentWorkspaceId()
        val uid = syncRepository?.currentUid() ?: ""
        val isAdminCaller = current.authSession.role == AuthRole.ADMIN

        viewModelScope.launch {
            val id = repository.submitApprovalRequest(
                employeeId = employeeId,
                employeeName = emp.name,
                type = type,
                date = date,
                inMinutes = inMinutes,
                outMinutes = outMinutes,
                reason = reason,
                employeeUid = uid
            )
            val inserted = repository.getApprovalById(id)
            if (inserted != null) {
                syncRepository?.upsertApprovalRemote(
                    workspaceId = wsId,
                    approval = inserted,
                    employeeLoginId = emp.loginId,
                    adminUids = currentAdminUids(),
                    isAdminCaller = isAdminCaller
                )
            }
            showFeedback(R.string.msg_request_submitted)
        }
    }

    fun resolveApproval(request: ApprovalRequestEntity, approved: Boolean) {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            repository.resolveApprovalRequest(request, approved)
            val updated = repository.getApprovalById(request.id)
            if (updated != null) {
                val loginId = repository.getEmployeeById(request.employeeId)?.loginId ?: ""
                syncRepository?.upsertApprovalRemote(
                    workspaceId = wsId,
                    approval = updated,
                    employeeLoginId = loginId,
                    adminUids = currentAdminUids(),
                    isAdminCaller = true
                )
            }
            if (approved) {
                syncAttendanceToCloudByAdmin(wsId, request.employeeId, request.date)
            }
            showFeedback(R.string.msg_approval_updated)
        }
    }

    fun deleteApproval(requestId: Long) {
        viewModelScope.launch {
            repository.deleteApprovalRequest(requestId)
        }
    }

    // Settings
    fun savePolicy(policy: AttendancePolicy) {
        val prefs = policyPreferences ?: return
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            prefs.updatePolicy(policy)
            syncRepository?.updateWorkspacePolicyAndEmails(
                workspaceId = wsId,
                policy = policy,
                authorizedAdminEmails = null
            )
            formState.update {
                it.copy(
                    inMinutes = policy.defaultInMinutes,
                    outMinutes = policy.defaultOutMinutes,
                    feedback = FeedbackMessage(R.string.msg_policy_saved)
                )
            }
        }
    }

    fun updateAuthorizedAdminEmails(emailsText: String) {
        val wsId = currentWorkspaceId()
        val parsed = emailsText.split(",", "\n", ";")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it.contains("@") }
            .distinct()
        viewModelScope.launch {
            val res = syncRepository?.updateWorkspacePolicyAndEmails(
                workspaceId = wsId,
                policy = uiState.value.policy,
                authorizedAdminEmails = parsed
            )
            if (res?.isFailure == true) {
                showFeedback(R.string.error_cloud_sync_failed, isError = true)
            } else {
                showFeedback(R.string.msg_workspace_settings_saved)
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        val prefs = policyPreferences ?: return
        viewModelScope.launch {
            prefs.updateThemeMode(mode)
        }
    }

    fun resetPolicyDefaults() {
        val prefs = policyPreferences ?: return
        val wsId = currentWorkspaceId()
        val defaults = AttendancePolicy()
        viewModelScope.launch {
            prefs.resetDefaults()
            syncRepository?.updateWorkspacePolicyAndEmails(
                workspaceId = wsId,
                policy = defaults,
                authorizedAdminEmails = null
            )
            formState.update {
                it.copy(
                    inMinutes = AttendancePolicy.DEFAULT_IN_MINUTES,
                    outMinutes = AttendancePolicy.DEFAULT_OUT_MINUTES,
                    feedback = FeedbackMessage(R.string.msg_policy_saved)
                )
            }
        }
    }

    fun loadSampleWorkforce() {
        val wsId = currentWorkspaceId()
        viewModelScope.launch {
            val today = AttendanceCalculator.todayDateString()
            val yesterday = AttendanceCalculator.yesterdayDateString()
            val specs = listOf(
                Triple("Rajesh Patil", "PBS-101", "Sai Baba Nagar") to Triple("Site Supervisor", 900.0, 150.0),
                Triple("Aarav Sharma", "PBS-102", "Sai Baba Nagar") to Triple("Technician", 750.0, 125.0),
                Triple("Vikram Pawar", "PBS-103", "Sai Baba Nagar") to Triple("Helper", 600.0, 100.0),
                Triple("Suresh Yadav", "PBS-104", "Meditya Nagar") to Triple("Foreman", 850.0, 140.0),
                Triple("Priya Nair", "PBS-105", "Meditya Nagar") to Triple("Coordinator", 800.0, 130.0),
                Triple("Rohan Verma", "PBS-106", "Other Sites") to Triple("Electrician", 780.0, 130.0)
            )
            val createdIds = mutableListOf<Long>()
            for ((bio, pay) in specs) {
                val created = repository.addEmployeeWithCredentials(
                    name = bio.first,
                    employeeCode = bio.second,
                    site = bio.third,
                    role = pay.first,
                    phone = "98765${(10000..99999).random()}",
                    dailyWage = pay.second,
                    otHourlyRate = pay.third,
                    requestedLoginId = bio.second,
                    requestedPassword = AuthUtils.generateReadablePassword()
                )
                createdIds += created.employeeId
            }
            if (createdIds.size >= 6) {
                repository.saveAttendance(createdIds[0], today, 9 * 60 + 25, 20 * 60 + 15, AttendanceStatus.PRESENT, "Site inspection + OT")
                repository.saveAttendance(createdIds[1], today, 9 * 60 + 30, 18 * 60 + 15, AttendanceStatus.PRESENT, "")
                repository.saveAttendance(createdIds[2], today, 0, 0, AttendanceStatus.ABSENT, "Uninformed")
                repository.saveAttendance(createdIds[3], today, 9 * 60 + 30, 19 * 60 + 45, AttendanceStatus.PRESENT, "Concrete pour")
                repository.saveAttendance(createdIds[4], today, 9 * 60 + 30, 18 * 60 + 15, AttendanceStatus.PRESENT, "")
                repository.saveAttendance(createdIds[5], today, 9 * 60 + 40, 17 * 60 + 30, AttendanceStatus.PRESENT, "Short shift")

                repository.saveAttendance(createdIds[0], yesterday, 9 * 60 + 30, 18 * 60 + 15, AttendanceStatus.PRESENT, "")
                repository.saveAttendance(createdIds[1], yesterday, 9 * 60 + 30, 20 * 60 + 30, AttendanceStatus.PRESENT, "Evening OT")

                val exp1Id = repository.addExpense(createdIds[0], "Rajesh Patil", "Sai Baba Nagar", "Cement & Hardware", "Site Material", 1850.0, today, "APPROVED", "Bill #402")
                repository.updateExpenseClaimed(exp1Id, true)
                repository.addExpense(createdIds[1], "Aarav Sharma", "Sai Baba Nagar", "Site Tea & Snacks", "Food", 320.0, yesterday, "APPROVED", "Team refreshments")
                repository.addExpense(createdIds[3], "Suresh Yadav", "Meditya Nagar", "Diesel & Transport", "Travel", 640.0, today, "PENDING", "Site delivery")

                repository.submitApprovalRequest(
                    employeeId = createdIds[2],
                    employeeName = "Vikram Pawar",
                    type = "LEAVE",
                    date = today,
                    inMinutes = 0,
                    outMinutes = 0,
                    reason = "Family medical appointment"
                )
                if (syncRepository != null && syncRepository.currentUid() != null) {
                    pushLocalAdminDataToCloud(wsId, currentAdminUids())
                }
            }
            showFeedback(R.string.msg_demo_loaded)
        }
    }

    class Factory(
        private val repository: AttendanceRepository,
        private val policyPreferences: PolicyPreferences,
        private val syncRepository: FirestoreSyncRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
                return AttendanceViewModel(repository, policyPreferences, syncRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

private data class QuadData(
    val employees: List<EmployeeEntity>,
    val records: List<AttendanceRecordItem>,
    val expenses: List<ExpenseEntity>,
    val approvals: List<ApprovalRequestEntity>
)

private data class QuadConfig(
    val policy: AttendancePolicy,
    val auth: AuthSession,
    val syncStatus: SyncStatus,
    val wsConfig: WorkspaceRemoteConfig?
)
