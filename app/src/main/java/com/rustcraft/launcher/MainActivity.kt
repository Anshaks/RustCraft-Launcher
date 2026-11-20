package com.rustcraft.launcher

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.util.AtomicFile
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.horizon.android.HorizonActivity
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

private val Ink = Color(0xFF20251F)
private val Muted = Color(0xFF858A83)
private val Canvas = Color(0xFFF5F6F3)
private val Green = Color(0xFF537A5A)
private val PaleGreen = Color(0xFFE7EEE5)

private data class GameVersion(val id: String, val type: String)
private data class ManagedFile(val name: String, val bytes: Long)
private data class PlayerProfile(val name: String, val provider: String)
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
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingProfileDelete by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf(listOf(GameVersion("26.3", "release"))) }
    var latest by remember { mutableStateOf("26.3") }
    var selectedVersion by remember { mutableStateOf("26.3") }
    var category by remember { mutableStateOf("mods") }
    val files = remember { mutableStateListOf<ManagedFile>() }
    val profiles = remember { mutableStateListOf<PlayerProfile>() }

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

    fun openEngine() {
        val name = selectedProfile.ifBlank { "Steve" }
        val configFile = AtomicFile(File(context.filesDir, "android-launcher.json"))
        val config = try {
            if (configFile.baseFile.exists()) JSONObject(configFile.baseFile.readText()) else JSONObject()
        } catch (error: JSONException) {
            notify("Engine settings are invalid: ${error.message}")
            return
        } catch (error: IOException) {
            notify("Could not read engine settings: ${error.message}")
            return
        }
        config.put("name", name)
        val stream = try {
            configFile.startWrite()
        } catch (error: IOException) {
            notify("Could not save the selected player: ${error.message}")
            return
        }
        try {
            stream.write(config.toString().toByteArray(Charsets.UTF_8))
            configFile.finishWrite(stream)
        } catch (error: IOException) {
            configFile.failWrite(stream)
            notify("Could not save the selected player: ${error.message}")
            return
        }
        context.startActivity(Intent(context, HorizonActivity::class.java))
    }

    fun chooseAccountProvider(provider: String) {
        accountProvider = provider
        if (provider != "offline") {
            profileDialog = false
            openEngine()
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
        refreshProfiles()
        invoke(JSONObject().put("action", "settings")) { result ->
            val settings = result as? JSONObject
            reduceMotion = settings?.optBoolean("reduce_motion", false) ?: false
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
                        onSelect = { selectedVersion = it },
                        onInstall = ::openEngine,
                    )
                    "Library" -> LibraryPage(
                        category = category,
                        files = files,
                        onCategory = { category = it; refreshFiles() },
                        onImport = { importFile.launch(arrayOf("*/*")) },
                        onRemove = { pendingDelete = it },
                    )
                    "Settings" -> SettingsPage(
                        reduceMotion = reduceMotion,
                        onReduceMotion = { reduceMotion = it },
                        onSave = {
                            invoke(JSONObject().put("action", "settings")
                                .put("settings", JSONObject().put("reduce_motion", reduceMotion))) {
                                notify("Settings saved")
                            }
                        },
                    )
                    else -> HomePage(
                        latest = latest,
                        selectedProfile = selectedProfile,
                        selectedVersion = selectedVersion,
                        onVersions = { page = "Versions" },
                        onInstall = ::openEngine,
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
                                    onClick = { chooseAccountProvider(id) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                ) { Text(label, fontSize = 11.sp) }
                            } else {
                                OutlinedButton(
                                    onClick = { chooseAccountProvider(id) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                ) { Text(label, fontSize = 11.sp) }
                            }
                        }
                    }
                    if (accountProvider != "offline") {
                        Text(
                            "${
                                if (accountProvider == "ely.by") "Ely.by" else "Microsoft"
                            } sign-in is handled by the RustCraft game engine. Continue there to authenticate securely.",
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
    onInstall: () -> Unit,
    onVersions: () -> Unit,
    onProfiles: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        Text("Ready to play?", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("RustCraft's native 26.3 engine, ready for the next adventure.", color = Muted, fontSize = 14.sp)

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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("OPEN RUSTCRAFT ENGINE")
                }
                Text(
                    "The engine downloads and verifies its required Minecraft 26.3 resources on first use. No Java game runtime is installed.",
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
                    "Horizon Android's native Rust engine is integrated for ARM64. Open the engine to download resources, manage game accounts and settings, or play.",
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
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
) {
    val filtered = versions.filter { it.id == "26.3" }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
        Text("Versions", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Minecraft Java Edition 26.3 · Vanilla only", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onInstall, enabled = selectedVersion == "26.3") {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open engine")
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
                    if (version.id == latest) {
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
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("RustCraft launcher preferences and engine information.", color = Muted, fontSize = 13.sp)
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LAUNCHER", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce animations", color = Ink, fontWeight = FontWeight.Medium)
                        Text("Use fewer page transitions.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = onReduceMotion)
                }
                Button(onClick = onSave) { Text("Save settings") }
            }
        }
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ENGINE SETTINGS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Configure graphics, touch controls, accounts and profiles inside the RustCraft game engine.", color = Ink, fontSize = 13.sp)
            }
        }
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ABOUT & CREDITS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("RustCraft Launcher 1.0.2", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Native game engine: Horizon Android, licensed under MIT. This app includes the engine license and third-party notices.", color = Muted, fontSize = 12.sp)
                Text("Minecraft is a trademark of Mojang Studios. RustCraft is independent and is not affiliated with Mojang or Microsoft.", color = Muted, fontSize = 12.sp)
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
