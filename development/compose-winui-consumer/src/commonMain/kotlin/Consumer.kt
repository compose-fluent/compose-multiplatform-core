import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

// Compiles against only the staged Maven publications, without source-project substitution.
@Composable
fun Navigation2Consumer() {
    MaterialTheme {
        NavHost(rememberNavController(), startDestination = "home") {
            composable("home") { Text("WinUI Navigation 2") }
        }
    }
}

@Composable
fun Navigation3Consumer() {
    MaterialTheme {
        NavDisplay(
            backStack = listOf("home"),
            entryProvider = { key -> NavEntry(key) { Text("WinUI Navigation 3") } },
        )
    }
}
