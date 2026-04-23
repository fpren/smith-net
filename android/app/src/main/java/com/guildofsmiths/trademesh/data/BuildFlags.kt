package com.guildofsmiths.trademesh.data

/**
 * Compile-time feature flags. Flip and rebuild; no runtime toggle.
 */
object BuildFlags {
    /**
     * When true, repositories seed mock jobs/crew/messages on first launch.
     * Keep false for beta/production builds; flip to true when recording demos.
     */
    const val SEED_DEMO_DATA = false
}
