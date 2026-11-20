package com.rustcraft.launcher

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xFF20251F)
private val Muted = Color(0xFF858A83)
private val Canvas = Color(0xFFF5F6F3)
private val Green = Color(0xFF537A5A)
private val PaleGreen = Color(0xFFE7EEE5)

private data class GameVersion(val id: String, val type: String)
private data class ManagedFile(val name: String, val bytes: Long)
private data class PlayerProfile(val name: String, val provider: String)
private data class InstallProgress(
    val version: String = "",
    val status: String = "",
    val message: String = "",
    val percent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameDir = getExternalFilesDir(null)?.resolve("games") ?: filesDir.resolve("games")
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green,
                    onPrimary = Color.White,
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink,
                    secondary = Green,
                )
            ) {
                RustCraftApp(gameDir, ::notify)
            }
        }
    }

    private fun notify(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RustCraftApp(gameDir: File, notify: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("Home") }
    var busy by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf("") }
    var accountProvider by remember { mutableStateOf("offline") }
    var reduceMotion by remember { mutableStateOf(false) }
    var touchControls by remember { mutableStateOf(true) }
    var touchSize by remember { mutableFloatStateOf(100f) }
    var touchTransparency by remember { mutableFloatStateOf(70f) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingProfileDelete by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    val installedVersions = remember { mutableStateListOf<String>() }
    var latest by remember { mutableStateOf("Loading versions…") }
    var selectedVersion by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("mods") }
    val files = remember { mutableStateListOf<ManagedFile>() }
    val profiles = remember { mutableStateListOf<PlayerProfile>() }
    var ram by remember { mutableFloatStateOf(2048f) }
    var versionQuery by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var showInstallProgress by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf(InstallProgress()) }

    suspend fun execute(request: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val json = JSONObject(request.toString())
        json.put("root", gameDir.absolutePath)
        JSONObject(NativeBridge.execute(json.toString()))
    }

    fun invoke(request: JSONObject, onResult: (Any) -> Unit = {}) {
        scope.launch {
            busy = true
            try {
                val response = execute(request)
                if (!response.optBoolean("ok")) {
                    notify(response.optString("error", "Launcher operation failed"))
                } else {
                    onResult(response.opt("data") ?: JSONObject())
                }
            } catch (error: Exception) {
                notify(error.message ?: "Launcher operation failed")
            } finally {
                busy = false
            }
        }
    }

    fun refreshProfiles() {
        invoke(JSONObject().put("action", "profiles")) { result ->
            profiles.clear()
            val array = result as? JSONArray
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.get(index)
                    if (item is JSONObject) {
                        profiles.add(PlayerProfile(item.optString("name"), item.optString("provider", "offline")))
                    } else {
                        profiles.add(PlayerProfile(item.toString(), "offline"))
                    }
                }
            }
            val savedSelection = selectedProfile
            if (profiles.none { it.name == savedSelection }) selectedProfile = profiles.firstOrNull()?.name.orEmpty()
        }
    }

    fun refreshInstalledVersions() {
        invoke(JSONObject().put("action", "installed_versions")) { result ->
            installedVersions.clear()
            val array = result as? JSONArray
            if (array != null) {
                for (index in 0 until array.length()) installedVersions.add(array.getString(index))
            }
        }
    }

    suspend fun updateInstallProgress(version: String) {
        val status = execute(JSONObject().put("action", "install_status"))
        if (!status.optBoolean("ok")) return
        val data = status.optJSONObject("data") ?: JSONObject()
        installProgress = InstallProgress(
            version = data.optString("version", version),
            status = data.optString("status"),
            message = data.optString("message"),
            percent = data.optInt("percent").coerceIn(0, 100),
            downloadedBytes = data.optLong("downloaded_bytes"),
            totalBytes = data.optLong("total_bytes"),
            bytesPerSecond = data.optLong("bytes_per_second"),
        )
    }

    fun startInstall(version: String) {
        if (installing) return
        installing = true
        busy = true
        showInstallProgress = true
        installProgress = InstallProgress(version = version, status = "preparing", message = "Preparing download")
        scope.launch {
            val installJob = scope.async(Dispatchers.IO) {
                val request = JSONObject().put("action", "install").put("id", version)
                val json = JSONObject(request.toString()).put("root", gameDir.absolutePath)
                JSONObject(NativeBridge.execute(json.toString()))
            }
            try {
                while (installJob.isActive) {
                    delay(300)
                    updateInstallProgress(version)
                }
                val response = installJob.await()
                updateInstallProgress(version)
                if (!response.optBoolean("ok")) {
                    val message = response.optString("error", "Installation failed")
                    installProgress = installProgress.copy(status = "failed", message = message)
                    notify(message)
                } else {
                    refreshInstalledVersions()
                }
            } catch (error: Exception) {
                installProgress = installProgress.copy(status = "failed", message = error.message ?: "Installation failed")
                notify(error.message ?: "Installation failed")
            } finally {
                installing = false
                busy = false
            }
        }
    }

    fun refreshFiles() {
        invoke(JSONObject().put("action", "list_files").put("category", category)) { result ->
            files.clear()
            val array = result as? JSONArray
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    files.add(ManagedFile(item.getString("name"), item.optLong("bytes")))
                }
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val destination = File(gameDir, category).apply { mkdirs() }
                        val name = queryDisplayName(context, uri)
                        val target = uniqueDestination(destination, name)
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Could not open the selected file" }
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    notify("File added to $category")
                    refreshFiles()
                } catch (error: Exception) {
                    notify(error.message ?: "Could not import file")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        invoke(JSONObject().put("action", "versions")) { result ->
            val data = result as? JSONObject ?: JSONObject()
            latest = data.optString("latest", "Unknown")
            val array = data.optJSONArray("versions") ?: JSONArray()
            versions = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(GameVersion(item.optString("id"), item.optString("type")))
                }
            }
            selectedVersion = latest
        }
        refreshInstalledVersions()
        refreshProfiles()
        invoke(JSONObject().put("action", "settings")) { result ->
            val settings = result as? JSONObject
            ram = settings?.optInt("ram_mb", 2048)?.toFloat() ?: 2048f
            reduceMotion = settings?.optBoolean("reduce_motion", false) ?: false
            touchControls = settings?.optBoolean("touch_controls", true) ?: true
            touchSize = settings?.optInt("touch_size", 100)?.toFloat() ?: 100f
            touchTransparency = settings?.optInt("touch_transparency", 70)?.toFloat() ?: 70f
            selectedProfile = settings?.optString("selected_profile").orEmpty()
        }
    }

    fun selectProfile(name: String) {
        selectedProfile = name
        invoke(
            JSONObject().put("action", "settings")
                .put("settings", JSONObject().put("selected_profile", name))
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 22.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("rustcraft", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                        Text("JAVA EDITION • ANDROID", fontSize = 9.sp, color = Muted, letterSpacing = 1.2.sp)
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("READY", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                },
                label = "page-transition",
                modifier = Modifier.weight(1f),
            ) { target ->
                when (target) {
                    "Versions" -> VersionsPage(
                        versions = versions,
                        latest = latest,
                        selectedVersion = selectedVersion,
                        installedVersions = installedVersions,
                        installing = installing,
                        query = versionQuery,
                        onQuery = { versionQuery = it },
                        onSelect = { selectedVersion = it },
                        onInstall = { startInstall(selectedVersion) },
                    )
                    "Library" -> LibraryPage(
                        category = category,
                        files = files,
                        onCategory = { category = it; refreshFiles() },
                        onImport = { importFile.launch(arrayOf("*/*")) },
                        onRemove = { pendingDelete = it },
                    )
                    "Settings" -> SettingsPage(
                        ram = ram,
                        onRamChange = { ram = it },
                        reduceMotion = reduceMotion,
                        onReduceMotion = { reduceMotion = it },
                        touchControls = touchControls,
                        onTouchControls = { touchControls = it },
                        touchSize = touchSize,
                        onTouchSize = { touchSize = it },
                        touchTransparency = touchTransparency,
                        onTouchTransparency = { touchTransparency = it },
                        onSave = {
                            invoke(JSONObject().put("action", "settings")
                                .put("settings", JSONObject().put("ram_mb", ram.toInt())
                                    .put("reduce_motion", reduceMotion)
                                    .put("touch_controls", touchControls)
                                    .put("touch_size", touchSize.toInt())
                                    .put("touch_transparency", touchTransparency.toInt()))) {
                                notify("Settings saved")
                            }
                        },
                    )
                    else -> HomePage(
                        latest = latest,
                        selectedProfile = selectedProfile,
                        selectedVersion = selectedVersion,
                        installed = selectedVersion in installedVersions,
                        onVersions = { page = "Versions" },
                        onInstall = { startInstall(selectedVersion) },
                        onProfiles = { profileDialog = true },
                    )
                }
            }

            NavigationBar(
                containerColor = Color.White,
            ) {
                listOf(
                    Triple("Home", Icons.Rounded.Home, "Home"),
                    Triple("Versions", Icons.AutoMirrored.Rounded.List, "Versions"),
                    Triple("Library", Icons.Rounded.Build, "Library"),
                    Triple("Settings", Icons.Rounded.Settings, "Settings"),
                ).forEach { (label, icon, route) ->
                    NavigationBarItem(
                        selected = page == route,
                        onClick = {
                            page = route
                            if (route == "Library") refreshFiles()
                            if (route == "Home") refreshProfiles()
                        },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                        label = { Text(label, fontSize = 10.sp) },
                    )
                }
            }
        }
    }

    if (profileDialog) {
        AlertDialog(
            onDismissRequest = { profileDialog = false },
            title = { Text("Player profiles · ${profiles.size}/3") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (profiles.isNotEmpty()) {
                        Text("SAVED PROFILES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        profiles.forEach { profile ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (profile.name == selectedProfile) PaleGreen else Canvas)
                                    .clickable {
                                        selectProfile(profile.name)
                                        profileDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(profile.name, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(profile.provider.uppercase(), color = Muted, fontSize = 9.sp)
                                }
                                if (profile.name == selectedProfile) Icon(Icons.Rounded.Check, null, tint = Green)
                                androidx.compose.material3.IconButton(onClick = { pendingProfileDelete = profile.name }) {
                                    Icon(Icons.Rounded.Delete, "Remove ${profile.name}", tint = Muted)
                                }
                            }
                        }
                    }
                    Text("ACCOUNT TYPE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("microsoft" to "Microsoft", "ely.by" to "Ely.by", "offline" to "Offline").forEach { (id, label) ->
                            if (accountProvider == id) {
                                Button(
                                    onClick = { accountProvider = id },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                ) { Text(label, fontSize = 11.sp) }
                            } else {
                                OutlinedButton(
                                    onClick = { accountProvider = id },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                ) { Text(label, fontSize = 11.sp) }
                            }
                        }
                    }
                    if (accountProvider != "offline") {
                        Text(
                            "${
                                if (accountProvider == "ely.by") "Ely.by" else "Microsoft"
                            } authentication is not configured in this build. No sign-in or account token is simulated.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    } else {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("New player name") },
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    invoke(JSONObject().put("action", "add_profile")
                        .put("provider", accountProvider).put("profile", profileName)) {
                        selectProfile(profileName)
                        profileName = ""
                        profileDialog = false
                        refreshProfiles()
                    }
                }, enabled = accountProvider == "offline" && profileName.isNotBlank() && profiles.size < 3) {
                    Text("Add offline profile")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (pendingProfileDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingProfileDelete = null },
            title = { Text("Remove profile?") },
            text = { Text("Remove ${pendingProfileDelete.orEmpty()} from this launcher?") },
            confirmButton = {
                Button(onClick = {
                    val name = pendingProfileDelete ?: return@Button
                    pendingProfileDelete = null
                    invoke(JSONObject().put("action", "remove_profile").put("profile", name)) {
                        if (selectedProfile == name) selectProfile("")
                        refreshProfiles()
                    }
                }) { Text("Remove") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingProfileDelete = null }) { Text("Cancel") } },
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove file?") },
            text = { Text("This deletes ${pendingDelete.orEmpty()} from this app's $category folder.") },
            confirmButton = {
                Button(onClick = {
                    val name = pendingDelete ?: return@Button
                    pendingDelete = null
                    invoke(JSONObject().put("action", "remove_file")
                        .put("category", category).put("name", name)) { refreshFiles() }
                }) { Text("Remove") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showInstallProgress) {
        InstallProgressDialog(
            progress = installProgress,
            running = installing,
            onDismiss = { if (!installing) showInstallProgress = false },
        )
    }
}

