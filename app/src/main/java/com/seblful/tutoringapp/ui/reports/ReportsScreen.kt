package com.seblful.tutoringapp.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.reports_title)) }) },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(stringResource(R.string.reports_empty), Modifier.padding(padding))
            return@Scaffold
        }

        // Resolve labels here (composable scope) so the LazyListScope block below stays string-free.
        val totalIncomeLabel = stringResource(R.string.reports_total_income)
        val byMonthTitle = stringResource(R.string.reports_by_month)
        val bySubjectTitle = stringResource(R.string.reports_by_subject)
        val byStudentTitle = stringResource(R.string.reports_by_student)
        val debtsTitle = stringResource(R.string.reports_debts)
        val noDebts = stringResource(R.string.reports_no_debts)
        val totalOutstandingLabel = stringResource(R.string.reports_total_outstanding)
        val errorColor = MaterialTheme.colorScheme.error
        val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TotalIncomeCard(totalIncomeLabel, state.totalIncome.format()) }

            section(byMonthTitle, state.byMonth)
            section(bySubjectTitle, state.bySubject)
            section(byStudentTitle, state.byStudent)

            item { SectionHeader(debtsTitle) }
            if (state.debts.isEmpty()) {
                item { Text(noDebts, color = mutedColor) }
            } else {
                item { AmountRow(totalOutstandingLabel, state.totalOutstanding.format(), bold = true) }
                items(state.debts) { line -> AmountRow(line.label, line.amount.format(), color = errorColor) }
            }
        }
    }
}

private fun LazyListScope.section(title: String, lines: List<LabeledAmount>) {
    if (lines.isEmpty()) return
    item { SectionHeader(title) }
    items(lines) { line -> AmountRow(line.label, line.amount.format()) }
}

@Composable
private fun TotalIncomeCard(label: String, amount: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(amount, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun AmountRow(
    label: String,
    amount: String,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = color,
            )
        }
        HorizontalDivider()
    }
}
