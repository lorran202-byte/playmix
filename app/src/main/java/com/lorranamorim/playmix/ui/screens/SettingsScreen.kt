package com.lorranamorim.playmix.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.Nav
import com.lorranamorim.playmix.ui.components.SectionTitle
import com.lorranamorim.playmix.ui.theme.PM

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav, bottomSpace: Dp) {
    val data by vm.data.collectAsStateWithLifecycle()
    val folders by vm.allFolders.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addMusicFolder(uri)
    }
    var fade by remember(data.settings.fadeSeconds) { mutableFloatStateOf(data.settings.fadeSeconds.toFloat()) }

    LazyColumn(Modifier.fillMaxSize().background(PM.Bg), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(PM.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Text("Configurações", style = MaterialTheme.typography.titleLarge)
            }
        }

        item { SectionTitle("Reprodução") }
        item {
            Column(Modifier.padding(horizontal = PM.s4)) {
                Text("Transição suave entre músicas", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (fade.toInt() == 0) "Desligada: uma música emenda na outra sem silêncio"
                    else "Abaixa o fim e sobe o começo em ${fade.toInt()} s",
                    style = MaterialTheme.typography.bodyMedium, color = PM.TextDim,
                )
                Slider(
                    value = fade,
                    onValueChange = { fade = it },
                    onValueChangeFinished = { vm.updateSettings { s -> s.copy(fadeSeconds = fade.toInt()) } },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = PM.Gold, activeTrackColor = PM.Gold, inactiveTrackColor = PM.SurfaceHigh),
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.updateSettings { s -> s.copy(autoEq = !s.autoEq) } }
                    .padding(horizontal = PM.s4, vertical = PM.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.GraphicEq, null, tint = PM.Gold)
                Spacer(Modifier.width(PM.s4))
                Column(Modifier.weight(1f)) {
                    Text("Equalizador automático", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ajusta o som sozinho ao gênero da música que está tocando (sertanejo, funk, rock, eletrônica…). Sem gênero no arquivo, usa uma curva equilibrada.",
                        style = MaterialTheme.typography.bodyMedium, color = PM.TextDim,
                    )
                }
                Switch(
                    checked = data.settings.autoEq, onCheckedChange = { on -> vm.updateSettings { s -> s.copy(autoEq = on) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = PM.Black, checkedTrackColor = PM.Gold),
                )
            }
        }
        item {
            SettingRow(Icons.Filled.Equalizer, "Equalizador do sistema", "Ajuste manual do aparelho (desligue o automático para usar só ele)") {
                val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                runCatching { context.startActivity(i) }.onFailure {
                    Toast.makeText(context, "Este aparelho não tem equalizador do sistema", Toast.LENGTH_SHORT).show()
                }
            }
        }

        item { SectionTitle("Biblioteca", Modifier.padding(top = PM.s4)) }
        item {
            SettingRow(
                Icons.Filled.Refresh,
                if (scanning) "Procurando músicas…" else "Procurar músicas de novo",
                "${lib.tracks.size} músicas encontradas",
            ) { vm.refreshLibrary() }
        }
        item {
            val chosen = data.settings.musicFolders.size
            SettingRow(
                Icons.Filled.FolderOpen,
                "Pastas de música",
                if (chosen == 0) "Todas as músicas do celular • toque para escolher pastas"
                else "$chosen ${if (chosen == 1) "pasta escolhida" else "pastas escolhidas"} • toque para adicionar outra",
            ) { folderPicker.launch(null) }
        }
        if (data.settings.musicFolders.isNotEmpty()) {
            item {
                SettingRow(Icons.Filled.LibraryMusic, "Usar todas as músicas do celular", "Volta a mostrar tudo, sem filtro de pasta") {
                    vm.useAllFolders()
                }
            }
        }

        item { SectionTitle("Sobre", Modifier.padding(top = PM.s4)) }
        item {
            Text(
                "PlayMix 2.0 • toca as músicas guardadas no seu celular. Letras do LRCLIB (serviço aberto e gratuito).",
                style = MaterialTheme.typography.bodySmall, color = PM.TextDim,
                modifier = Modifier.padding(horizontal = PM.s4),
            )
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PM.s4, vertical = PM.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = PM.Gold)
        Spacer(Modifier.width(PM.s4))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PM.TextDim)
        }
    }
}
