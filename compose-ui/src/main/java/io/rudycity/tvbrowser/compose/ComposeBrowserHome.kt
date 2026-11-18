package io.rudycity.tvbrowser.compose

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rudycity.tvbrowser.BrowserHome

/** Real Material 3 home surface. Does not replace or style the browsing WebView. */
class ComposeBrowserHome(activity: Activity, private val actions: BrowserHome.Actions) : BrowserHome {
    private var darkMode by androidx.compose.runtime.mutableStateOf(
        activity.getSharedPreferences("tv_browser", android.content.Context.MODE_PRIVATE)
            .getBoolean("dark_mode", true))
    private val screen = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RudyBrowserTheme(darkMode) { RudyHomeContent(actions) }
        }
    }
    override fun view(): View = screen
    override fun setDarkTheme(dark: Boolean) { darkMode = dark }
}

private data class QuickSite(val label: String, val url: String)

private val quickSites = listOf(
    QuickSite("Google", "https://www.google.com/"),
    QuickSite("YouTube", "https://www.youtube.com/"),
    QuickSite("Wikipedia", "https://www.wikipedia.org/"),
    QuickSite("Bing", "https://www.bing.com/")
)

@Composable
private fun RudyHomeContent(actions: BrowserHome.Actions) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Rudy Browser",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(RudySpacing.sm))
            Text(
                text = "A quieter way to browse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // The browser already has an omnibox. Never duplicate it in the homepage.
            Spacer(Modifier.height(28.dp))
            Text(
                "Quick access",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(RudySpacing.md))
            val columns = if (wide) 4 else 2
            quickSites.chunked(columns).forEach { group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RudySpacing.sm)
                ) {
                    group.forEach { site ->
                        FilledTonalButton(
                            onClick = { actions.openWebsite(site.url) },
                            modifier = Modifier.weight(1f)
                                .height(RudyDimensions.touchTarget)
                                .semantics { contentDescription = "Open " + site.label + " website" },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(site.label, maxLines = 1, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(RudySpacing.sm))
            }
            Spacer(Modifier.height(RudySpacing.lg))
            Text(
                "Use the address bar above to search or enter a website.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
