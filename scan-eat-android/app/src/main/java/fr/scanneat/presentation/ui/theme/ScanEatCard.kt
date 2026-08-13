package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * How strongly a card should announce itself relative to its neighbors —
 * the "hiérarchisation" from the app-wide polish pass: previously every card
 * on a screen got the exact same glassSheen()/border treatment regardless of
 * whether it was the one number a screen exists to show (Dashboard's calorie
 * balance, Result's score) or a minor supporting stat three scrolls down.
 *  - HERO: the single focal element on a screen — stronger glow and a
 *    visible tinted border. Use for at most one element per screen; using
 *    it everywhere defeats the point of a hierarchy.
 *  - PRIMARY: the default — matches this card primitive's original look
 *    (same edge/glow strength ScanEatCard already shipped with).
 *  - SECONDARY: supporting/minor content — quieter still.
 */
enum class CardEmphasis { HERO, PRIMARY, SECONDARY }

/**
 * User-requested: one standard glass config for every card/panel/popup in the
 * app (header/nav are the one deliberate exception - they use a real
 * backdrop blur via Haze, see FrostedGlassStyle in FloatingBars.kt, not a
 * static fill). Previously ScanEatCard's own default (~0.22-0.28 dark, tuned
 * for a translucent card floating over the screen's ambientGloom wash) and
 * every popup/dialog's own alpha (0.85-0.94, tuned for a solid, readable
 * modal) drifted independently - GlassAlertDialog wrapped ScanEatCard
 * directly and inherited its low alpha, which is what read as "too
 * transparent, not standard" for a popup. One shared constant now backs
 * both: ScanEatCard's own [ScanEatCard.color] default below, and every
 * dialog built on [GlassAlertDialog].
 */
val StandardCardAlpha: Float @Composable get() = if (isLightBackground()) 0.9f else 0.85f

/**
 * Higher-opacity variant for dialogs that render directly over the live
 * camera preview (ScanStateOverlay.kt's MedicationFound/NonConsumableFound
 * AlertDialogs) rather than over the app's own static background. Those
 * dialogs are AlertDialogs, which Compose renders in their own Android
 * PopupWindow - a separate window from the camera-preview surface behind
 * it, so [glassPopupSurface]'s real-time backdrop blur can't reach it (see
 * that function's own doc comment for why). [StandardCardAlpha] was tuned
 * assuming the blur would soften whatever's visible underneath; without it,
 * that alpha over a busy, high-contrast camera feed left the dialog's own
 * text unreadable - user-reported. Near-opaque instead, since translucency
 * with no blur just means "camera image showing through," not "glass."
 */
val CameraOverlayDialogAlpha: Float @Composable get() = if (isLightBackground()) 0.97f else 0.96f

// internal (not private) so a card that can't use ScanEatCard directly - e.g.
// CalorieBalanceCard, which overlays a streak badge on the outer Box via
// BoxScope.align, a slot ScanEatCard's content: ColumnScope.() -> Unit
// doesn't expose - can still render at the HERO tier without re-declaring
// (and risking drifting from) these same numbers as separate literals.
internal data class GlassSpec(val glowAlpha: Float, val edgeAlpha: Float, val elevation: Dp)
internal val HeroGlassSpec      = GlassSpec(glowAlpha = 0.12f, edgeAlpha = 0.34f, elevation = 10.dp)
private val PrimaryGlassSpec   = GlassSpec(glowAlpha = 0.06f, edgeAlpha = 0.16f, elevation = 6.dp)
private val SecondaryGlassSpec = GlassSpec(glowAlpha = 0.03f, edgeAlpha = 0.10f, elevation = 3.dp)

// User-reported: the original post-it treatment (solid color fill + a small
// independent rotation per card) made adjacent cards visually touch/overlap
// in a Column ("certaine carte ce touche et ce superpose") - rotation
// doesn't change a composable's LAYOUT bounds, only its drawn appearance, so
// a rotated card's corners can extend past the flat rectangular space the
// layout system still reserves for it, into its neighbor's space. Replaced
// per explicit follow-up instruction: "remplace les carte post-it par des
// carte dessiné au stylo... doivent être superposé et bougé en même temps
// que le background, comme si écrit sur un cahier" - cards are now
// axis-aligned (removes the rotation that caused the overlap bug as a side
// effect) with a hand-drawn pen/sketch border instead of a solid post-it
// fill, and a paper-toned (not opaque) interior so they read as part of the
// page rather than separate floating objects on top of it.
internal val NotebookPostItColors = listOf(NotebookPostItPink, NotebookPostItGold, NotebookPostItSky, NotebookPostItGreen)

/**
 * A hand-rolled `Surface(...)` card (one that can't use [ScanEatCard]
 * directly - e.g. it overlays a badge via `BoxScope.align`, a slot
 * [ScanEatCard]'s `content: ColumnScope.() -> Unit` doesn't expose, the
 * same reason [HeroGlassSpec] above is `internal`) can call this to get the
 * same sketched-card treatment [ScanEatCard] itself applies, instead of
 * re-deriving it or - the previous state for every `Surface(...)` card in
 * the app - silently keeping the glass look under Notebook theme while
 * every [ScanEatCard]-based card around it changed. User-reported: "toutes
 * les cartes n'ont pas été remplacées" - this is the fix for hand-rolled
 * cards specifically; [ScanEatCard]-based ones were already covered.
 */
@Composable
internal fun rememberNotebookPostItStyle(baseShape: Shape): NotebookPostItStyle? {
    if (LocalThemeName.current != "notebook") return null
    val penColor = remember { NotebookPostItColors.random() }
    return NotebookPostItStyle(penColor, RoundedCornerShape(6.dp), 0f)
}
internal data class NotebookPostItStyle(val color: Color, val shape: Shape, val rotationDegrees: Float)

/** Which real pen-drawn box asset [Modifier.notebookPenBorder] stretches over a card. */
enum class NotebookBoxAsset { LARGE, SMALL }

/**
 * Draws a hand-sketched rounded-rectangle outline using a real scanned
 * pen-drawn box asset (cropped from a user-supplied hand-drawn doodle pack
 * and thresholded to an ink-alpha PNG) instead of a procedurally-jittered
 * path. User-reported: "le stylo n'a aucune texture, aucune irrégularités,
 * et les case trop parfaite" - the old two-pass whole-path-translate
 * sketch moved the same perfectly smooth rounded-rect outline a pixel or
 * two, which reads as jittered but never as an actual pen stroke (no
 * varying line weight, no real wobble). This stretches the real asset -
 * which has genuine pressure variation and hand wobble baked in - to the
 * card's bounds and tints it per-card via [color], with a second
 * smaller/rotated copy layered underneath for depth. `seed` picks the
 * second copy's jitter/rotation so it stays stable across recompositions
 * for the same card instance.
 *
 * User-requested two distinct source doodles depending on card size: the
 * wide double-line box (`notebook_pen_box_large`) for full-width cards
 * ([ScanEatCard]/BioCard), and the tighter, more square scribble box
 * (`notebook_pen_box_small`) for square-ish tiles ([FeatureTile]) - each
 * doodle's own proportions and line character were drawn for that shape,
 * so stretching the wide one over a square tile (or vice versa) distorts
 * the linework rather than just resizing it.
 */
@Composable
fun Modifier.notebookPenBorder(color: Color, seed: Int, strokeWidth: Dp = 2.dp, asset: NotebookBoxAsset = NotebookBoxAsset.LARGE): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val assetRes = when (asset) {
        NotebookBoxAsset.LARGE -> fr.scanneat.R.drawable.notebook_pen_box_large
        NotebookBoxAsset.SMALL -> fr.scanneat.R.drawable.notebook_pen_box_small
    }
    val boxBitmap = remember(assetRes) {
        android.graphics.BitmapFactory.decodeResource(context.resources, assetRes)
            .asImageBitmap()
    }
    val rng = remember(seed) { Random(seed) }
    val backRotation = remember(seed) { (rng.nextFloat() - 0.5f) * 3f }
    val backInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 3.dp.toPx() }
    return this.drawWithContent {
        drawContent()
        // Second, slightly smaller/rotated pass underneath reads as a
        // "second pen pass" the same way the old two-path version did,
        // now carrying real ink texture instead of a clean line twice.
        rotate(degrees = backRotation, pivot = center) {
            drawImage(
                image = boxBitmap,
                dstOffset = IntOffset(backInsetPx.toInt(), backInsetPx.toInt()),
                dstSize = IntSize((size.width - backInsetPx * 2).toInt().coerceAtLeast(1), (size.height - backInsetPx * 2).toInt().coerceAtLeast(1)),
                colorFilter = ColorFilter.tint(color.copy(alpha = 0.35f), BlendMode.SrcIn),
            )
        }
        drawImage(
            image = boxBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = ColorFilter.tint(color.copy(alpha = 0.85f), BlendMode.SrcIn),
        )
    }
}

