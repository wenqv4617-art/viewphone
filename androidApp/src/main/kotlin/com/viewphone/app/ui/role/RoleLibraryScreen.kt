package com.viewphone.app.ui.role

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
import androidx.compose.ui.unit.sp
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.data.Character
import com.viewphone.app.data.ServiceKind
import com.viewphone.app.data.UserProfile
import com.viewphone.app.ui.settings.Hint
import com.viewphone.app.ui.settings.NavBar
import com.viewphone.app.ui.settings.Field
import com.viewphone.app.ui.theme.VpColors
import java.util.UUID

/**
 * 角色库 = 「用户」+「角色」，两者都可分组。
 *
 * 用户明确的结构：先在这里建「用户」（你扮演的身份），再建/导入「角色」（AI）。
 */
@Composable
fun RoleLibraryScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) } // 0=用户 1=角色
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }
    var editingChar by remember { mutableStateOf<Character?>(null) }

    editingUser?.let { u ->
        UserEditScreen(vm, u) { editingUser = null }
        return
    }
    editingChar?.let { c ->
        CharacterEditScreen(vm, c) { editingChar = null }
        return
    }

    val users by vm.store.users.collectAsState()
    val characters by vm.store.characters.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(title = "角色库", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("用户", "角色").forEachIndexed { i, label ->
                val selected = tab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) VpColors.Accent else VpColors.BgSurface)
                        .clickable { tab = i }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) VpColors.OnAccent else VpColors.TextSecondary,
                    )
                }
            }
        }
        Hint(
            if (tab == 0) "「用户」是你在这台手机里扮演的身份。聊天时会以它的口吻和人设进行。"
            else "「角色」是 AI。给它写人设、绑定文本 API，之后在聊天里加进会话。"
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (tab == 0) {
                if (users.isEmpty()) Hint("还没有用户。先建一个你自己。")
                users.forEach { u ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VpColors.BgSurface)
                            .clickable { editingUser = u }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarBox(vm, u.avatarPath, 44, u.name)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                u.name.ifBlank { "未命名用户" } + if (u.isActive) "  ·  当前" else "",
                                fontWeight = FontWeight.Medium,
                                color = VpColors.TextPrimary,
                            )
                            Text(
                                u.persona.ifBlank { "未设置人设" }.replace('\n', ' ').take(26),
                                style = MaterialTheme.typography.labelSmall,
                                color = VpColors.TextSecondary,
                            )
                        }
                        if (!u.isActive) {
                            Text(
                                "设为当前",
                                color = VpColors.Accent,
                                modifier = Modifier.clickable { vm.activateUser(u.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        editingUser = UserProfile(
                            id = UUID.randomUUID().toString(),
                            createdAt = System.currentTimeMillis(),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("新建用户") }
            } else {
                if (characters.isEmpty()) Hint("还没有角色。新建一个并写好人设。")
                characters.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VpColors.BgSurface)
                            .clickable { editingChar = c }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AvatarBox(vm, c.avatarPath, 44, c.name)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.name.ifBlank { "未命名角色" } + if (c.group.isNotBlank()) "  ·  ${c.group}" else "",
                                fontWeight = FontWeight.Medium,
                                color = VpColors.TextPrimary,
                            )
                            val bound = vm.store.presetById(c.apiPresetId)?.name
                                ?: vm.store.defaultTextPreset()?.name
                                ?: "未绑定 API"
                            Text(
                                "API：$bound",
                                style = MaterialTheme.typography.labelSmall,
                                color = VpColors.TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        editingChar = Character(
                            id = UUID.randomUUID().toString(),
                            createdAt = System.currentTimeMillis(),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("新建角色") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UserEditScreen(vm: AppViewModel, initial: UserProfile, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var persona by remember { mutableStateOf(initial.persona) }
    var group by remember { mutableStateOf(initial.group) }
    var avatarPath by remember { mutableStateOf(initial.avatarPath) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importImage(uri, "user") { rel -> if (rel != null) avatarPath = rel }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(
            title = if (initial.name.isBlank()) "新建用户" else "编辑用户",
            onBack = onClose,
            action = {
                Text(
                    "保存",
                    color = VpColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.saveUser(
                            initial.copy(
                                name = name.trim(),
                                persona = persona.trim(),
                                group = group.trim(),
                                avatarPath = avatarPath,
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
            AvatarPickerRow(vm, avatarPath, onPick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            Field("名字", name) { name = it }
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("你的人设（会写进提示词的「用户」部分）") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Field("分组（可选）", group, placeholder = "例如：我 / 分身") { group = it }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                vm.deleteUser(initial.id)
                onClose()
            }) { Text("删除用户", color = VpColors.Error) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CharacterEditScreen(vm: AppViewModel, initial: Character, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var persona by remember { mutableStateOf(initial.persona) }
    var group by remember { mutableStateOf(initial.group) }
    var note by remember { mutableStateOf(initial.note) }
    var avatarPath by remember { mutableStateOf(initial.avatarPath) }
    var textPreset by remember { mutableStateOf(initial.apiPresetId) }

    val presets by vm.store.presets.collectAsState()
    val textPresets = presets.filter { it.kindEnum == ServiceKind.TEXT }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importImage(uri, "avatar") { rel -> if (rel != null) avatarPath = rel }
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
                    "保存",
                    color = VpColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.saveCharacter(
                            initial.copy(
                                name = name.trim(),
                                persona = persona.trim(),
                                group = group.trim(),
                                note = note.trim(),
                                avatarPath = avatarPath,
                                apiPresetId = textPreset,
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
            AvatarPickerRow(vm, avatarPath, onPick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            Field("名字", name) { name = it }
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("人设 / 系统提示词") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("绑定文本 API", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            if (textPresets.isEmpty()) {
                Hint("还没有「文本」类 API。去 设置 → API 配置 → 文本 建一套。")
            } else {
                textPresets.forEach { p ->
                    val selected = textPreset == p.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) VpColors.AccentSoft else VpColors.BgSurface)
                            .clickable { textPreset = if (selected) null else p.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.name.ifBlank { "未命名" },
                            color = if (selected) VpColors.Accent else VpColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            p.model.ifBlank { "未填模型" },
                            style = MaterialTheme.typography.labelSmall,
                            color = VpColors.TextSecondary,
                        )
                    }
                }
            }

            Field("分组（可选）", group, placeholder = "例如：主线 / 配角") { group = it }
            Field("备注（可选）", note) { note = it }
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
private fun AvatarPickerRow(vm: AppViewModel, avatarPath: String?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarBox(vm, avatarPath, 64, null)
        Spacer(Modifier.size(12.dp))
        TextButton(onClick = onPick) { Text(if (avatarPath == null) "选择头像" else "更换头像") }
        Hint("从系统相册选（不申请相册权限），图片存进应用私有目录，库里只记路径。")
    }
}

/** 通用头像：有图显示图，无图显示名字首字（**不用 emoji**，符合视觉约束）。 */
@Composable
fun AvatarBox(vm: AppViewModel, relativePath: String?, size: Int, name: String?) {
    val bitmap = remember(relativePath) {
        val f = vm.imageFile(relativePath) ?: return@remember null
        runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VpColors.BgSurface2),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            !name.isNullOrBlank() -> Text(
                text = name.trim().take(1),
                color = VpColors.TextSecondary,
                fontSize = (size * 0.4).sp,
            )
        }
    }
}
