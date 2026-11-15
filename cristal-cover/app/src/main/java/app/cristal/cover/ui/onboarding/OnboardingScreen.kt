package app.cristal.cover.ui.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.GlassButton
import app.cristal.cover.ui.launcher.SwitchRow
import app.cristal.cover.ui.launcher.startSafely
import app.cristal.cover.ui.settings.CorePermissions
import app.cristal.cover.ui.settings.InfoText
import app.cristal.cover.ui.settings.OptionalPermissions
import app.cristal.cover.ui.settings.SettingsViewModel
import app.cristal.cover.ui.settings.ShizukuSection

private const val STEP_COUNT = 6

/**
 * Configuração guiada da primeira execução. Cada etapa explica para que serve o acesso antes de
 * pedir, todas podem ser puladas e tudo pode ser refeito depois em Configurações > Permissões.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backdrop = rememberGlassBackdrop()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    // Ao voltar das telas do sistema, relê o que foi concedido.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { text -> safely("mostrar aviso") { Toast.makeText(context, text, Toast.LENGTH_LONG).show() } }
    }
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WallpaperLayer(
            image = if (settings.wallpaperKind == WallpaperKind.IMAGE) wallpaper else null,
            presetIndex = settings.wallpaperPreset,
            dim = 0.2f,
            modifier = Modifier.glassBackdropSource(backdrop),
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().padding(insets).padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(STEP_COUNT) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(4.dp)
                                .width(if (index == step) 22.dp else 10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (index <= step) 0.95f else 0.35f)),
                        )
                    }
                }
                GlassSurface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    cornerRadius = 30.dp,
                    reactToTouch = false,
                    elevation = 16.dp,
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                        when (step) {
                            0 -> {
                                StepTitle("Bem-vindo ao Cristal Cover")
                                InfoText(
                                    "Um launcher e uma tela de bloqueio em vidro líquido, feitos para a tela externa do seu Razr.",
                                )
                                InfoText("• Grade de apps com páginas, pastas e dock")
                                InfoText("• Gaveta com busca, widgets e painel de notificações")
                                InfoText("• Tela de bloqueio com relógio, notificações e atalhos")
                                InfoText(
                                    "Vamos ativar o que o app precisa. Leva uns dois minutos, cada etapa pode ser pulada " +
                                        "e tudo pode ser refeito depois em Configurações > Permissões.",
                                )
                            }

                            1 -> {
                                StepTitle("Acessos principais")
                                CorePermissions(vm, permissions)
                            }

                            2 -> {
                                StepTitle("Shizuku (opcional)")
                                ShizukuSection(vm, permissions)
                            }

                            3 -> {
                                StepTitle("Tela externa")
                                InfoText(
                                    "A Motorola só mostra na tela externa os apps que você liberar. Libere o Cristal (e os " +
                                        "apps que quiser usar por lá) em Configurações > Tela externa > Apps.",
                                )
                                GlassButton("Abrir configurações do sistema", Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    val opened = vm.center.externalDisplaySettingsIntents().any { context.startSafely(it) }
                                    if (!opened) vm.message("Não foi possível abrir as configurações do sistema.")
                                }
                                InfoText(
                                    "Para abrir o Cristal lá: feche o aparelho, vá ao painel de apps da tela externa e toque em Cristal.",
                                )
                                SwitchRow(
                                    title = "Assumir a tela externa",
                                    subtitle = "Abre o Cristal sozinho sempre que a tela inicial da Motorola aparecer na tela externa. " +
                                        "Precisa do serviço de acessibilidade.",
                                    checked = settings.coverTakeover,
                                    onChange = { vm.set(PrefKeys.COVER_TAKEOVER, it) },
                                    enabled = permissions.accessibility || settings.coverTakeover,
                                )
                            }

                            4 -> {
                                StepTitle("Extras")
                                OptionalPermissions(vm, permissions)
                            }

                            else -> {
                                StepTitle("Tudo pronto")
                                InfoText("• Deslize para cima: gaveta de apps")
                                InfoText("• Deslize para baixo: painel rápido e notificações")
                                InfoText("• Deslize para os lados: páginas e o painel \"Hoje\"")
                                InfoText("• Toque longo em um ícone: menu; continue segurando e arraste para mover")
                                InfoText("• Solte um app sobre outro para criar uma pasta")
                                InfoText("• Toque longo no fundo: widgets, papel de parede e configurações")
                                InfoText("• Dois toques no fundo: bloquear a tela")
                                Spacer(Modifier.size(6.dp))
                                InfoText("A tela de bloqueio do Cristal começa desligada. Ative em Configurações > Tela de bloqueio.")
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step > 0) GlassButton("Voltar", Modifier.weight(1f)) { step-- }
                    GlassButton(
                        text = when (step) {
                            0 -> "Começar"
                            STEP_COUNT - 1 -> "Abrir o launcher"
                            else -> "Continuar"
                        },
                        modifier = Modifier.weight(1f),
                        primary = true,
                    ) {
                        if (step >= STEP_COUNT - 1) onFinish() else step++
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
}