/**
 * The app's one card primitive — glassSheen() top-light + hairline edge over
 * a fill, 16dp corners by default. Generalizes the pattern BioCard() already
 * proved out, so a hand-rolled `Surface(...)` doesn't need to be re-derived
 * (and its glassSheen/radius drifted) on every new screen. glassSheen is on
 * by default — the app's one distinctive surface treatment should be the
 * default, not a per-screen coin flip.
 *
 * Frosted-glass + hierarchy upgrade (app-wide polish pass):
 *  - [color] defaults to a translucent fill so a screen's own ambient
 *    background wash (see [ambientGloom]) bleeds through — this is what
 *    reads as "frosted glass over an atmosphere" rather than a flat tinted
 *    rectangle. Existing call sites that pass an explicit [color] are
 *    unaffected.
 *    User-reported correction (real-device screenshots, Dashboard): an
 *    earlier pass dropped this to 0.24 dark-theme alpha reasoning that 0.42
 *    "dominated the blend" against ambientGloom's ~7-10% glow blobs - but at
 *    0.24, next to the app's actual (mostly static, not glowing) background,
 *    cards read as "almost inseparable" from it instead of a distinct
 *    surface. Raised back to 0.4 - still meaningfully translucent (nowhere
 *    near the old fully-opaque baseline), paired with PrimaryGlassSpec's new
 *    subtle border above so the fill difference isn't the only thing
 *    carrying the card's edge.
 *  - [emphasis]/[accent] pick which [CardEmphasis] tier this card renders at
 *    and which hue its glow/border echo — default (PRIMARY, white accent)
 *    reproduces this primitive's original look plus the new subtle layers,
 *    so no existing call site needs to change to keep working.
 *  - [onClick], when non-null, makes the card tappable and applies
 *    [pressScale] alongside the tap ripple — both share one interaction
 *    source, which a caller-supplied `Modifier.clickable` on [modifier]
 *    couldn't give pressScale access to (it owns the ripple internally).
 *    A caller that needs a tappable card should use this instead of adding
 *    its own `Modifier.clickable`.
 */
