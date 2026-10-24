package jp.linkserver.projectgoldy

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.compose.UpdateOverviewScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var model: FixtureUpdateViewModel
        private set
    private lateinit var updateModel: DemoUpdateViewModel
    override fun onResume() {
        super.onResume()
        if (::updateModel.isInitialized) updateModel.refreshPermission()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[FixtureUpdateViewModel::class.java]
        val live = ViewModelProvider(this)[LiveDemoViewModel::class.java]
        val update = ViewModelProvider(this)[DemoUpdateViewModel::class.java]
        updateModel = update
        setContent { SampleApp(model, live, update,
            onOpenUrl = { url ->
                try {
                    val uri = java.net.URI(url)
                    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && url.none { it.isISOControl() })
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) { update.navigationFailed() }
            },
            onInstall = { lifecycleScope.launch { update.install(::startActivity) } },
            onResumePending = { request -> lifecycleScope.launch { update.resumePending(request, ::startActivity) } },
            onPermission = {
                try { startActivity(update.settingsIntent()) } catch (_: Exception) { update.navigationFailed() }
            }) }
    }
}

@Composable
internal fun SampleApp(
    model: FixtureUpdateViewModel,
    live: LiveDemoViewModel? = null,
    update: DemoUpdateViewModel? = null,
    onOpenUrl: (String) -> Unit = {},
    onInstall: () -> Unit = {},
    onResumePending: (jp.linkserver.goldy.InstallRequest) -> Unit = {},
    onPermission: () -> Unit = {}
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val preferences = remember(context) { DemoAppearancePreferences(context) }
    var appearance by remember(preferences) { mutableStateOf(preferences.load()) }
    var stylePicker by rememberSaveable { mutableStateOf(false) }
    fun changeAppearance(value: DemoAppearance) {
        appearance = value
        preferences.save(value)
    }
    val content: @Composable () -> Unit = {
        Surface {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("UI Showcase", "Live Demo", "Update Demo", "Style reference").forEachIndexed { index, name ->
                        FilterChip(mode == index, { mode = index }, { Text(name) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { stylePicker = true }, label = { Text("Style: ${appearance.style.label}") },
                        modifier = Modifier.testTag("demo-style-picker"))
                    FilterChip(appearance.dark, { changeAppearance(appearance.copy(dark = !appearance.dark)) }, { Text("Dark") })
                    FilterChip(appearance.dynamic && appearance.style != DemoStyle.Nothing,
                        { changeAppearance(appearance.copy(dynamic = !appearance.dynamic)) }, { Text("Dynamic") },
                        enabled = Build.VERSION.SDK_INT >= 31 && appearance.style != DemoStyle.Nothing)
                    FilterChip(appearance.customFont, { changeAppearance(appearance.copy(customFont = !appearance.customFont)) }, { Text("Custom font") })
                }
                if (mode == 0) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenarioNames.forEachIndexed { index, label -> FilterChip(model.scenario == index, { model.select(index) }, { Text(label) }) }
                }
                BackHandler { if (mode == 0) model.event = "Back requested by host" else mode = 0 }
                when (mode) {
                1 -> live?.let { LiveDemoScreen(it, onOpenUrl) }
                3 -> StyleReferenceScreen { mode = 0 }
                2 -> update?.let { UpdateDemoScreen(it, { mode = 0 }, onOpenUrl, onInstall, onResumePending, onPermission) }
                else -> UpdateOverviewScreen(model.state,
                    onBack = { model.event = "Back requested by host" },
                    onOpenUrl = { model.event = "Host received link: " + it },
                    onAction = model::action,
                    header = { Column {
                        Text("Goldy Demo · offline UI Showcase", style = MaterialTheme.typography.titleMedium)
                        Text(model.event, style = MaterialTheme.typography.labelMedium)
                    } })
                }
            }
        }
        if (stylePicker) AlertDialog(
            onDismissRequest = { stylePicker = false },
            title = { Text("UI style") },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    DemoStyle.entries.forEach { style ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("demo-style-${style.name}")
                            .selectable(selected = appearance.style == style, role = Role.RadioButton, onClick = {
                                changeAppearance(appearance.copy(style = style))
                                stylePicker = false
                            }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = appearance.style == style, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(style.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { stylePicker = false }) { Text("Close") } }
        )
    }
    DemoTheme(appearance.style, appearance.dark, appearance.dynamic, appearance.customFont, content)
}