@Composable
private fun BrandMark() {
    BoxMark()
}

@Composable
private fun BoxMark() {
    Surface(
        modifier = Modifier.size(37.dp),
        shape = RoundedCornerShape(13.dp),
        color = PaleGreen,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Build, contentDescription = null, tint = Green, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun HomePage(
    latest: String,
    selectedProfile: String,
    selectedVersion: String,
    installed: Boolean,
    onInstall: () -> Unit,
    onVersions: () -> Unit,
    onProfiles: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        Text("Ready to build?", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Your vanilla launcher, ready for the next adventure.", color = Muted, fontSize = 14.sp)

        Box(
            Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(23.dp))
        ) {
            WorldArtwork(Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("THE OVERWORLD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Text("Make it yours.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VANILLA JAVA", color = Green, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text(selectedVersion.ifBlank { latest }, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onVersions) { Text("Change") }
                }
                Button(
                    onClick = onInstall,
                    enabled = !installed,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (installed) "CLIENT FILES DOWNLOADED" else "INSTALL $selectedVersion")
                }
                Text(
                    if (installed) "The client JAR is verified. A compatible runtime is still required to play."
                    else "Downloads and verifies the official client files. A game runtime is not included yet.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onProfiles).padding(19.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = PaleGreen, modifier = Modifier.size(43.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AccountCircle, null, tint = Green)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("PLAYER PROFILE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(selectedProfile.ifBlank { "Add an offline profile" }, color = Ink, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Rounded.Check, null, tint = Green, modifier = Modifier.size(19.dp))
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A NOTE ON COMPATIBILITY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    "Version files and add-on folders are ready to manage. Running Java Edition on Android requires a compatible Java runtime and graphics bridge, which are not included yet.",
                    color = Ink,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun WorldArtwork(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(Color(0xFF91CFE0))
        drawCircle(
            Color(0xFFFFE7A1),
            radius = size.minDimension * 0.1f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.24f),
        )
        val farHill = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height * 0.67f)
            lineTo(size.width * 0.27f, size.height * 0.32f)
            lineTo(size.width * 0.5f, size.height * 0.68f)
            lineTo(size.width * 0.72f, size.height * 0.39f)
            lineTo(size.width, size.height * 0.67f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(farHill, Color(0xFF789E79))
        drawRect(Color(0xFF639257), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.72f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.28f))
        drawRect(Color(0xFF477044), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.74f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.14f))
        drawRect(Color(0xFF527C49), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.7f), size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.17f))
        drawRect(Color(0xFF417040), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.65f), size = androidx.compose.ui.geometry.Size(size.width * 0.08f, size.height * 0.06f))
        drawRect(Color(0xFFB88A5C), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.86f), size = androidx.compose.ui.geometry.Size(size.width * 0.26f, size.height * 0.14f))
        drawRect(Color(0xFF9D714A), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.86f), size = androidx.compose.ui.geometry.Size(size.width * 0.2f, size.height * 0.14f))
        drawRect(Color(0xFF6D9659), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.84f), size = androidx.compose.ui.geometry.Size(size.width * 0.26f, size.height * 0.035f))
    }
}

