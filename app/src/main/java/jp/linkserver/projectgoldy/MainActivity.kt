package jp.linkserver.projectgoldy

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.compose.UpdateOverviewScreen

class MainActivity : ComponentActivity() {
    lateinit var model: FixtureUpdateViewModel
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[FixtureUpdateViewModel::class.java]
        setContent { SampleApp(model) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SampleApp(model: FixtureUpdateViewModel) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var expressive by rememberSaveable { mutableStateOf(false) }
    var dynamic by rememberSaveable { mutableStateOf(false) }
    var customFont by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val typography = if (customFont) Typography().let { base -> base.copy(
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Monospace),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Monospace),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace)
    ) } else Typography()
    val content: @Composable () -> Unit = {
        Surface {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(dark, { dark = !dark }, { Text("Dark") })
                    FilterChip(expressive, { expressive = !expressive }, { Text("Expressive") })
                    FilterChip(dynamic, { dynamic = !dynamic }, { Text("Dynamic") }, enabled = Build.VERSION.SDK_INT >= 31)
                    FilterChip(customFont, { customFont = !customFont }, { Text("Custom font") })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenarioNames.forEachIndexed { index, label -> FilterChip(model.scenario == index, { model.select(index) }, { Text(label) }) }
                }
                BackHandler { model.event = "Back requested by host" }
                UpdateOverviewScreen(model.state,
                    onBack = { model.event = "Back requested by host" },
                    onOpenUrl = { model.event = "Host received link: " + it },
                    onAction = model::action,
                    header = { Text(model.event, style = MaterialTheme.typography.labelMedium) })
            }
        }
    }
    if (expressive) MaterialExpressiveTheme(colorScheme = scheme, typography = typography, content = content)
    else MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
