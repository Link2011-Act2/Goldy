package example.jitpackconsumer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable fun HostTheme(content: @Composable () -> Unit) { MaterialExpressiveTheme(content = content) }
