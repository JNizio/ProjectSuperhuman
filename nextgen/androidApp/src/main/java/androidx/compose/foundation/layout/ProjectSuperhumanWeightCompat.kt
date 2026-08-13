package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier

/** Compatibility shim for source files that still import the pre-1.11 top-level weight symbol. */
fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = this
