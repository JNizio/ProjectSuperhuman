package com.projectsuperhuman.next.trudy

internal object TrudyEmotionalResponseComposer {
    fun trend(question: String, comparison: TrudyBaselineComparison): String? =
        TrudyEmotionalTrendResponse.compose(question, comparison)

    fun association(question: String, association: TrudyAssociationResult): String? =
        TrudyEmotionalAssociationResponse.compose(question, association)

}
