package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AdminSection
import com.example.ui.AttendanceUiState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: AttendanceUiState,
    onSelectSection: (AdminSection) -> Unit,
    onSelectSiteFilter: (String?) -> Unit,
    onOpenAddEmployee: () -> Unit,
    onLoadSampleWorkforce: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val formattedToday = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    }
    val siteSubtitle = uiState.dashboardSiteFilter ?: stringResource(R.string.all_sites_label)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.admin_dashboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$formattedToday · $siteSubtitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.employees.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.dashboardSiteFilter == null,
                        onClick = { onSelectSiteFilter(null) },
                        label = { Text(stringResource(R.string.all_sites_label)) },
                        modifier = Modifier.testTag("dashboard_site_all")
                    )
                    uiState.siteStats.forEach { stat ->
                        FilterChip(
                            selected = uiState.dashboardSiteFilter == stat.siteName,
                            onClick = {
                                onSelectSiteFilter(
                                    if (uiState.dashboardSiteFilter == stat.siteName) null else stat.siteName
                                )
                            },
                            label = { Text(stat.siteName) }
                        )
                    }
                }
            }

            // 2x2 Tinted KPI Grid matching demo screenshots
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardKpiCard(
                        value = uiState.dashboardTotalEmployees.toString(),
                        label = stringResource(R.string.kpi_total_employees),
                        containerColor = semantic.kpiBlueContainer,
                        borderColor = semantic.kpiBlueBorder,
                        valueColor = semantic.kpiBlueValue,
                        onClick = { onSelectSection(AdminSection.EMPLOYEES) },
                        testTag = "kpi_total_employees",
                        modifier = Modifier.weight(1f)
                    )
                    DashboardKpiCard(
                        value = uiState.dashboardPresentToday.toString(),
                        label = stringResource(R.string.kpi_present_today),
                        containerColor = semantic.fullDayContainer,
                        borderColor = semantic.fullDayBorder,
                        valueColor = semantic.fullDayAccent,
                        onClick = { onSelectSection(AdminSection.ATTENDANCE) },
                        testTag = "kpi_present_today",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardKpiCard(
                        value = uiState.dashboardAbsentToday.toString(),
                        label = stringResource(R.string.kpi_absent_today),
                        containerColor = semantic.shortDayContainer,
                        borderColor = semantic.shortDayBorder,
                        valueColor = semantic.shortDayAccent,
                        onClick = { onSelectSection(AdminSection.REPORTS) },
                        testTag = "kpi_absent_today",
                        modifier = Modifier.weight(1f)
                    )
                    DashboardKpiCard(
                        value = uiState.dashboardPendingCount.toString(),
                        label = stringResource(R.string.kpi_pending),
                        containerColor = semantic.overtimeContainer,
                        borderColor = semantic.overtimeBorder,
                        valueColor = semantic.overtimeAccent,
                        onClick = { onSelectSection(AdminSection.APPROVALS) },
                        testTag = "kpi_pending",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Site-wise manpower card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("site_manpower_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.site_manpower_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (uiState.siteStats.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_manpower_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenAddEmployee,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_add_employee_button")
                            ) {
                                Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.add_employee_button))
                            }
                            OutlinedButton(
                                onClick = onLoadSampleWorkforce,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("load_sample_workforce_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.load_demo_data_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        uiState.siteStats.forEach { stat ->
                            val ratio = if (stat.totalEmployees > 0) {
                                (stat.presentToday.toFloat() / stat.totalEmployees.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.siteName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${stat.presentToday} / ${stat.totalEmployees}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    color = semantic.fullDayAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }
            }

            // Explore app sections (8-pill 2-column grid matching screenshot)
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore_sections_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.explore_sections_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val sections = AdminSection.entries
                    sections.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { section ->
                                val isActive = uiState.adminSection == section
                                SectionPillButton(
                                    label = stringResource(section.titleRes),
                                    isActive = isActive,
                                    badgeCount = if (section == AdminSection.APPROVALS && uiState.dashboardPendingCount > 0) {
                                        uiState.dashboardPendingCount
                                    } else {
                                        null
                                    },
                                    onClick = { onSelectSection(section) },
                                    testTag = "section_pill_${section.name.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    value: String,
    label: String,
    containerColor: Color,
    borderColor: Color,
    valueColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 104.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 30.sp
                ),
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionPillButton(
    label: String,
    isActive: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (badgeCount != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
