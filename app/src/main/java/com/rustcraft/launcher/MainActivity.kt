package com.rustcraft.launcher

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    val installedVersions = remember { mutableStateListOf<String>() }
    var latest by remember { mutableStateOf("Loading versions…") }
    var selectedVersion by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("mods") }
    val files = remember { mutableStateListOf<ManagedFile>() }
    val profiles = remember { mutableStateListOf<String>() }
    var ram by remember { mutableFloatStateOf(2048f) }
    var versionQuery by remember { mutableStateOf("") }

    fun invoke(request: JSONObject, onResult: (Any) -> Unit = {}) {
        scope.launch {
            busy = true
            try {
                val response = withContext(Dispatchers.IO) {
                    val json = JSONObject(request.toString())
                    json.put("root", gameDir.absolutePath)
                    JSONObject(NativeBridge.execute(json.toString()))
                }
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
                for (index in 0 until array.length()) profiles.add(array.getString(index))
            }
            if (selectedProfile !in profiles) selectedProfile = profiles.firstOrNull().orEmpty()
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

    fun refreshInstalledVersions() {
        invoke(JSONObject().put("action", "installed_versions")) { result ->
            installedVersions.clear()
            val array = result as? JSONArray
            if (array != null) {
                for (index in 0 until array.length()) installedVersions.add(array.getString(index))
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
        }
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

            AnimatedContent(targetState = page, label = "page-transition", modifier = Modifier.weight(1f)) { target ->
                when (target) {
                    "Versions" -> VersionsPage(
                        versions = versions,
                        latest = latest,
                        selectedVersion = selectedVersion,
                        installedVersions = installedVersions,
                        query = versionQuery,
                        onQuery = { versionQuery = it },
                        onSelect = { selectedVersion = it },
                        onInstall = {
                            invoke(JSONObject().put("action", "install").put("id", selectedVersion)) {
                                notify("$selectedVersion client downloaded and verified")
                                refreshInstalledVersions()
                            }
                        },
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
                        onSave = {
                            invoke(JSONObject().put("action", "settings")
                                .put("settings", JSONObject().put("ram_mb", ram.toInt()))) {
                                notify("RAM preference saved")
                            }
                        },
                    )
                    else -> HomePage(
                        latest = latest,
                        selectedProfile = selectedProfile,
                        onVersions = { page = "Versions" },
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
            title = { Text("Offline profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profiles.isNotEmpty()) {
                        Text("SAVED PROFILES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        profiles.forEach { profile ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (profile == selectedProfile) PaleGreen else Canvas)
                                    .clickable {
                                        selectedProfile = profile
                                        profileDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(profile, modifier = Modifier.weight(1f), color = Ink)
                                if (profile == selectedProfile) Icon(Icons.Rounded.Check, null, tint = Green)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("New player name") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    invoke(JSONObject().put("action", "add_profile").put("profile", profileName)) {
                        selectedProfile = profileName
                        profileName = ""
                        profileDialog = false
                        refreshProfiles()
                    }
                }, enabled = profileName.isNotBlank()) { Text("Save profile") }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileDialog = false }) { Text("Cancel") }
            },
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
    onVersions: () -> Unit,
    onProfiles: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        Text("YOUR WORLD,\nYOUR WAY.", fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("A little launcher for a blocky world.", color = Muted, fontSize = 14.sp)

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE7EEE5)),
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("VANILLA VERSION", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Text(latest, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Mojang version catalog", color = Muted, fontSize = 13.sp)
                Button(onClick = onVersions, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Browse versions")
                }
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
private fun VersionsPage(
    versions: List<GameVersion>,
    latest: String,
    selectedVersion: String,
    installedVersions: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
) {
    val filtered = remember(versions, query) {
        if (query.isBlank()) versions.take(80)
        else versions.filter { it.id.contains(query, ignoreCase = true) }.take(100)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
        Text("Versions", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Choose a release or snapshot from Mojang.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search versions") },
            shape = RoundedCornerShape(15.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onInstall, enabled = selectedVersion.isNotBlank()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download client")
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
private fun SettingsPage(ram: Float, onRamChange: (Float) -> Unit, onSave: () -> Unit) {
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
                Button(onClick = onSave) { Text("Save preference") }
            }
        }
        Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ABOUT THIS BUILD", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("RustCraft 0.1.0", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Rust metadata and file-management core · Android 10+", color = Muted, fontSize = 12.sp)
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
