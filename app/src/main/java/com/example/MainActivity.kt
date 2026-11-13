package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppThemeMode
import com.example.data.AttendancePolicy
import com.example.data.AttendanceRepository
import com.example.data.AuthRole
import com.example.data.EmployeeEntity
import com.example.data.FirestoreSyncRepository
import com.example.data.PolicyPreferences
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceExportHelper
import com.example.ui.AdminSection
import com.example.ui.AppPersona
import com.example.ui.AttendanceUiState
import com.example.ui.AttendanceViewModel
import com.example.ui.GoogleSyncSignInScreen
import com.example.ui.SyncStatus
import com.example.ui.authStateFlow
import com.example.ui.components.EmployeeFormDialog
import com.example.ui.components.GeneratedCredentialsDialog
import com.example.ui.screens.ApprovalsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EmployeeAppScreen
import com.example.ui.screens.EntryScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SalaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeamScreen
import com.example.ui.signOutGoogle
import com.example.ui.theme.LocalAttendanceColors
import com.example.ui.theme.PbsAttendanceTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = AttendanceRepository(database.attendanceDao())
        val policyPreferences = PolicyPreferences(applicationContext)
        val syncRepository = FirestoreSyncRepository(applicationContext)

        setContent {
            val policy by policyPreferences.policyFlow.collectAsStateWithLifecycle(
                initialValue = AttendancePolicy()
            )
            val darkTheme = when (policy.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PbsAttendanceTheme(darkTheme = darkTheme) {
                AppContent(
                    repository = repository,
                    policyPreferences = policyPreferences,
                    syncRepository = syncRepository
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    repository: AttendanceRepository,
    policyPreferences: PolicyPreferences,
    syncRepository: FirestoreSyncRepository
) {
    val firebaseAuth = remember { Firebase.auth }
    val currentUser by firebaseAuth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = firebaseAuth.currentUser
    )
    val user = currentUser

    if (user == null) {
        GoogleSyncSignInScreen()
    } else {
        key(user.uid) {
            val viewModel: AttendanceViewModel = viewModel(
                key = user.uid,
                factory = AttendanceViewModel.Factory(
                    repository = repository,
                    policyPreferences = policyPreferences,
                    syncRepository = syncRepository
                )
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PbsAttendanceApp(viewModel = viewModel, uiState = uiState)
        }
    }
}

@Composable
fun PbsAttendanceApp(
    viewModel: AttendanceViewModel,
    uiState: AttendanceUiState
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var showEmployeeDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }

    val handleGoogleSignOut: () -> Unit = {
        viewModel.logout()
        signOutGoogle(
            credentialManager = credentialManager,
            scope = coroutineScope
        )
    }

    if (uiState.authSession.role == AuthRole.NONE) {
        LoginScreen(
            authErrorRes = uiState.authErrorRes,
            initialWorkspaceId = uiState.authSession.workspaceId,
            googleUserEmail = uiState.googleUserEmail,
            isAuthBusy = uiState.isAuthBusy,
            onClearError = viewModel::clearAuthError,
            onGoogleSignOut = handleGoogleSignOut,
            onAdminLogin = viewModel::loginAdmin,
            onAdminSignUp = viewModel::signUpAdmin,
            onEmployeeLogin = viewModel::loginEmployee
        )
        return
    }

    BackHandler(
        enabled = uiState.persona == AppPersona.ADMIN && uiState.adminSection != AdminSection.DASHBOARD
    ) {
        viewModel.selectAdminSection(AdminSection.DASHBOARD)
    }

    LaunchedEffect(uiState.feedback) {
        val feedback = uiState.feedback ?: return@LaunchedEffect
        val message = context.getString(feedback.messageRes)
        val actionLabel = if (feedback.showUndo) context.getString(R.string.action_undo) else null
        viewModel.clearFeedback()
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && feedback.showUndo) {
            viewModel.undoDeleteRecord()
        }
    }

    val primaryNavSections = remember {
        listOf(
            AdminSection.DASHBOARD,
            AdminSection.ATTENDANCE,
            AdminSection.EMPLOYEES,
            AdminSection.APPROVALS,
            AdminSection.REPORTS
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                AppBrandHeader(
                    uiState = uiState,
                    onBackToDashboard = { viewModel.selectAdminSection(AdminSection.DASHBOARD) },
                    onLogout = viewModel::logout
                )
            },
            bottomBar = {
                if (!useNavRail && uiState.persona == AppPersona.ADMIN) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        primaryNavSections.forEach { section ->
                            val selected = uiState.adminSection == section
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectAdminSection(section) },
                                icon = { SectionIcon(section) },
                                label = {
                                    Text(
                                        text = stringResource(section.titleRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_bar_${section.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && uiState.persona == AppPersona.ADMIN) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AdminSection.entries.forEach { section ->
                            val selected = uiState.adminSection == section
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectAdminSection(section) },
                                icon = { SectionIcon(section) },
                                label = { Text(stringResource(section.titleRes)) },
                                modifier = Modifier.testTag("rail_${section.name.lowercase()}")
                            )
                        }
                    }
                }

                if (uiState.persona == AppPersona.EMPLOYEE) {
                    EmployeeAppScreen(
                        uiState = uiState,
                        onSelectEmployeeProfile = viewModel::selectEmployeeAppProfile,
                        onOpenAddEmployee = {
                            editingEmployee = null
                            showEmployeeDialog = true
                        },
                        onSelfCheckInOutWithProof = viewModel::selfCheckInOutWithProof,
                        onSubmitApprovalRequest = viewModel::submitApprovalRequest,
                        onSubmitExpense = { empId, site, title, cat, amount, date, note ->
                            viewModel.addExpense(
                                employeeId = empId,
                                site = site,
                                title = title,
                                category = cat,
                                amount = amount,
                                date = date,
                                note = note,
                                autoApprove = false
                            )
                        },
                        onShowFeedback = viewModel::showFeedback,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    when (uiState.adminSection) {
                        AdminSection.DASHBOARD -> DashboardScreen(
                            uiState = uiState,
                            onSelectSection = viewModel::selectAdminSection,
                            onSelectSiteFilter = viewModel::setDashboardSiteFilter,
                            onOpenAddEmployee = {
                                editingEmployee = null
                                showEmployeeDialog = true
                            },
                            onLoadSampleWorkforce = viewModel::loadSampleWorkforce,
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.ATTENDANCE -> EntryScreen(
                            uiState = uiState,
                            onEntryModeChange = viewModel::selectEntryMode,
                            onOpenAddEmployee = {
                                editingEmployee = null
                                showEmployeeDialog = true
                            },
                            onSelectEmployee = viewModel::selectEmployee,
                            onDateTextChange = viewModel::updateDateText,
                            onDateMillisSelected = viewModel::updateDateMillis,
                            onStatusSelected = viewModel::updateEntryStatus,
                            onInMinutesSelected = viewModel::updateInMinutes,
                            onOutMinutesSelected = viewModel::updateOutMinutes,
                            onPunchInNow = viewModel::punchInNow,
                            onPunchOutNow = viewModel::punchOutNow,
                            onNoteChange = viewModel::updateEntryNote,
                            onResetStandardShift = viewModel::resetStandardShift,
                            onSaveAttendance = viewModel::saveAttendance,
                            onQuickMarkRollCall = viewModel::quickMarkRollCall,
                            onMarkAllFullDayRollCall = viewModel::markAllFullDayRollCall,
                            onEditRecord = viewModel::editRecord,
                            onDeleteRecord = viewModel::deleteRecord,
                            onViewAllRecords = { viewModel.selectAdminSection(AdminSection.REPORTS) },
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.EMPLOYEES -> TeamScreen(
                            uiState = uiState,
                            onOpenAddEmployee = {
                                editingEmployee = null
                                showEmployeeDialog = true
                            },
                            onEditEmployee = { emp ->
                                editingEmployee = emp
                                showEmployeeDialog = true
                            },
                            onResetEmployeePassword = viewModel::resetEmployeePassword,
                            onDeleteEmployee = viewModel::deleteEmployee,
                            onViewEmployeeRecords = { empId ->
                                viewModel.setFilterEmployee(empId)
                                viewModel.setFilterMonth(null)
                                viewModel.selectAdminSection(AdminSection.REPORTS)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.EXPENSES -> ExpensesScreen(
                            uiState = uiState,
                            onAddExpense = viewModel::addExpense,
                            onSetExpenseStatus = viewModel::setExpenseStatus,
                            onSetExpenseClaimed = viewModel::setExpenseClaimed,
                            onDeleteExpense = viewModel::deleteExpense,
                            onShowFeedback = viewModel::showFeedback,
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.SALARY -> SalaryScreen(
                            uiState = uiState,
                            onShiftMonth = viewModel::shiftSummaryMonth,
                            onExportPayrollCsv = {
                                val csv = AttendanceExportHelper.buildSalaryCsvContent(
                                    monthLabel = uiState.summaryMonth.displayLabel(),
                                    summaries = uiState.monthlySummaries
                                )
                                val safeName = "pbs_payroll_${uiState.summaryMonth.year}_${uiState.summaryMonth.month}.csv"
                                if (!AttendanceExportHelper.shareCsv(context, safeName, csv)) {
                                    viewModel.showFeedback(R.string.msg_export_failed, isError = true)
                                }
                            },
                            onSharePayrollSummary = {
                                val label = uiState.summaryMonth.displayLabel()
                                val text = AttendanceExportHelper.buildSummaryText(label, uiState.monthlySummaries)
                                AttendanceExportHelper.sharePlainText(context, "PBS Salary — $label", text)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.APPROVALS -> ApprovalsScreen(
                            uiState = uiState,
                            onFilterChange = viewModel::setApprovalFilter,
                            onResolveApproval = viewModel::resolveApproval,
                            onResolveExpense = viewModel::setExpenseStatus,
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.REPORTS -> ReportsScreen(
                            uiState = uiState,
                            onFilterEmployeeChange = viewModel::setFilterEmployee,
                            onFilterMonthChange = viewModel::setFilterMonth,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onShiftSummaryMonth = viewModel::shiftSummaryMonth,
                            onExportFilteredCsv = {
                                val csv = AttendanceExportHelper.buildCsvContent(uiState.filteredRecords, uiState.policy)
                                if (!AttendanceExportHelper.shareCsv(context, "pbs_attendance_records.csv", csv)) {
                                    viewModel.showFeedback(R.string.msg_export_failed, isError = true)
                                }
                            },
                            onExportMonthlyCsv = {
                                val monthRecords = uiState.allRecords.filter {
                                    AttendanceCalculator.extractYearMonth(it.date) == uiState.summaryMonth
                                }
                                val csv = AttendanceExportHelper.buildCsvContent(monthRecords, uiState.policy)
                                val safeName = "pbs_attendance_${uiState.summaryMonth.year}_${uiState.summaryMonth.month}.csv"
                                if (!AttendanceExportHelper.shareCsv(context, safeName, csv)) {
                                    viewModel.showFeedback(R.string.msg_export_failed, isError = true)
                                }
                            },
                            onShareMonthlySummary = {
                                val label = uiState.summaryMonth.displayLabel()
                                val text = AttendanceExportHelper.buildSummaryText(label, uiState.monthlySummaries)
                                AttendanceExportHelper.sharePlainText(context, "PBS Attendance — $label", text)
                            },
                            onEditRecord = viewModel::editRecord,
                            onDeleteRecord = viewModel::deleteRecord,
                            modifier = Modifier.weight(1f)
                        )

                        AdminSection.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            onSavePolicy = viewModel::savePolicy,
                            onThemeModeChange = viewModel::setThemeMode,
                            onResetDefaults = viewModel::resetPolicyDefaults,
                            onLoadSampleWorkforce = viewModel::loadSampleWorkforce,
                            onUpdateAuthorizedAdminEmails = viewModel::updateAuthorizedAdminEmails,
                            onGoogleSignOut = handleGoogleSignOut,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showEmployeeDialog) {
        EmployeeFormDialog(
            existingEmployee = editingEmployee,
            availableSites = uiState.availableSites,
            existingLoginIds = uiState.employees.map { it.loginId }.filter { it.isNotBlank() }.toSet(),
            existingEmployeeCount = uiState.employees.size,
            onDismiss = {
                showEmployeeDialog = false
                editingEmployee = null
            },
            onConfirm = { name, code, site, role, phone, dailyWage, otHourlyRate, loginId, password ->
                val target = editingEmployee
                if (target == null) {
                    viewModel.addEmployee(
                        name = name,
                        employeeCode = code,
                        site = site,
                        role = role,
                        phone = phone,
                        dailyWage = dailyWage,
                        otHourlyRate = otHourlyRate,
                        loginId = loginId,
                        password = password
                    )
                } else {
                    viewModel.updateEmployee(
                        employee = target,
                        name = name,
                        employeeCode = code,
                        site = site,
                        role = role,
                        phone = phone,
                        dailyWage = dailyWage,
                        otHourlyRate = otHourlyRate
                    )
                }
                showEmployeeDialog = false
                editingEmployee = null
            }
        )
    }

    uiState.generatedCredentials?.let { credState ->
        GeneratedCredentialsDialog(
            state = credState,
            onCopied = { viewModel.showFeedback(R.string.credentials_copied_msg) },
            onDismiss = viewModel::dismissGeneratedCredentials
        )
    }
}

@Composable
private fun AppBrandHeader(
    uiState: AttendanceUiState,
    onBackToDashboard: () -> Unit,
    onLogout: () -> Unit
) {
    val semantic = LocalAttendanceColors.current
    val (badgeContainer, badgeContent, badgeBorder, badgeIcon, badgeText) = when (uiState.syncStatus) {
        SyncStatus.ONLINE -> HeaderBadgeStyle(
            semantic.fullDayContainer,
            semantic.onFullDayContainer,
            semantic.fullDayBorder,
            Icons.Filled.CloudDone,
            stringResource(R.string.cloud_sync_online_badge, uiState.authSession.workspaceId)
        )
        SyncStatus.SYNCING -> HeaderBadgeStyle(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.Filled.CloudSync,
            stringResource(R.string.cloud_sync_syncing_badge)
        )
        SyncStatus.ERROR -> HeaderBadgeStyle(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error,
            Icons.Filled.CloudOff,
            stringResource(R.string.cloud_sync_error_badge)
        )
        SyncStatus.LOCAL_ONLY -> HeaderBadgeStyle(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outlineVariant,
            Icons.Filled.CloudOff,
            stringResource(R.string.offline_badge)
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.persona == AppPersona.ADMIN && uiState.adminSection != AdminSection.DASHBOARD) {
                        IconButton(
                            onClick = onBackToDashboard,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("back_to_dashboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.section_dashboard)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.pbs_logo),
                                contentDescription = stringResource(R.string.brand_title),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = if (uiState.persona == AppPersona.ADMIN && uiState.adminSection != AdminSection.DASHBOARD) {
                                stringResource(uiState.adminSection.titleRes)
                            } else {
                                stringResource(R.string.brand_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val roleTag = if (uiState.authSession.role == AuthRole.ADMIN) {
                            "Admin · ${uiState.authSession.displayName}"
                        } else {
                            "Employee · ${uiState.authSession.identifier}"
                        }
                        Text(
                            text = roleTag,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = badgeContainer,
                        contentColor = badgeContent,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, badgeBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(badgeIcon, null, modifier = Modifier.size(13.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.auth_logout_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private data class HeaderBadgeStyle(
    val container: Color,
    val content: Color,
    val border: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val text: String
)

@Composable
private fun SectionIcon(section: AdminSection) {
    val icon = when (section) {
        AdminSection.DASHBOARD -> Icons.Filled.Dashboard
        AdminSection.ATTENDANCE -> Icons.Filled.EditCalendar
        AdminSection.EMPLOYEES -> Icons.Filled.Group
        AdminSection.EXPENSES -> Icons.AutoMirrored.Filled.ReceiptLong
        AdminSection.SALARY -> Icons.Filled.Payments
        AdminSection.APPROVALS -> Icons.Filled.Approval
        AdminSection.REPORTS -> Icons.Filled.Assessment
        AdminSection.SETTINGS -> Icons.Filled.Settings
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(section.titleRes)
    )
}