@Composable
private fun VersionsPage(
    versions: List<GameVersion>,
    latest: String,
    selectedVersion: String,
    installedVersions: List<String>,
    installing: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
) {
    val filtered = versions.filter { it.id == "26.3" }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
        Text("Versions", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Minecraft Java Edition 26.3 · Vanilla only", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onInstall, enabled = selectedVersion.isNotBlank() && !installing) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (installing) "Installing…" else "Install client")
            }
            Text("Selected: ${selectedVersion.ifBlank { latest }}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(filtered, key = { it.id }) { version ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (version.id == selectedVersion) PaleGreen else Color.White)
                        .clickable { onSelect(version.id) }
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(version.id, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(version.type, fontSize = 11.sp, color = Muted)
                    }
                    if (version.id in installedVersions) {
                        Text("DOWNLOADED", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    } else if (version.id == latest) {
                        Text("LATEST", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    if (version.id == selectedVersion) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Check, null, tint = Green, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallProgressDialog(
    progress: InstallProgress,
    running: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Installing ${progress.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(progress.message.ifBlank { "Preparing installation" }, color = Muted, fontSize = 13.sp)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Green,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${progress.percent.coerceIn(0, 100)}%", fontWeight = FontWeight.Bold, color = Ink)
                    Text(formatBytes(progress.bytesPerSecond) + "/s", color = Muted, fontSize = 12.sp)
                }
                Text(
                    "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Text(
                    when (progress.status) {
                        "complete" -> "Verified client files. A compatible game runtime is still required."
                        "failed" -> "Installation failed. You can try again."
                        "verifying" -> "Checking Mojang SHA-1 checksums…"
                        else -> progress.status.replaceFirstChar { it.uppercase() }
                    },
                    color = if (progress.status == "failed") Color(0xFFB3261E) else Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            if (!running) OutlinedButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun LibraryPage(
    category: String,
    files: List<ManagedFile>,
    onCategory: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
        Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Manage files in this app's private game folder.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("mods", "resourcepacks", "shaderpacks").forEach { item ->
                val label = when (item) {
                    "resourcepacks" -> "Packs"
                    "shaderpacks" -> "Shaders"
                    else -> "Mods"
                }
                if (category == item) {
                    Button(onClick = { onCategory(item) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onCategory(item) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(label) }
                }
            }
        }
        androidx.compose.material3.IconButton(onClick = {
            onCategory(category)
        }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh files", tint = Green)
        }
        Spacer(Modifier.height(9.dp))
        FilledTonalButton(onClick = onImport) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Import a file")
        }
        Spacer(Modifier.height(8.dp))
        if (files.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.AutoMirrored.Rounded.List, null, tint = Muted, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                Text("Nothing here yet", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Imported files will appear in $category.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(files, key = { it.name }) { file ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .padding(start = 14.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(file.bytes), color = Muted, fontSize = 11.sp)
                        }
                        androidx.compose.material3.IconButton(onClick = { onRemove(file.name) }) {
                            Icon(Icons.Rounded.Delete, "Remove ${file.name}", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    ram: Float,
    onRamChange: (Float) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    touchControls: Boolean,
    onTouchControls: (Boolean) -> Unit,
    touchSize: Float,
    onTouchSize: (Float) -> Unit,
    touchTransparency: Float,
    onTouchTransparency: (Float) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("A few practical launcher preferences.", color = Muted, fontSize = 13.sp)
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MEMORY PREFERENCE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("${ram.toInt()} MB", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                Slider(value = ram, onValueChange = onRamChange, valueRange = 512f..8192f, steps = 30)
                Text("Saved for a future game runtime. This does not change Android's memory limit.", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce animations", color = Ink, fontWeight = FontWeight.Medium)
                        Text("Use fewer page transitions.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = onReduceMotion)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Touch controls", color = Ink, fontWeight = FontWeight.Medium)
                        Text("Saved for a future Android gameplay integration.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = touchControls, onCheckedChange = onTouchControls)
                }
                Text("Control size · ${touchSize.toInt()}%", color = Ink, fontWeight = FontWeight.Medium)
                Slider(value = touchSize, onValueChange = onTouchSize, valueRange = 50f..100f, steps = 9)
                Text("Transparency · ${touchTransparency.toInt()}%", color = Ink, fontWeight = FontWeight.Medium)
                Slider(value = touchTransparency, onValueChange = onTouchTransparency, valueRange = 20f..100f, steps = 15)
                Button(onClick = onSave) { Text("Save settings") }
            }
        }
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ABOUT THIS BUILD", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("RustCraft 1.0.1", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Rust download and profile core · Android 10+", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return File(cursor.getString(index)).name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "imported-file"
}

private fun uniqueDestination(directory: File, requestedName: String): File {
    val name = File(requestedName).name.ifBlank { "imported-file" }
    val extensionAt = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val stem = name.substring(0, extensionAt)
    val extension = name.substring(extensionAt)
    var candidate = File(directory, name)
    var suffix = 1
    while (candidate.exists()) {
        candidate = File(directory, "$stem ($suffix)$extension")
        suffix++
    }
    return candidate
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
