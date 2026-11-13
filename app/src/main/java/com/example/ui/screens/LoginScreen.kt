package com.example.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AuthSession
import com.example.data.PolicyPreferences
import com.example.ui.theme.LocalAttendanceColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    @StringRes authErrorRes: Int?,
    initialWorkspaceId: String = AuthSession.DEFAULT_WORKSPACE_ID,
    googleUserEmail: String = "",
    isAuthBusy: Boolean = false,
    onClearError: () -> Unit,
    onGoogleSignOut: () -> Unit = {},
    onAdminLogin: (mobile: String, password: String, workspaceId: String) -> Unit,
    onAdminSignUp: (name: String, mobile: String, password: String, confirmPassword: String, workspaceId: String) -> Unit,
    onEmployeeLogin: (loginId: String, password: String, workspaceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var roleTab by remember { mutableIntStateOf(0) } // 0 = Admin, 1 = Employee
    var adminSignUpMode by remember { mutableStateOf(false) }

    var workspaceId by remember(initialWorkspaceId) {
        mutableStateOf(initialWorkspaceId.ifBlank { AuthSession.DEFAULT_WORKSPACE_ID })
    }
    var adminName by remember { mutableStateOf("") }
    var adminMobile by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminConfirmPassword by remember { mutableStateOf("") }
    var adminPasswordVisible by remember { mutableStateOf(false) }

    var employeeLoginId by remember { mutableStateOf("") }
    var employeePassword by remember { mutableStateOf("") }
    var employeePasswordVisible by remember { mutableStateOf(false) }

    val semantic = LocalAttendanceColors.current
    val resolvedWorkspace = PolicyPreferences.sanitizeWorkspaceId(workspaceId)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brand Hero Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 2.dp,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.pbs_logo),
                            contentDescription = stringResource(R.string.brand_title),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = semantic.fullDayContainer,
                        contentColor = semantic.onFullDayContainer,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, semantic.fullDayBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.CloudSync, null, modifier = Modifier.size(14.dp))
                            Text(
                                text = stringResource(R.string.cloud_sync_online_badge, resolvedWorkspace),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (googleUserEmail.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.google_account_connected, googleUserEmail),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onGoogleSignOut,
                                modifier = Modifier.testTag("switch_google_account_button")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.google_signout_button), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Auth Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Role switcher: Admin | Employee
                        SecondaryTabRow(
                            selectedTabIndex = roleTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = roleTab == 0,
                                onClick = {
                                    roleTab = 0
                                    onClearError()
                                },
                                text = { Text(stringResource(R.string.auth_admin_tab)) },
                                icon = {
                                    Icon(
                                        Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("auth_tab_admin")
                            )
                            Tab(
                                selected = roleTab == 1,
                                onClick = {
                                    roleTab = 1
                                    onClearError()
                                },
                                text = { Text(stringResource(R.string.auth_employee_tab)) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Badge,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("auth_tab_employee")
                            )
                        }

                        // Workspace Sync Code field shared across Admin & Employee phones
                        OutlinedTextField(
                            value = workspaceId,
                            onValueChange = {
                                workspaceId = it.uppercase().filter { ch ->
                                    ch.isLetterOrDigit() || ch == '-' || ch == '_'
                                }.take(32)
                                onClearError()
                            },
                            label = { Text(stringResource(R.string.workspace_code_label)) },
                            supportingText = { Text(stringResource(R.string.workspace_code_hint)) },
                            leadingIcon = { Icon(Icons.Filled.Hub, null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workspace_code_input")
                        )

                        if (roleTab == 0) {
                            // Admin Login | Sign Up Pill Switcher
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AuthModePill(
                                        label = stringResource(R.string.auth_login_mode),
                                        selected = !adminSignUpMode,
                                        onClick = {
                                            adminSignUpMode = false
                                            onClearError()
                                        },
                                        testTag = "admin_mode_login",
                                        modifier = Modifier.weight(1f)
                                    )
                                    AuthModePill(
                                        label = stringResource(R.string.auth_signup_mode),
                                        selected = adminSignUpMode,
                                        onClick = {
                                            adminSignUpMode = true
                                            onClearError()
                                        },
                                        testTag = "admin_mode_signup",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (adminSignUpMode) {
                                OutlinedTextField(
                                    value = adminName,
                                    onValueChange = {
                                        adminName = it
                                        onClearError()
                                    },
                                    label = { Text(stringResource(R.string.auth_admin_name_label)) },
                                    leadingIcon = { Icon(Icons.Filled.Person, null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_signup_name_input")
                                )
                            }

                            OutlinedTextField(
                                value = adminMobile,
                                onValueChange = { input ->
                                    adminMobile = input.filter { it.isDigit() }.take(10)
                                    onClearError()
                                },
                                label = { Text(stringResource(R.string.auth_mobile_label)) },
                                leadingIcon = { Icon(Icons.Filled.PhoneAndroid, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_mobile_input")
                            )

                            OutlinedTextField(
                                value = adminPassword,
                                onValueChange = {
                                    adminPassword = it
                                    onClearError()
                                },
                                label = { Text(stringResource(R.string.auth_password_label)) },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { adminPasswordVisible = !adminPasswordVisible }) {
                                        Icon(
                                            imageVector = if (adminPasswordVisible) {
                                                Icons.Filled.VisibilityOff
                                            } else {
                                                Icons.Filled.Visibility
                                            },
                                            contentDescription = stringResource(
                                                if (adminPasswordVisible) R.string.auth_hide_password else R.string.auth_show_password
                                            )
                                        )
                                    }
                                },
                                visualTransformation = if (adminPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_password_input")
                            )

                            if (adminSignUpMode) {
                                OutlinedTextField(
                                    value = adminConfirmPassword,
                                    onValueChange = {
                                        adminConfirmPassword = it
                                        onClearError()
                                    },
                                    label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                    visualTransformation = if (adminPasswordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_confirm_password_input")
                                )
                            }

                            Text(
                                text = stringResource(R.string.auth_admin_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (authErrorRes != null) {
                                AuthErrorBanner(message = stringResource(authErrorRes))
                            }

                            Button(
                                onClick = {
                                    if (adminSignUpMode) {
                                        onAdminSignUp(
                                            adminName,
                                            adminMobile,
                                            adminPassword,
                                            adminConfirmPassword,
                                            resolvedWorkspace
                                        )
                                    } else {
                                        onAdminLogin(adminMobile, adminPassword, resolvedWorkspace)
                                    }
                                },
                                enabled = !isAuthBusy && adminMobile.isNotBlank() && adminPassword.isNotBlank() &&
                                    (!adminSignUpMode || (adminName.isNotBlank() && adminConfirmPassword.isNotBlank())),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag(
                                        if (adminSignUpMode) "admin_signup_submit_button" else "admin_login_submit_button"
                                    )
                            ) {
                                if (isAuthBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = stringResource(
                                        if (adminSignUpMode) {
                                            R.string.auth_create_account_button
                                        } else {
                                            R.string.auth_sign_in_button
                                        }
                                    )
                                )
                            }
                        } else {
                            // Employee Login
                            OutlinedTextField(
                                value = employeeLoginId,
                                onValueChange = {
                                    employeeLoginId = it
                                    onClearError()
                                },
                                label = { Text(stringResource(R.string.auth_login_id_label)) },
                                leadingIcon = { Icon(Icons.Filled.Badge, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("employee_login_id_input")
                            )

                            OutlinedTextField(
                                value = employeePassword,
                                onValueChange = {
                                    employeePassword = it
                                    onClearError()
                                },
                                label = { Text(stringResource(R.string.auth_password_label)) },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { employeePasswordVisible = !employeePasswordVisible }) {
                                        Icon(
                                            imageVector = if (employeePasswordVisible) {
                                                Icons.Filled.VisibilityOff
                                            } else {
                                                Icons.Filled.Visibility
                                            },
                                            contentDescription = stringResource(
                                                if (employeePasswordVisible) R.string.auth_hide_password else R.string.auth_show_password
                                            )
                                        )
                                    }
                                },
                                visualTransformation = if (employeePasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("employee_password_input")
                            )

                            Text(
                                text = stringResource(R.string.auth_employee_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (authErrorRes != null) {
                                AuthErrorBanner(message = stringResource(authErrorRes))
                            }

                            Button(
                                onClick = {
                                    onEmployeeLogin(employeeLoginId, employeePassword, resolvedWorkspace)
                                },
                                enabled = !isAuthBusy && employeeLoginId.isNotBlank() && employeePassword.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("employee_login_submit_button")
                            ) {
                                if (isAuthBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.auth_sign_in_button))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AuthErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
