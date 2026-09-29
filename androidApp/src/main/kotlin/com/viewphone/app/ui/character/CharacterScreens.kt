package com.viewphone.app.ui.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.data.Character
import com.viewphone.app.ui.settings.Field
import com.viewphone.app.ui.settings.Hint
import com.viewphone.app.ui.settings.NavBar
import com.viewphone.app.ui.theme.VpColors
import java.util.UUID

/** 角色库：列表 + 新建/编辑/删除。头像是文件引用，只存路径。 */
@Composable
fun CharacterListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<Character?>(null) }
    val characters by vm.store.characters.collectAsState()

    if (editing != null) {
        CharacterEditScreen(vm = vm, initial = editing!!, onClose = { editing = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(title = "角色库", onBack = onBack)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (characters.isEmpty()) {
                Hint("还没有角色。\n新建一个：填名字和人设（系统提示词），再把 API 预设绑定给它。")
            } else {
                characters.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VpColors.BgSurface)
                            .clickable { onOpenChat(c.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(vm, c.avatarPath, size = 44)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = c.name.ifBlank { "未命名角色" },
                                fontWeight = FontWeight.Medium,
                                color = VpColors.TextPrimary,
                            )
                            Text(
                                text = if (c.persona.isBlank()) "未设置人设" else c.persona.replace('\n', ' ').take(28),
                                style = MaterialTheme.typography.labelSmall,
                                color = VpColors.TextSecondary,
                            )
                        }
                        Text(
                            text = "编辑",
                            color = VpColors.Accent,
                            modifier = Modifier.clickable { editing = c },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { editing = Character(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("新建角色") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CharacterEditScreen(vm: AppViewModel, initial: Character, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var persona by remember { mutableStateOf(initial.persona) }
    var note by remember { mutableStateOf(initial.note) }
    var avatarPath by remember { mutableStateOf(initial.avatarPath) }
    var presetId by remember { mutableStateOf(initial.apiPresetId) }

    val presets by vm.store.presets.collectAsState()

    // 系统照片选择器：不申请读相册权限（DEC：Photo Picker）
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.importAvatar(uri) { rel -> if (rel != null) avatarPath = rel }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(
            title = if (initial.name.isBlank()) "新建角色" else "编辑角色",
            onBack = onClose,
            action = {
                Text(
                    text = "保存",
                    color = VpColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.saveCharacter(
                            initial.copy(
                                name = name.trim(),
                                persona = persona.trim(),
                                note = note.trim(),
                                avatarPath = avatarPath,
                                apiPresetId = presetId,
                            )
                        )
                        onClose()
                    },
                )
            },
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm, avatarPath, size = 64)
                Spacer(Modifier.size(12.dp))
                TextButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (avatarPath == null) "选择头像" else "更换头像") }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("人设 / 系统提示词") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("绑定 API", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            if (presets.isEmpty()) {
                Hint("还没有 API 预设。先到 设置 → API 配置 里新建一套，再回来绑定。")
            } else {
                presets.forEach { p ->
                    val selected = presetId == p.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) VpColors.AccentSoft else VpColors.BgSurface)
                            .clickable { presetId = if (selected) null else p.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = p.name.ifBlank { "未命名预设" },
                            color = if (selected) VpColors.Accent else VpColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = p.model.ifBlank { "未填模型" },
                            style = MaterialTheme.typography.labelSmall,
                            color = VpColors.TextSecondary,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                vm.deleteCharacter(initial.id)
                onClose()
            }) { Text("删除角色", color = VpColors.Error) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun Avatar(vm: AppViewModel, relativePath: String?, size: Int) {
    val bitmap = remember(relativePath) {
        val f = vm.avatarFile(relativePath) ?: return@remember null
        runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VpColors.BgSurface2),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("👤", color = VpColors.TextSecondary)
        }
    }
}
