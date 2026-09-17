package com.seblful.klas.ui.theme

import androidx.compose.ui.graphics.Color

// Curated Material 3 palette — a calm indigo primary with a teal accent. Chosen over the
// template purple so the app has a consistent identity (dynamic color is opt-in, see Theme.kt).

// --- Light ---
val md_primary = Color(0xFF4C5BC4)
val md_onPrimary = Color(0xFFFFFFFF)
val md_primaryContainer = Color(0xFFDFE0FF)
val md_onPrimaryContainer = Color(0xFF000F5C)

val md_secondary = Color(0xFF5A5D72)
val md_onSecondary = Color(0xFFFFFFFF)
val md_secondaryContainer = Color(0xFFDFE1F9)
val md_onSecondaryContainer = Color(0xFF171A2C)

val md_tertiary = Color(0xFF006879)
val md_onTertiary = Color(0xFFFFFFFF)
val md_tertiaryContainer = Color(0xFFA9EDFF)
val md_onTertiaryContainer = Color(0xFF001F26)

val md_error = Color(0xFFBA1A1A)
val md_onError = Color(0xFFFFFFFF)
val md_errorContainer = Color(0xFFFFDAD6)
val md_onErrorContainer = Color(0xFF410002)

/*
 * The green a **taught lesson** is said in — a role Material's scheme has no slot for, so it is carried
 * beside it (see [KlasColors]) rather than borrowed from one of the three accents.
 *
 * Borrowed is what it was: «Проведено» was `tertiary`, and the panel the schedule's swipe reveals was
 * `tertiaryContainer` — which, this palette's teal being a cyan hue, is the pale blue #A9EDFF. Blue is
 * what a lesson *still to come* is written in here (`primary`), so the gesture that marks one taught was
 * revealing the colour of one that had not been. Green is what "done" means outside this app, and it is
 * a colour nothing else in the app claims: the teal it replaces is also the individual lesson's icon
 * (`LessonKindUi`), so a taught lesson and a one-to-one lesson used to be the same colour.
 *
 * A tonal pair, because the green is used two ways: the strong tone for a *word* (the «Проведено» badge,
 * which tints its own pill from it), the container tone for a *panel* that has words on it.
 */
val md_success = Color(0xFF146C2E)
val md_successContainer = Color(0xFFA6F2B5)
val md_onSuccessContainer = Color(0xFF00210A)

/*
 * The amber a lesson **happening right now** is said in — the second role Material's scheme has no slot
 * for, carried beside it for [KlasColors]' reason rather than borrowed from an accent.
 *
 * Every accent is already spoken for, and each would say the wrong thing: `primary` is the indigo of a
 * lesson *still to come*, which is precisely the state «Идёт» has to be told apart from; `tertiary` is
 * the teal of the individual-lesson icon (`LessonKindUi`), so a one-to-one lesson and a running one would
 * be one colour; `success` is green and means *taught*, which a lesson in progress is not yet; `error`
 * red is a cancellation and a debt. Amber is the colour "live" is written in outside this app, it is
 * unclaimed inside it, and it sits between the blue of what is coming and the green of what is done — the
 * order the lesson actually moves in.
 *
 * One tone, not a pair: this is only ever a *word* (the badge tints its own pill from it), and there is
 * no panel that means "in progress" the way the swipe's panel means "taught".
 */
val md_live = Color(0xFF8B5000)

val md_background = Color(0xFFFBF8FF)
val md_onBackground = Color(0xFF1B1B21)
val md_surface = Color(0xFFFBF8FF)
val md_onSurface = Color(0xFF1B1B21)
val md_surfaceVariant = Color(0xFFE3E1EC)
val md_onSurfaceVariant = Color(0xFF46464F)
val md_outline = Color(0xFF777680)
val md_outlineVariant = Color(0xFFC7C5D0)

/*
 * The tonal ladder — the surfaces a card is drawn on, from the page upward.
 *
 * Left undefined, every one of these falls back to a Material default derived from a palette that is
 * not this one, which is why every card in the app was reaching for `surfaceVariant` and landing on
 * the same grey: a lesson row, the stat strip above it and the report section holding it were three
 * different *kinds* of thing painted one colour, so the screen had no hierarchy to read. Stated here,
 * they give one: items sit on `surfaceContainerLow`, the sections that hold them on `surfaceContainer`.
 */
val md_surfaceDim = Color(0xFFDBD9E0)
val md_surfaceBright = Color(0xFFFBF8FF)
val md_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_surfaceContainerLow = Color(0xFFF5F2FA)
val md_surfaceContainer = Color(0xFFEFEDF4)
val md_surfaceContainerHigh = Color(0xFFE9E7EF)
val md_surfaceContainerHighest = Color(0xFFE3E1E9)

// --- Dark ---
val md_primary_d = Color(0xFFBCC2FF)
val md_onPrimary_d = Color(0xFF1A2691)
val md_primaryContainer_d = Color(0xFF3340AA)
val md_onPrimaryContainer_d = Color(0xFFDFE0FF)

val md_secondary_d = Color(0xFFC3C5DD)
val md_onSecondary_d = Color(0xFF2C2F42)
val md_secondaryContainer_d = Color(0xFF424559)
val md_onSecondaryContainer_d = Color(0xFFDFE1F9)

val md_tertiary_d = Color(0xFF82D3E8)
val md_onTertiary_d = Color(0xFF003640)
val md_tertiaryContainer_d = Color(0xFF004E5C)
val md_onTertiaryContainer_d = Color(0xFFA9EDFF)

val md_error_d = Color(0xFFFFB4AB)
val md_onError_d = Color(0xFF690005)
val md_errorContainer_d = Color(0xFF93000A)
val md_onErrorContainer_d = Color(0xFFFFDAD6)

/**
 * The same green after dark: the word lightens so it reads on a dark surface, the panel darkens so a word
 * can be read on *it*. The light scheme's two tones swapped would be a bright green slab in a dark list.
 */
val md_success_d = Color(0xFF8BD99A)
val md_successContainer_d = Color(0xFF005322)
val md_onSuccessContainer_d = Color(0xFFA6F2B5)

/** The same amber after dark: lightened so the word reads on a dark surface, as [md_success_d] is. */
val md_live_d = Color(0xFFFFB86E)

val md_background_d = Color(0xFF121318)
val md_onBackground_d = Color(0xFFE3E1E9)
val md_surface_d = Color(0xFF121318)
val md_onSurface_d = Color(0xFFE3E1E9)
val md_surfaceVariant_d = Color(0xFF46464F)
val md_onSurfaceVariant_d = Color(0xFFC7C5D0)
val md_outline_d = Color(0xFF918F9A)
val md_outlineVariant_d = Color(0xFF46464F)

/** The same ladder in the dark scheme, climbing away from the page rather than towards white. */
val md_surfaceDim_d = Color(0xFF121318)
val md_surfaceBright_d = Color(0xFF38393F)
val md_surfaceContainerLowest_d = Color(0xFF0D0E13)
val md_surfaceContainerLow_d = Color(0xFF1A1B21)
val md_surfaceContainer_d = Color(0xFF1E1F25)
val md_surfaceContainerHigh_d = Color(0xFF292A2F)
val md_surfaceContainerHighest_d = Color(0xFF34343A)
