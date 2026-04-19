package com.guildofsmiths.trademesh.ui.dashboard

import com.guildofsmiths.trademesh.data.UserRole

/**
 * Identifies a dashboard module. Each module is a composable card
 * rendered by DashboardScreen based on role.
 */
enum class DashboardModule {
    HEADER,
    MESSAGE_STRIP,
    MY_TASKS,           // TEAM_MEMBER: assigned tasks with clock per task
    JOBS_PANEL,         // SOLO, TEAM_LEAD, FOREMAN, GC: prioritized job list
    TEAM_PRESENCE,      // TEAM_LEAD, FOREMAN, GC: crew status panel
    DISPATCH,           // FOREMAN: unassigned jobs, quick-assign
    PROJECT_OVERVIEW,   // GC: multi-project cards
    SITE_MAP,           // FOREMAN, GC: map thumbnail
    PROGRESS,           // SOLO, FOREMAN, GC: monthly progress
    CALENDAR,           // ALL: month calendar
    QUICK_ACTIONS,      // ALL: role-specific quick actions
    ACTIVITY_LOG,       // ALL: today's activity
    FINANCIALS,         // FOREMAN, GC: revenue, payroll
    GETTING_STARTED,    // ALL: empty state for new users
    HUB_STATUS,         // FOREMAN: mesh hub stats
    AI_INBOX,           // ALL (when enabled): AI supervisor insights
}

/**
 * Returns the ordered list of dashboard modules for a given role.
 * DashboardScreen iterates this list and renders each module.
 */
fun resolveModules(role: UserRole): List<DashboardModule> = when (role) {

    UserRole.SOLO -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.JOBS_PANEL,
        DashboardModule.SITE_MAP,
        DashboardModule.GETTING_STARTED,
        DashboardModule.AI_INBOX,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
        DashboardModule.FINANCIALS,
        DashboardModule.PROGRESS,
    )

    UserRole.TEAM_MEMBER -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.AI_INBOX,
        DashboardModule.MY_TASKS,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
    )

    UserRole.TEAM_LEAD -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.AI_INBOX,
        DashboardModule.TEAM_PRESENCE,
        DashboardModule.JOBS_PANEL,
        DashboardModule.SITE_MAP,
        DashboardModule.PROGRESS,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
    )

    UserRole.FOREMAN -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.AI_INBOX,
        DashboardModule.TEAM_PRESENCE,
        DashboardModule.DISPATCH,
        DashboardModule.JOBS_PANEL,
        DashboardModule.PROJECT_OVERVIEW,
        DashboardModule.SITE_MAP,
        DashboardModule.FINANCIALS,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
        DashboardModule.HUB_STATUS,
    )

    UserRole.GENERAL_CONTRACTOR -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.AI_INBOX,
        DashboardModule.PROJECT_OVERVIEW,
        DashboardModule.DISPATCH,
        DashboardModule.TEAM_PRESENCE,
        DashboardModule.SITE_MAP,
        DashboardModule.FINANCIALS,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
    )

    UserRole.ENTERPRISE, UserRole.ADMIN -> listOf(
        DashboardModule.HEADER,
        DashboardModule.MESSAGE_STRIP,
        DashboardModule.PROJECT_OVERVIEW,
        DashboardModule.TEAM_PRESENCE,
        DashboardModule.JOBS_PANEL,
        DashboardModule.FINANCIALS,
        DashboardModule.CALENDAR,
        DashboardModule.QUICK_ACTIONS,
    )
}