@Composable
fun ScanEatCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius.CARD),
    // design-aesthetic-audit: Light theme's SurfaceVariant (#F0E7E0) sits only ~1-3
    // RGB units from its own Background (#F6F1EC) - composited at the 0.24 alpha
    // tuned against Dark/OLED (where SurfaceVariant contrasts strongly with a
    // near-black Background), the card's own fill was imperceptible in Light theme.
    // With no visible fill, the card never read as one whole shape - only its
    // shadowElevation shadow (which DOES have real contrast against a light
    // background) showed up, as a disconnected rectangle instead of a filled card.
    // User-requested: one standard glass config app-wide (see StandardCardAlpha's
    // own doc comment) - previously tuned independently as a more-translucent
    // "floats over the ambient wash" look, now the same near-opaque fill every
    // dialog/popup already uses.
    color: Color = SurfaceVariant.copy(alpha = StandardCardAlpha),
    contentPadding: PaddingValues = PaddingValues(Spacing.L),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    emphasis: CardEmphasis = CardEmphasis.PRIMARY,
    accent: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = when (emphasis) {
        CardEmphasis.HERO      -> HeroGlassSpec
        CardEmphasis.PRIMARY   -> PrimaryGlassSpec
        CardEmphasis.SECONDARY -> SecondaryGlassSpec
    }
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val isNotebook = LocalThemeName.current == "notebook"
    // Rolled once per card instance (see NotebookPostItColors' own doc
    // comment above) and held stable across recomposition via `remember`,
    // so a card doesn't visibly redraw its sketch or re-pick its pen color
    // on every recompose - only a fresh composition (e.g. scrolling a
    // LazyColumn item back into existence) rerolls it.
    val penColor = if (isNotebook) remember { NotebookPostItColors.random() } else Color.Unspecified
    val sketchSeed = if (isNotebook) remember { Random.nextInt() } else 0
    val notebookShape = if (isNotebook) RoundedCornerShape(6.dp) else shape
    Box(
        modifier.fillMaxWidth()
            .glassSheen(
                edgeAlpha = if (isNotebook) 0f else spec.edgeAlpha,
                shape = notebookShape,
                glowTint = accent,
                glowAlpha = if (isNotebook) 0f else spec.glowAlpha,
            ),
    ) {
        // Simplified to match FloatingTopBar/FloatingBars' structure exactly
        // (single Surface, plain untinted .shadow(), .clip(), fill via the
        // Surface's own color) after the previous multi-layer version (a
        // separate offset+blur shadow Box, a separate flat-fill Box, a
        // tinted Modifier.shadow, a radial-vignette drawWithContent) kept
        // producing stray rectangle artifacts on real devices — MIUI in
        // particular rendered the tinted shadow as a solid, hard-edged grey
        // box instead of a soft shadow. Headers never had this problem
        // because they never carried those extra layers; cards now don't
        // either. Trade-off: cards lose the directional-shadow/vignette look
        // and always show a neutral shadow, same as the header chrome.
        //
        // Notebook theme: no independent rotation (that was the root cause
        // of cards visually touching/overlapping their neighbors - see
        // NotebookPostItColors' own doc comment), a paper-toned near-
        // transparent interior instead of an opaque fill (glassSheen
        // disabled above via alpha=0f - a frosted-glass sheen doesn't
        // belong on paper either), and notebookPenBorder draws a hand-
        // sketched outline on top instead of a solid post-it block, so the
        // card reads as written directly on the page rather than a
        // separate object floating over it.
        Surface(
            modifier = Modifier.fillMaxWidth()
                .shadow(elevation = if (isNotebook) 1.dp else spec.elevation, shape = notebookShape)
                .clip(notebookShape)
                .then(if (isNotebook) Modifier.notebookPenBorder(penColor, sketchSeed) else Modifier)
                .then(
                    if (onClick != null)
                        Modifier.pressScale(interactionSource)
                            .clickable(interactionSource = interactionSource, indication = indication, onClick = onClick)
                    else Modifier
                ),
            shape = notebookShape,
            color = if (isNotebook) NotebookPaper.copy(alpha = 0.4f) else color,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
