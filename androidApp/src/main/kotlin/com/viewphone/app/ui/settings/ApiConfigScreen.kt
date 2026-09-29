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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.viewphone.app.ui.theme.VpColors
import java.util.UUID

/**
 * API 配置：预设列表 + 编辑器 + 真实"测试连接"。
 *
 * 关键要求（用户验收清单第 3 步）：测试连接必须给出**真实结果**——
 * 成功列出模型名，失败显示 HTTP 状态与服务端返回正文，禁止只转圈。
 */
@Composable
fun ApiConfigScreen(vm: AppViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<ApiPreset?>(null) }
    val presets by vm.store.presets.collectAsState()

    if (editing != null) {
        ApiPresetEditor(
            vm = vm,
            initial = editing!!,
            onClose = { editing = null },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VpColors.BgBase)
                .systemBarsPadding(),
        ) {
            NavBar(title = "API 配置", onBack = onBack)

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (presets.isEmpty()) {
                    Hint("还没有任何 API 预设。\n点下面的按钮新建一套：填 Base URL、Key、模型名，然后点「测试连接」。")
                } else {
                    presets.forEach { p ->
                        PresetRow(
                            name = p.name.ifBlank { "未命名预设" },
                            subtitle = "${p.protocolEnum.label} · ${p.model.ifBlank { "未填模型" }} · ${p.baseUrlShort.ifBlank { "未填地址" }}" +
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
                            name = "预设 ${presets.size + 1}",
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("新建 API 预设") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ApiPresetEditor(vm: AppViewModel, initial: ApiPreset, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocolEnum) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var temperature by remember { mutableStateOf(initial.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var stream by remember { mutableStateOf(initial.stream) }
    var keyInput by remember { mutableStateOf("") }

    val testing by vm.testing.collectAsState()
    val testResult by vm.testResult.collectAsState()

    fun currentDraft(): ApiPreset = initial.copy(
        name = name.trim(),
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
            title = if (initial.name.isBlank()) "新建预设" else "编辑预设",
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
            Field("Base URL", baseUrl, placeholder = "https://api.openai.com/v1") { baseUrl = it }

            Text("协议", style = MaterialTheme.typography.labelMedium, color = VpColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiProtocol.entries.forEach { p ->
                    val selected = protocol == p
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) VpColors.Accent else VpColors.BgSurface)
                            .clickable { protocol = p }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = p.label,
                            fontSize = 13.sp,
                            color = if (selected) VpColors.OnAccent else VpColors.TextSecondary,
                        )
                    }
                }
            }
            if (protocol == ApiProtocol.GEMINI) {
                Hint("Gemini 原生协议本轮暂未支持。多数中转站提供 OpenAI 兼容接口，选「OpenAI 兼容」填同一个地址即可。")
            }

            Field("模型名", model, placeholder = "gpt-4o-mini / claude-3-5-sonnet / ...") { model = it }

            Field(
                label = "API Key" + if (vm.hasKey(initial.id)) "（已存 ${vm.maskedKey(initial.id)}，留空则不修改）" else "",
                value = keyInput,
                placeholder = "sk-...",
                isPassword = true,
            ) { keyInput = it }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("温度", temperature, modifier = Modifier.weight(1f)) { temperature = it }
                Field("最大 token", maxTokens, modifier = Modifier.weight(1f)) { maxTokens = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("流式输出", color = VpColors.TextPrimary, modifier = Modifier.weight(1f))
                Switch(checked = stream, onCheckedChange = { stream = it })
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        // 先落库，保证测试用的就是用户看到的那套配置
                        vm.savePreset(currentDraft(), keyInput.ifBlank { null })
                        vm.testConnection(currentDraft())
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (testing) "正在测试…" else "测试连接") }
            }

            testResult?.let { r ->
                when (r) {
                    is ConnectionTestResult.Success -> ResultCard(
                        title = "连接成功 · 可用模型 ${r.models.size} 个",
                        body = r.models.take(30).joinToString("\n"),
                        error = false,
                    )
                    is ConnectionTestResult.Failure -> ResultCard(
                        title = "连接失败",
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
            }) { Text("删除这套预设", color = VpColors.Error) }

            Hint("Key 保存在本机加密存储（Android Keystore），不会写进任何日志。")
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
        Spacer(Modifier.padding(start = 8.dp))
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
        Spacer(Modifier.height(6.dp))
        Text(
            text = body.take(2000),
            style = MaterialTheme.typography.labelSmall,
            color = VpColors.TextSecondary,
        )
    }
}
