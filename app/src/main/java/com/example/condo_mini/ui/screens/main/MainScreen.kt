package com.example.condo_mini.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import com.example.condo_mini.ui.screens.manager.notices.ManagerNoticesRoute
import com.example.condo_mini.ui.screens.manager.occurrences.ManagerOccurrencesRoute
import com.example.condo_mini.ui.screens.manager.reservations.ManagerReservationsRoute
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.screens.bills.BillPdfRoute
import com.example.condo_mini.ui.screens.bills.BillsRoute
import com.example.condo_mini.ui.screens.notices.NoticesRoute
import com.example.condo_mini.ui.screens.occurrences.OccurrencesRoute
import com.example.condo_mini.ui.screens.parcels.ParcelsRoute
import com.example.condo_mini.ui.screens.profile.ProfileRoute
import com.example.condo_mini.ui.screens.reservations.ReservationsRoute
import java.time.LocalTime

/** Todas as abas do app; [AppTab.forRole] define quais aparecem para cada perfil. */
enum class AppTab(
    val route: String,
    val label: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // Morador
    NOTICES("notices", "Avisos", "Mural", Icons.Filled.Campaign, Icons.Outlined.Campaign),
    RESERVATIONS("reservations", "Reservas", "Reservas", Icons.Filled.EventAvailable, Icons.Outlined.EventAvailable),
    BILLS("bills", "Boletos", "Financeiro", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    PARCELS("parcels", "Encomendas", "Encomendas", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    OCCURRENCES("occurrences", "Ocorrências", "Ocorrências", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),

    // Síndico
    MANAGER_NOTICES("manager_notices", "Avisos", "Mural", Icons.Filled.Campaign, Icons.Outlined.Campaign),
    MANAGER_RESERVATIONS("manager_reservations", "Reservas", "Aprovações", Icons.Filled.EventAvailable, Icons.Outlined.EventAvailable),
    MANAGER_OCCURRENCES("manager_occurrences", "Ocorrências", "Ocorrências", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook);

    val isHome: Boolean get() = this == NOTICES || this == MANAGER_NOTICES

    companion object {
        /** Abas visíveis por perfil; a primeira é a tela inicial. */
        fun forRole(role: UserRole?): List<AppTab> = when (role) {
            UserRole.MANAGER -> listOf(MANAGER_NOTICES, MANAGER_RESERVATIONS, MANAGER_OCCURRENCES)
            else -> listOf(NOTICES, RESERVATIONS, BILLS, PARCELS, OCCURRENCES)
        }
    }
}

private const val PROFILE_ROUTE = "profile"
private const val BILL_ID_ARG = "billId"
private const val BILL_PDF_ROUTE = "bill_pdf/{$BILL_ID_ARG}"
private const val FADE_MS = 220

@Composable
fun MainScreen(
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel = viewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    // O perfil só muda com logout, que recria esta tela.
    val tabs = remember(user?.role) { AppTab.forRole(user?.role) }
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = tabs.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }
    val isProfile = currentDestination?.route == PROFILE_ROUTE
    // Telas de detalhe em tela cheia desenham o próprio cabeçalho.
    val isFullScreen = currentDestination?.route == BILL_PDF_ROUTE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullScreen) {
                MainHeader(
                    user = user,
                    tab = currentTab ?: tabs.first(),
                    isProfile = isProfile,
                    onProfileClick = { navController.navigate(PROFILE_ROUTE) { launchSingleTop = true } },
                    onBackClick = { navController.popBackStack() }
                )
            }
        },
        bottomBar = {
            if (!isProfile && !isFullScreen) {
                MainBottomBar(
                    tabs = tabs,
                    currentTab = currentTab,
                    badges = badges,
                    onTabClick = { navController.navigateToTab(it) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = tabs.first().route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(FADE_MS)) },
            exitTransition = { fadeOut(tween(FADE_MS)) },
            popEnterTransition = { fadeIn(tween(FADE_MS)) },
            popExitTransition = { fadeOut(tween(FADE_MS)) }
        ) {
            // Morador
            composable(AppTab.NOTICES.route) { NoticesRoute() }
            composable(AppTab.RESERVATIONS.route) { ReservationsRoute(snackbarHostState) }
            composable(AppTab.BILLS.route) {
                BillsRoute(
                    snackbarHostState = snackbarHostState,
                    onOpenPdf = { bill -> navController.navigate("bill_pdf/${bill.id}") }
                )
            }
            composable(BILL_PDF_ROUTE) { entry ->
                BillPdfRoute(
                    billId = entry.arguments?.getString(BILL_ID_ARG).orEmpty(),
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(AppTab.PARCELS.route) { ParcelsRoute() }
            composable(AppTab.OCCURRENCES.route) { OccurrencesRoute() }

            // Síndico
            composable(AppTab.MANAGER_NOTICES.route) { ManagerNoticesRoute() }
            composable(AppTab.MANAGER_RESERVATIONS.route) { ManagerReservationsRoute(snackbarHostState) }
            composable(AppTab.MANAGER_OCCURRENCES.route) { ManagerOccurrencesRoute(snackbarHostState) }

            composable(PROFILE_ROUTE) { ProfileRoute(onLogoutClick = onLogoutClick) }
        }
    }
}

@Composable
private fun MainHeader(
    user: User?,
    tab: AppTab,
    isProfile: Boolean,
    onProfileClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val firstName = user?.name?.substringBefore(" ").orEmpty()
    val isManager = user?.role == UserRole.MANAGER
    val (eyebrow, title) = when {
        isProfile -> "Conta" to "Meu perfil"
        tab.isHome -> {
            val hello = if (firstName.isBlank()) "Bem-vindo" else "Olá, $firstName"
            (if (isManager) "${greeting()} · Síndico" else greeting()) to hello
        }
        isManager -> "Gestão do condomínio" to tab.title
        else -> user?.unit.orEmpty() to tab.title
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = if (isProfile) 8.dp else 24.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isProfile) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        AnimatedContent(
            targetState = eyebrow to title,
            transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
            label = "header",
            modifier = Modifier.weight(1f)
        ) { (eyebrowText, titleText) ->
            Column {
                Eyebrow(eyebrowText)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!isProfile && user != null) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(48.dp)) {
                InitialsAvatar(name = user.name, size = 42.dp)
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    tabs: List<AppTab>,
    currentTab: AppTab?,
    badges: Map<AppTab, Int>,
    onTabClick: (AppTab) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
        NavigationBar(containerColor = scheme.surfaceContainerLow, tonalElevation = 0.dp) {
            tabs.forEach { tab ->
                val selected = tab == currentTab
                val count = badges[tab] ?: 0
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabClick(tab) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = scheme.tertiary, contentColor = scheme.onTertiary) {
                                        Text(if (count > 9) "9+" else "$count")
                                    }
                                }
                            }
                        ) {
                            Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null)
                        }
                    },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Ponto champanhe no item ativo, sem a "pílula" padrão do Material.
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) scheme.tertiary else Color.Transparent)
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = scheme.primary,
                        selectedTextColor = scheme.primary,
                        unselectedIconColor = scheme.outline,
                        unselectedTextColor = scheme.outline
                    )
                )
            }
        }
    }
}

@Composable
fun CondoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            actionColor = MaterialTheme.colorScheme.tertiary
        )
    }
}

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

private fun NavHostController.navigateToTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
