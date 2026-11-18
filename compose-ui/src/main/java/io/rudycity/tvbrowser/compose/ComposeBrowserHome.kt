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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
    private val screen = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            RudyBrowserTheme { RudyHomeContent(actions) }
        }
    }
    override fun view(): View = screen
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
        modifier = Modifier.fillMaxSize().background(Color(0xFF0C1524))
    ) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 36.dp else 24.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Rudy Browser",
                style = MaterialTheme.typography.headlineMedium,
                color = RudyColors.foreground,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(RudySpacing.sm))
            Text(
                text = "A simpler way to explore the web.",
                style = MaterialTheme.typography.bodyMedium,
                color = RudyColors.muted
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { actions.focusAddress() },
                modifier = Modifier.fillMaxWidth()
                    .height(RudyDimensions.omniboxHeight)
                    .semantics { contentDescription = "Search or enter a website address" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RudyColors.omnibox,
                    contentColor = RudyColors.foreground
                )
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(RudySpacing.sm))
                Text("Search or enter address", fontSize = 14.sp)
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Quick access",
                style = MaterialTheme.typography.titleMedium,
                color = RudyColors.foreground
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
                                .height(RudyDimensions.omniboxHeight),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RudyColors.omnibox,
                                contentColor = RudyColors.foreground
                            )
                        ) { Text(site.label, maxLines = 1, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(RudySpacing.sm))
            }
            Spacer(Modifier.height(RudySpacing.lg))
            Text(
                "Your websites open in the browser above. No account required.",
                style = MaterialTheme.typography.labelMedium,
                color = RudyColors.muted
            )
        }
    }
}
