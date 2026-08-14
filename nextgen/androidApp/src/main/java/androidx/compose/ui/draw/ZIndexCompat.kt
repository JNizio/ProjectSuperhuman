package androidx.compose.ui.draw

import androidx.compose.ui.Modifier

/**
 * Compatibility shim for the Trudy voice overlay on the Compose version used by this app.
 * The overlay children are already declared after the main content inside the parent Box,
 * so their natural draw order is sufficient on this target. Keeping this modifier as a
 * no-op preserves the intended layering without introducing a dependency on a zIndex API
 * that is not available from androidx.compose.ui.draw in the current dependency set.
 */
fun Modifier.zIndex(@Suppress("UNUSED_PARAMETER") zIndex: Float): Modifier = this
