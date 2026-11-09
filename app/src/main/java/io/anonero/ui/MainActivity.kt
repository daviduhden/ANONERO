package io.anonero.ui

import AnonNeroTheme
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.asLiveData
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.anonero.AnonApplication
import io.anonero.R
import io.anonero.model.WalletManager
import io.anonero.services.AnonNeroService
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.services.startAnonService
import io.anonero.ui.home.LockScreen
import io.anonero.ui.home.LockScreenMode
import io.anonero.ui.home.LockScreenShortCut
import io.anonero.ui.home.graph.homeGraph
import io.anonero.ui.home.graph.routes.Home
import io.anonero.ui.onboard.OnboardViewModel
import io.anonero.ui.onboard.graph.LandingScreenRoute
import io.anonero.ui.onboard.graph.onboardingGraph
import io.anonero.ui.theme.ThemeMode
import io.anonero.ui.theme.ThemeStore
import io.anonero.util.LocaleController
import io.anonero.util.StealthMode
import io.anonero.util.WalletProfileStore
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WALLET_USE_TOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named
import timber.log.Timber


private const val TAG = "MainActivity"

/**
 * Single Android activity and Compose host.
 *
 * Responsibilities that must remain here rather than in a composable:
 * - applying the per-app locale before the view tree is created;
 * - installing the splash screen before `super.onCreate`;
 * - setting `FLAG_SECURE` for every window content;
 * - deciding whether the lock overlay is shown from [WalletState.backgroundSyncFlow];
 * - entering background sync when the user leaves the app or the screen turns off;
 * - closing the wallet, stopping Tor and the foreground service, and killing the process
 *   on a real finish.
 *
 * The activity does not own the wallet. It delegates open/close/sync to the services and
 * survives configuration changes without closing the wallet.
 */
