package com.lorranamorim.playmix.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.theme.GoldBrush
import com.lorranamorim.playmix.ui.theme.PM

/** Foto redonda do perfil; sem foto, mostra a inicial do nome (ou um ícone). */
@Composable
fun ProfileAvatar(vm: AppViewModel, size: Dp, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsStateWithLifecycle()
    val s = data.settings
    val context = LocalContext.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GoldBrush)
            .border(1.5.dp, Brush.linearGradient(listOf(PM.GoldLight, Color.White.copy(alpha = 0.3f), PM.GoldDeep)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (s.profilePhotoVersion > 0L) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(vm.profilePhotoFile)
                    .memoryCacheKey("profile_${s.profilePhotoVersion}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (s.profileName.isNotBlank()) {
            Text(
                s.profileName.trim().first().uppercase(),
                color = PM.Black,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Icon(Icons.Filled.Person, "Perfil", tint = PM.Black, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
fun ProfileDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(data.settings.profileName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setProfilePhoto(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PM.Surface,
        title = { Text("Seu perfil") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.clickable { pick() }) {
                    ProfileAvatar(vm, 112.dp)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(PM.SurfaceHigh).border(1.dp, PM.Gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CameraAlt, "Trocar foto", tint = PM.Gold, modifier = Modifier.size(18.dp)) }
                }
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = pick) { Text("Escolher foto", color = PM.Gold) }
                    if (data.settings.profilePhotoVersion > 0L) {
                        TextButton(onClick = { vm.removeProfilePhoto() }) { Text("Remover", color = PM.TextDim) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text("Nome do perfil") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PM.Gold, focusedLabelColor = PM.Gold, cursorColor = PM.Gold),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.setProfileName(name); onDismiss() }) { Text("Salvar", color = PM.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
