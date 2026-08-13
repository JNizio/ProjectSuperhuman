package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

/**
 * Optional scientific-context seam. Implementations may adapt the existing ScientificEngine, but
 * scientific context remains a separate evidence class and is never merged into personal confidence.
 */
interface TrudyScientificContextProvider {
    suspend fun context(domains: List<HealthDomain>): List<TrudyScientificContext>
}
