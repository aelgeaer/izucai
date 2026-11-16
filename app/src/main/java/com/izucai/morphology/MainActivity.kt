package com.izucai.morphology

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izucai.morphology.data.TextNormalizer
import com.izucai.morphology.ui.AboutScreen
import com.izucai.morphology.ui.AppColors
import com.izucai.morphology.ui.HelpScreen
import com.izucai.morphology.ui.IzucaiTheme
import com.izucai.morphology.ui.SearchScreen
import com.izucai.morphology.ui.SearchViewModel
import com.izucai.morphology.ui.Speaker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        setContent {
            IzucaiTheme { App(viewModel, speaker, versionName) }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}

private val TABS = listOf("ПОИСК", "ПОМОЩЬ", "О ПРОГРАММЕ")

@Composable
private fun App(viewModel: SearchViewModel, speaker: Speaker, versionName: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ketuk bentuk kata: bacakan (TTS offline) + tampilkan keterangan gramatikalnya.
    val onFormTap: (String, String) -> Unit = { form, info ->
        val spoken = speaker.speak(form)
        val shown = TextNormalizer.displayStress(form.removePrefix("*"), state.settings.stressStyle)
        val message = buildString {
            append(shown).append(" — ").append(info)
            if (!spoken) append("\n(Suara bahasa Rusia belum terpasang: Setelan → Text-to-speech)")
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.SystemBar)) {
        // Pita gelap di belakang status bar, seperti pada rancangan referensi.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
        Box(Modifier.weight(1f).fillMaxWidth().background(AppColors.Background)) {
            Column(Modifier.fillMaxSize()) {
                SecondaryTabRow(
                    selectedTabIndex = tab,
                    containerColor = AppColors.Background,
                    contentColor = AppColors.TextDark,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tab, matchContentSize = false), height = 3.dp, color = AppColors.Teal,
                        )
                    },
                    divider = {},
                ) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            modifier = Modifier.testTag("tab:$index"),
                            selectedContentColor = AppColors.TextDark,
                            unselectedContentColor = AppColors.TextGray,
                            text = {
                                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, letterSpacing = 0.3.sp)
                            },
                        )
                    }
                }
                when (tab) {
                    0 -> SearchScreen(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { viewModel.search(it) },
                        onClear = viewModel::clearQuery,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onRemoveBookmark = viewModel::removeBookmark,
                        onClearHistory = viewModel::clearHistory,
                        onFormTap = onFormTap,
                    )
                    1 -> HelpScreen()
                    else -> AboutScreen(state.stats, state.settings, versionName, viewModel::updateSettings)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp)) { data ->
                Snackbar(data, containerColor = Color(0xFF323232), contentColor = Color.White)
            }
        }
        // Ruang untuk papan ketik / bilah navigasi sistem.
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars.union(WindowInsets.ime)))
    }
}
