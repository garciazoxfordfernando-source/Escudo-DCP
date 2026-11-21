package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface EscudoRoute

@Serializable
data object AppsRoute : EscudoRoute

@Serializable
data object WorkProfileRoute : EscudoRoute

@Serializable
data object AntivirusRoute : EscudoRoute

@Serializable
data object FirewallRoute : EscudoRoute

@Serializable
data object TrackersRoute : EscudoRoute

@Serializable
data object SettingsRoute : EscudoRoute
