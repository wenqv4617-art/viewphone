package com.viewphone.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewphone.app.data.ApiPreset
import com.viewphone.app.data.ApiProtocol
import com.viewphone.app.data.AppViewModel
import com.viewphone.app.data.ConnectionTestResult
import com.viewphone.app.data.ServiceKind
import com.viewphone.app.ui.theme.VpColors
import java.util.UUID

/**
 * API 配置：按四类用途分页（文本 / 语音合成 / 生图 / 向量）。
 *
 * 用户明确要求：这四类都要能配置、都能拉取模型。
 * 拉不到列表的站点（不少中转站不提供 /models）允许手填，并给出明确提示。
 */
@Composable
fun ApiConfigScreen(vm: AppViewModel, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(ServiceKind.TEXT) }
    var editing by remember { mutableStateOf<ApiPreset?>(null) }
    val presets by vm.store.presets.collectAsState()

    val draft = editing
    if (draft != null) {
        ApiPresetEditor(vm = vm, initial = draft, onClose = { editing = null })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(title = "API 配置", onBack = onBack)

        // 四类用途
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ServiceKind.entries.forEach { k ->
                val selected = kind == k
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) VpColors.Accent else VpColors.BgSurface)
                        .clickable { kind = k }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = k.label,
                        fontSize = 13.sp,
                        color = if (selected) VpColors.OnAccent else VpColors.TextSecondary,
                    )
                }
            }
        }
        Hint(kind.hint)

        val list = presets.filter { it.kindEnum == kind }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (list.isEmpty()) {
                Hint("这一类还没有配置。点下面的按钮新建一套。")
            } else {
                list.forEach { p ->
                    PresetRow(
                        name = p.name.ifBlank { "未命名" } + if (p.group.isNotBlank()) "  ·  ${p.group}" else "",
                        subtitle = "${p.protocolEnum.label} · ${p.model.ifBlank { "未填模型" }} · " +
                            p.baseUrlShort.ifBlank { "未填地址" } +
                            if (vm.hasKey(p.id)) " · 已存 Key" else " · 未填 Key",
                        onClick = { editing = p },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    editing = ApiPreset(
                        id = UUID.randomUUID().toString(),
                        name = "${kind.label} ${list.size + 1}",
                        kind = kind.name,
                        protocol = vm.store.protocolsFor(kind).firstOrNull()?.name
                            ?: ApiProtocol.OPENAI_COMPAT.name,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("新建「${kind.label}」配置") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ApiPresetEditor(vm: AppViewModel, initial: ApiPreset, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var group by remember { mutableStateOf(initial.group) }
    var protocol by remember { mutableStateOf(initial.protocolEnum) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var temperature by remember { mutableStateOf(initial.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var stream by remember { mutableStateOf(initial.stream) }
    var keyInput by remember { mutableStateOf("") }
    var protocolMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    val testing by vm.testing.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val models = vm.modelsFor(initial.id)
    val kind = initial.kindEnum
    val protocols = vm.store.protocolsFor(kind)

    fun currentDraft(): ApiPreset = initial.copy(
        name = name.trim().ifBlank { "未命名" },
        group = group.trim(),
        protocol = protocol.name,
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        temperature = temperature.toFloatOrNull() ?: 0.8f,
        maxTokens = maxTokens.toIntOrNull() ?: 2048,
        stream = stream,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VpColors.BgBase)
            .systemBarsPadding(),
    ) {
        NavBar(
            title = "${kind.label} · ${if (initial.name.isBlank()) "新建" else "编辑"}",
            onBack = onClose,
            action = {
                Text(
                    text = "保存",
                    color = VpColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.savePreset(currentDraft(), keyInput.ifBlank { null })
                        vm.clearTestResult()
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
            Field("名称", name) { name = it }
            Field("分组（可选）", group, placeholder = "例如：主力 / 备用") { group = it }
            Field(
                "Base URL",
                baseUrl,
                placeholder = when (protocol) {
                    ApiProtocol.MINIMAX_CN -> "https://api.minimax.chat/v1"
                    ApiProtocol.MINIMAX_INTL -> "https://api.minimaxi.com/v1"
                    ApiProtocol.NOVA_AI -> "https://api.novaai.xxx/v1"
                    else -> "https://api.openai.com/v1"
                },
            ) { baseUrl = it }

            Text("协议", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VpColors.BgSurface)
                        .clickable { protocolMenu = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(protocol.label, color = VpColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text("▾", color = VpColors.TextTertiaryDecorative)
                }
                DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                    protocols.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = {
                                protocol = p
                                protocolMenu = false
                            },
                        )
                    }
                }
            }
            if (protocol == ApiProtocol.GEMINI) {
                Hint("Gemini 原生协议暂未支持。多数中转站提供 OpenAI 兼容接口，换用「OpenAI 兼容」填同一地址即可。")
            }

            // 模型：可手填，也可从拉取结果里选
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    Field(
                        label = if (kind == ServiceKind.TTS) "模型 / 音色" else "模型名",
                        value = model,
                        placeholder = when (kind) {
                            ServiceKind.TEXT -> "gpt-4o-mini / claude-3-5-sonnet"
                            ServiceKind.TTS -> "speech-01-turbo 或音色 id"
                            ServiceKind.IMAGE -> "gpt-image-1 / flux-schnell"
                            ServiceKind.VECTOR -> "text-embedding-3-small"
                        },
                    ) { model = it }
                }
                if (models.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { modelMenu = true }) { Text("选择") }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m, fontSize = 13.sp) },
                                    onClick = {
                                        model = m
                                        modelMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (kind == ServiceKind.TTS) {
                Hint("MiniMax 不提供模型列表接口，这里给出的是**内置音色候选**；国内版与国际版域名不同，注意选对协议。")
            }

            Field(
                label = "API Key" + if (vm.hasKey(initial.id)) "（已存 ${vm.maskedKey(initial.id)}，留空则不改）" else "",
                value = keyInput,
                placeholder = "sk-...",
                isPassword = true,
            ) { keyInput = it }

            if (kind == ServiceKind.TEXT) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("温度", temperature, modifier = Modifier.weight(1f)) { temperature = it }
                    Field("最大 token", maxTokens, modifier = Modifier.weight(1f)) { maxTokens = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("流式输出", color = VpColors.TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = stream, onCheckedChange = { stream = it })
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    vm.savePreset(currentDraft(), keyInput.ifBlank { null })
                    vm.testConnection(currentDraft())
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (testing) "正在拉取模型…" else "测试连接 / 拉取模型") }

            testResult?.let { r ->
                when (r) {
                    is ConnectionTestResult.Success -> ResultCard(
                        title = if (r.models.isEmpty()) "连接成功" else "成功 · 拉取到 ${r.models.size} 个模型",
                        body = r.models.take(40).joinToString("\n"),
                        error = false,
                    )
                    is ConnectionTestResult.Failure -> ResultCard(
                        title = "失败",
                        body = r.message,
                        error = true,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                vm.deletePreset(initial.id)
                vm.clearTestResult()
                onClose()
            }) { Text("删除这套配置", color = VpColors.Error) }

            Hint("Key 存在本机加密存储（Android Keystore），不写进日志、不进备份明文。")
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---------------- 复用小组件 ----------------

@Composable
internal fun NavBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            fontSize = 26.sp,
            color = VpColors.Accent,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VpColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
internal fun PresetRow(name: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VpColors.BgSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(name, fontWeight = FontWeight.Medium, color = VpColors.TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VpColors.TextSecondary)
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        singleLine = !isPassword,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = VpColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultCard(title: String, body: String, error: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VpColors.BgSurface)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = if (error) VpColors.Error else VpColors.Success,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body.take(2000),
                style = MaterialTheme.typography.labelSmall,
                color = VpColors.TextSecondary,
            )
        }
    }
}
