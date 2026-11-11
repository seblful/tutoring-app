package com.seblful.klas.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.FreeTier
import com.seblful.klas.domain.pro.ProFeature
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.domain.pro.ProSource
import com.seblful.klas.pro.ProOffers
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberExternalApps
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.designsystem.component.Banner
import com.seblful.klas.ui.designsystem.component.BannerKind
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.RowStart
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SettingsGroup
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Klas Pro: what it adds, its two plans at the store's prices, and buying or restoring one. A tutor who has it
 * sees why instead, and the subscription's way to the store. Opened for a [feature], it leads with that one.
 */
@Composable
fun ProScreen(feature: ProFeature?, onBack: () -> Unit, viewModel: ProViewModel = metroViewModel()) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val messages = rememberMessages()
    val apps = rememberExternalApps(messages)
    val words = mapOf(
        ProViewModel.Event.BOUGHT to stringResource(Strings.pro_bought),
        ProViewModel.Event.PENDING to stringResource(Strings.pro_pending),
        ProViewModel.Event.UNAVAILABLE to stringResource(Strings.pro_unavailable),
        ProViewModel.Event.FAILED to stringResource(Strings.pro_failed),
        ProViewModel.Event.RESTORED to stringResource(Strings.pro_restored),
        ProViewModel.Event.NOTHING_TO_RESTORE to stringResource(Strings.pro_nothing_to_restore),
        ProViewModel.Event.UNREACHABLE to stringResource(Strings.pro_unreachable),
    )
    LaunchedEffect(Unit) { viewModel.events.stream.collect { messages.show(words.getValue(it)) } }
    val failedToOpen = stringResource(Strings.student_contact_failed)

    KlasScreen(title = stringResource(Strings.pro_title), onBack = onBack, messages = messages) {
            modifier,
            bottomInset,
        ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(bottomInset).padding(bottom = Spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            when (val access = gate?.access) {
                null -> Unit
                is Access.Pro -> Owned(
                    source = access.source,
                    onManage = viewModel.manageUrl?.let { url -> { apps.open(ExternalTarget.Web(url), failedToOpen) } },
                )
                Access.Free -> Offer(feature, viewModel)
            }
        }
    }
}

/** What a free tutor is offered: the lead, what Pro adds, the plans, and restoring a purchase. */
@Composable
private fun Offer(feature: ProFeature?, viewModel: ProViewModel) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    Lead(feature)
    if (pending) {
        Banner(
            title = stringResource(Strings.pro_pending),
            detail = stringResource(Strings.pro_pending_detail),
            kind = BannerKind.Advisory,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
    }
    Features(feature)
    Plans(offers, plan, busy, onPick = viewModel::pick, onBuy = viewModel::buy, onRetry = viewModel::loadOffers)
    Text(
        stringResource(Strings.pro_free_keeps),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.gutter * 2),
    )
    KlasButton(
        onClick = viewModel::restore,
        kind = ButtonKind.Text,
        enabled = !busy,
        modifier = Modifier.padding(horizontal = Spacing.gutter).heightIn(min = MinTouchTarget),
    ) { Text(stringResource(Strings.pro_restore)) }
}

@Composable
private fun Lead(feature: ProFeature?) {
    val text = when (feature) {
        null -> stringResource(Strings.pro_lead_generic)
        ProFeature.STUDENTS -> stringResource(Strings.pro_lead_students, FreeTier.STUDENTS)
        ProFeature.GROUPS -> stringResource(Strings.pro_lead_groups)
        ProFeature.CALENDAR -> stringResource(Strings.pro_lead_calendar)
        ProFeature.DIGESTS -> stringResource(Strings.pro_lead_digests)
        ProFeature.REPORTS -> stringResource(Strings.pro_lead_reports)
        ProFeature.STATEMENTS -> stringResource(Strings.pro_lead_statements)
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.gutter),
    )
    // The free way out, said beside the paid one: a paywall that hides it is one nobody trusts.
    if (feature == ProFeature.STUDENTS) {
        Text(
            stringResource(Strings.pro_lead_students_archive),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
    }
}

/** Everything Pro adds, the one it was opened for first. */
@Composable
private fun Features(lead: ProFeature?) {
    SettingsGroup {
        (listOfNotNull(lead) + ProFeature.entries.filter { it != lead }).forEach { feature ->
            val (icon, title) = feature.row()
            KlasRow(title = stringResource(title), start = RowStart.Icon(icon))
        }
    }
}

