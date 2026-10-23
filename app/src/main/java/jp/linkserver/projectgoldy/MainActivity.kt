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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    var dark by rememberSaveable { mutableStateOf(false) }
    var expressive by rememberSaveable { mutableStateOf(false) }
    var nothing by rememberSaveable { mutableStateOf(false) }
    var dynamic by rememberSaveable { mutableStateOf(false) }
    var customFont by rememberSaveable { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Surface {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("UI Showcase", "Live Demo", "Update Demo", "Style reference").forEachIndexed { index, name ->
                        FilterChip(mode == index, { mode = index }, { Text(name) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(dark, { dark = !dark }, { Text("Dark") })
                    FilterChip(expressive, { expressive = !expressive; nothing = false }, { Text("Expressive") })
                    FilterChip(nothing, { nothing = !nothing; expressive = false }, { Text("Nothing") })
                    FilterChip(dynamic && !nothing, { dynamic = !dynamic }, { Text("Dynamic") }, enabled = Build.VERSION.SDK_INT >= 31 && !nothing)
                    FilterChip(customFont, { customFont = !customFont }, { Text("Custom font") })
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
    }
    DemoTheme(expressive, nothing, dark, dynamic, customFont, content)
}