class MainActivity : ComponentActivity() {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val walletState: WalletState by inject()
    private val torService: TorService by inject()
    val anonPrefs: SharedPreferences by inject(named(WALLET_PREFERENCES))

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashscreen = installSplashScreen()
        var isAppReady by mutableStateOf(false)
        super.onCreate(savedInstanceState)
        // The per-app locale can change without a process restart; re-create the
        // channels so their user-visible names follow the selected language.
        (application as? AnonApplication)?.initNotificationChannels()
        ThemeStore.load(anonPrefs)
        StealthMode.load(anonPrefs)
        if (StealthMode.isEnabled(anonPrefs)) walletState.setHideAmounts(true)
        //a permission prompt does not count as leaving the app
        ActivityCompat.setPermissionCompatDelegate(object : ActivityCompat.PermissionCompatDelegate {
            override fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int): Boolean {
                permissionPrompt = true
                return false
            }

            // The compat delegate has no Activity Result API equivalent.
            @Suppress("DEPRECATION")
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) = false
        })
        // Everything this window ever shows -- seed, balance, addresses, the signing
        // review -- otherwise lands in the recents thumbnail, which the system writes
        // to disk, and is capturable by any screen-capture-privileged app. One flag
        // closes all of it.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val scrimColor = Color.Black.copy(alpha = 0.1f).toArgb()
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val lightBars = when (ThemeStore.mode.value) {
            ThemeMode.LIGHT -> true
            ThemeMode.SYSTEM -> !systemDark
            ThemeMode.DARK -> false
        }
        splashscreen.setKeepOnScreenCondition { isAppReady }
        enableEdgeToEdge(
            statusBarStyle = if (lightBars) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, scrimColor)
            } else {
                SystemBarStyle.dark(scrimColor)
            },
            navigationBarStyle = if (lightBars) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, scrimColor)
            } else {
                SystemBarStyle.dark(scrimColor)
            },
        )
        setContent {
            val context = LocalContext.current
            var walletExist by remember {
                mutableStateOf(WalletProfileStore.anyWalletExists(context, anonPrefs))
            }
            var useTor by remember { mutableStateOf(true) }
            val onboardViewModel = koinViewModel<OnboardViewModel>()
            val scope = rememberCoroutineScope()

            LaunchedEffect(key1 = true) {
                scope.launch(Dispatchers.IO) {
                    walletExist = WalletProfileStore.anyWalletExists(context, anonPrefs)
                    useTor = anonPrefs.getBoolean(WALLET_USE_TOR, true)
                    isAppReady = true
                }
            }
            TorSplash(enableTor = useTor) {
                val themeMode by ThemeStore.mode.collectAsState()
                AnonNeroTheme(mode = themeMode) {
                    val navController = rememberNavController()
                    DisposableEffect(navController) {
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            Timber.tag(TAG).d("destination changed to ${destination.route}")
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose {
                            navController.removeOnDestinationChangedListener(listener)
                        }
                    }
                    val showLockScreen by walletState.backgroundSyncFlow.asLiveData().observeAsState(walletState.backgroundSync)
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController, popExitTransition = {
                            ExitTransition.None
                        }, popEnterTransition = {
                            EnterTransition.None
                        }, startDestination = if (walletExist) Home else LandingScreenRoute
                        ) {
                            //landing screen and onboarding screens
                            onboardingGraph(navController, onboardViewModel)
                            //home bottom navigation
                            homeGraph(navController)
                        }
                        if (showLockScreen) {
                            LockScreen(
                                mode = LockScreenMode.LOCK_SCREEN,
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                onUnLocked = { pin, shortCut ->
                                    // The lock screen stays up until wallet2 has actually left
                                    // background sync; hiding it first showed a wallet that
                                    // looked unlocked but had no spend key.
                                    // A shortcut navigates behind the lock screen first, as
                                    // before, so the screen is ready when the lock lifts.
                                    if (shortCut != LockScreenShortCut.HOME) {
                                        walletState.emitUnlockShortcut(shortCut)
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        val exited = walletState.exitBackgroundSync(pin)
                                        withContext(Dispatchers.Main) {
                                            if (!exited) {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    getString(R.string.could_not_unlock_wallet),
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Intent(applicationContext, AnonNeroService::class.java).also {
                it.action = "start"
                ContextCompat.startForegroundService(applicationContext, it)
            }
        }
        //handle and show dialog
    }

    fun startNotificationService() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startAnonService(applicationContext)
        }
    }

    fun stopNotificationService() {
        stopService(Intent(applicationContext, AnonNeroService::class.java))
    }

    //set while a permission prompt opened by the app is on screen
    private var permissionPrompt = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (permissionPrompt) {
            permissionPrompt = false
            return
        }
        scope.launch { walletState.enterBackgroundSync() }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || isFinishing || permissionPrompt) return
        // onUserLeaveHint covers Home/Recents; screen-off and some OEM app switches go
        // straight to onStop. Stealth mode opts into the stricter lock.
        if (StealthMode.isEnabled(anonPrefs) && StealthMode.autoLock(anonPrefs)) {
            walletState.setHideAmounts(true)
            scope.launch { walletState.enterBackgroundSync() }
        }
    }

    // Global permission-result hook used only to clear the prompt flag.
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionPrompt = false
    }

    // Intercepts every external activity start so a system screen is not treated as
    // leaving the app. The Activity Result API cannot provide a global override.
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onDestroy() {
        // A language change recreates the activity. Do not close the wallet, stop Tor
        // or kill the process for that internal configuration change.
        if (isChangingConfigurations) {
            super.onDestroy()
            return
        }
        // Do not kill the process until the wallet has stored and closed. The close runs
        // off the main thread with a bounded wait; cancellation of the UI scope must not
        // cancel this job before it finishes.
        val closeScope = CoroutineScope(Dispatchers.IO)
        closeScope.launch {
            withTimeoutOrNull(5_000) {
                WalletManager.instance?.wallet?.let {
                    it.store()
                    it.close()
                }
            }
        }.invokeOnCompletion { failure ->
            if (failure != null) {
                Timber.tag(TAG).e(failure)
            }
            torService.dispose()
            scope.cancel()
            // the service is START_STICKY: left started, Android reads the kill below as a crash
            // and restarts the app headless a second later, notification and Tor included
            stopNotificationService()
            Process.killProcess(Process.myPid())
        }
        super.onDestroy()
    }
}
