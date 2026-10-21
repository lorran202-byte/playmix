package com.lorranamorim.playmix.ui

import android.Manifest
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import com.lorranamorim.playmix.ui.components.GlassRim
import com.lorranamorim.playmix.ui.components.rememberCoverColor
import com.lorranamorim.playmix.ui.theme.LocalAmbient
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.ui.components.TrackMenuSheet
import com.lorranamorim.playmix.ui.screens.CollectionScreen
import com.lorranamorim.playmix.ui.screens.HomeScreen
import com.lorranamorim.playmix.ui.screens.LibraryScreen
import com.lorranamorim.playmix.ui.screens.MiniPlayer
import com.lorranamorim.playmix.ui.screens.NowPlayingScreen
import com.lorranamorim.playmix.ui.screens.SearchScreen
import com.lorranamorim.playmix.ui.screens.SettingsScreen
import com.lorranamorim.playmix.ui.theme.GoldBrush
import com.lorranamorim.playmix.ui.theme.PM

/** Ações comuns que as telas chamam. */
class Nav(
    val open: (PlayContext) -> Unit,
    val trackMenu: (Track, String?) -> Unit,
    val settings: () -> Unit,
    val back: () -> Unit,
)

fun NavController.openContext(ctx: PlayContext) {
    navigate("collection/${ctx.type.name}/${Uri.encode(ctx.id)}")
}

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun PlayMixRoot(vm: AppViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[audioPermission()] == true
    }
    val perms = remember {
        buildList {
            add(audioPermission())
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    LaunchedEffect(granted) { if (granted) vm.refreshLibrary() }

    if (!granted) {
        PermissionScreen { launcher.launch(perms) }
        return
    }
    MainScaffold(vm)
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PM.Bg).padding(PM.s8),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).background(GoldBrush, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = PM.Black, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(PM.s6))
        Text("Suas músicas, do seu jeito", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(PM.s3))
        Text(
            "O PlayMix precisa de permissão para encontrar as músicas guardadas no seu celular.",
            style = MaterialTheme.typography.bodyLarge, color = PM.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PM.s6))
        Button(onClick = onAsk, colors = ButtonDefaults.buttonColors(containerColor = PM.Gold, contentColor = PM.Black)) {
            Text("Permitir acesso")
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val player by vm.player.collectAsStateWithLifecycle()
    val hazeState = remember { HazeState() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Pair<Track, String?>?>(null) }

    val actions = remember(nav) {
        Nav(
            open = { ctx -> expanded = false; nav.openContext(ctx) },
            trackMenu = { t, pl -> menu = t to pl },
            settings = { nav.goTab("settings") },
            back = { nav.popBackStack() },
        )
    }

    BackHandler(enabled = expanded) { expanded = false }

    // cor ambiente: vem da capa que está tocando e tinge o fundo do app todo
    val ambientRaw = rememberCoverColor(player.current?.mediaMetadata?.artworkUri, PM.GoldDeep)
    val ambient by animateColorAsState(ambientRaw, tween(900), label = "ambiente")

    CompositionLocalProvider(LocalAmbient provides ambient) {
    Box(Modifier.fillMaxSize().background(PM.Bg)) {
        val hasPlayer = player.current != null
        val bottomSpace = if (hasPlayer) 168.dp else 104.dp

        // tudo aqui dentro é o que o vidro da barra desfoca (fundo ambiente + telas)
        Box(Modifier.fillMaxSize().haze(hazeState)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to ambient.copy(alpha = 0.55f), 0.45f to ambient.copy(alpha = 0.12f), 1f to PM.Bg),
            ),
        )
        NavHost(
            nav, startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            composable("home") { HomeScreen(vm, actions, bottomSpace) }
            composable("search") { SearchScreen(vm, actions, bottomSpace) }
            composable("library") { LibraryScreen(vm, actions, bottomSpace) }
            composable("settings") { SettingsScreen(vm, actions, bottomSpace) }
            composable(
                "collection/{type}/{id}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val type = runCatching { ContextType.valueOf(entry.arguments?.getString("type") ?: "") }.getOrNull()
                val id = entry.arguments?.getString("id")?.let { Uri.decode(it) } ?: ""
                if (type != null) CollectionScreen(vm, actions, type, id, bottomSpace)
            }
        }
        }

        // véu escuro embaixo para a barra flutuante ficar legível sobre qualquer conteúdo
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomSpace + 24.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, PM.Bg.copy(alpha = 0.55f), PM.Bg.copy(alpha = 0.85f)))),
        )

        // barra flutuante de vidro (liquid glass): mini player + abas + busca
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasPlayer) MiniPlayer(vm, hazeState, onExpand = { expanded = true })
            FloatingNavBar(nav, hazeState)
        }

        AnimatedVisibility(
            visible = expanded && hasPlayer,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            NowPlayingScreen(
                vm,
                onCollapse = { expanded = false },
                onTrackMenu = { t -> menu = t to null },
                onOpen = { ctx -> expanded = false; nav.openContext(ctx) },
            )
        }

        menu?.let { (t, pl) ->
            TrackMenuSheet(
                track = t,
                vm = vm,
                playlistId = pl,
                onDismiss = { menu = null },
                onGoAlbum = { id -> actions.open(PlayContext(ContextType.ALBUM, id.toString(), t.album)) },
                onGoArtist = { name -> actions.open(PlayContext(ContextType.ARTIST, name, name)) },
            )
        }
    }
    }
}

/** Pílula de vidro com as abas + botão redondo de busca ao lado, como nos players "liquid glass". */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun FloatingNavBar(nav: NavController, haze: HazeState) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Triple("home", "Início", Icons.Filled.Home to Icons.Outlined.Home),
        Triple("library", "Biblioteca", Icons.Filled.LibraryMusic to Icons.Outlined.LibraryMusic),
        Triple("settings", "Ajustes", Icons.Filled.Settings to Icons.Outlined.Settings),
    )
    val selectedIdx = tabs.indexOfFirst { it.first == route }
    val pill = RoundedCornerShape(50)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            Modifier.weight(1f).height(64.dp)
                .clip(pill)
                .hazeChild(haze, style = HazeMaterials.thin(Color(0xFF1C1C1F)))
                .border(0.8.dp, GlassRim, pill)
                .padding(5.dp),
        ) {
            val slot = maxWidth / tabs.size
            // "bolha" que desliza até a aba escolhida
            val x by animateDpAsState(
                slot * selectedIdx.coerceAtLeast(0),
                spring(dampingRatio = 0.72f, stiffness = 420f), label = "bolha",
            )
            if (selectedIdx >= 0) {
                Box(
                    Modifier.offset(x = x).width(slot).fillMaxHeight()
                        .clip(pill)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), pill),
                )
            }
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, (r, label, icons) ->
                    val on = i == selectedIdx
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(pill).clickable { nav.goTab(r) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (on) icons.first else icons.second, label, tint = if (on) PM.Gold else PM.Text, modifier = Modifier.size(24.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) PM.Text else PM.TextDim)
                    }
                }
            }
        }
        val searchOn = route == "search"
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .hazeChild(haze, style = HazeMaterials.thin(Color(0xFF1C1C1F)))
                .background(if (searchOn) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .border(0.8.dp, GlassRim, CircleShape)
                .clickable { nav.goTab("search") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Search, "Buscar", tint = if (searchOn) PM.Gold else PM.Text, modifier = Modifier.size(28.dp)) }
    }
}

private fun NavController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