private fun ProFeature.row(): Pair<ImageVector, StringResource> = when (this) {
    ProFeature.STUDENTS -> KlasIcons.Outlined.PersonAdd to Strings.pro_feature_students
    ProFeature.GROUPS -> KlasIcons.Outlined.Groups to Strings.pro_feature_groups
    ProFeature.CALENDAR -> KlasIcons.Outlined.CalendarMonth to Strings.pro_feature_calendar
    ProFeature.DIGESTS -> KlasIcons.Outlined.Notifications to Strings.pro_feature_digests
    ProFeature.REPORTS -> KlasIcons.Outlined.QueryStats to Strings.pro_feature_reports
    ProFeature.STATEMENTS -> KlasIcons.Outlined.Share to Strings.pro_feature_statements
}

@Composable
private fun Plans(
    offers: Loadable<ProOffers?>,
    plan: ProPlan,
    busy: Boolean,
    onPick: (ProPlan) -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
) {
    val ready = (offers as? Loadable.Ready)?.value
    if (offers is Loadable.Ready && ready.sellsNothing) {
        Banner(
            title = stringResource(Strings.pro_unreachable),
            detail = stringResource(Strings.pro_unreachable_detail),
            kind = BannerKind.Blocking,
            action = SectionAction(stringResource(Strings.pro_retry), onClick = onRetry),
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        return
    }
    SettingsGroup(title = stringResource(Strings.pro_plans)) {
        ready?.monthly?.let { price ->
            PlanRow(
                title = stringResource(Strings.pro_plan_monthly),
                supporting = listOfNotNull(
                    stringResource(Strings.pro_plan_monthly_price, price.formatted),
                    price.trialDays?.let { pluralStringResource(Plurals.pro_plan_trial, it, it) },
                ).joinToString(" · "),
                selected = plan == ProPlan.MONTHLY,
                onClick = { onPick(ProPlan.MONTHLY) },
            )
        }
        ready?.lifetime?.let { price ->
            PlanRow(
                title = stringResource(Strings.pro_plan_lifetime),
                supporting = stringResource(Strings.pro_plan_lifetime_price, price.formatted),
                selected = plan == ProPlan.LIFETIME,
                onClick = { onPick(ProPlan.LIFETIME) },
            )
        }
    }
    val buy = when {
        plan == ProPlan.LIFETIME -> Strings.pro_buy_lifetime
        ready?.monthly?.trialDays != null -> Strings.pro_buy_trial
        else -> Strings.pro_buy_monthly
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        KlasButton(
            onClick = onBuy,
            enabled = ready != null && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text(stringResource(buy)) }
        if (plan == ProPlan.MONTHLY) {
            Text(
                stringResource(Strings.pro_renews),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** No plan to show: the store could not be asked, or offers neither. */
private val ProOffers?.sellsNothing: Boolean get() = this == null || (monthly == null && lifetime == null)

/** A plan, ticked when picked: the app's tick, as a subject's on the student form. */
@Composable
private fun PlanRow(title: String, supporting: String, selected: Boolean, onClick: () -> Unit) {
    KlasRow(
        title = title,
        supporting = supporting,
        start = RowStart.Icon(
            if (selected) KlasIcons.Filled.CheckCircle else KlasIcons.Outlined.Circle,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
    )
}

@Composable
private fun Owned(source: ProSource, onManage: (() -> Unit)?) {
    val (title, detail) = when (source) {
        ProSource.EARLY_USER -> Strings.pro_have_early to Strings.pro_have_early_detail
        ProSource.MONTHLY -> Strings.pro_have_monthly to Strings.pro_have_detail
        ProSource.LIFETIME -> Strings.pro_have_lifetime to Strings.pro_have_detail
        ProSource.NO_STORE -> Strings.pro_settings_has to Strings.pro_have_detail
    }
    SettingsGroup {
        KlasRow(
            title = stringResource(title),
            supporting = stringResource(detail),
            start = RowStart.Icon(KlasIcons.Outlined.WorkspacePremium, tint = MaterialTheme.colorScheme.primary),
        )
        if (source == ProSource.MONTHLY && onManage != null) {
            KlasRow(
                title = stringResource(Strings.pro_manage),
                end = RowEnd.Action(KlasIcons.Filled.OpenInNew, stringResource(Strings.pro_manage), onManage),
            )
        }
    }
    Features(lead = null)
}
