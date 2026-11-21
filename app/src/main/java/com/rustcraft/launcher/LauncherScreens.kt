package com.rustcraft.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF101218)
private val Bone = Color(0xFFF4F0E7)
private val Soft = Color(0xFF9AA1AF)
private val Acid = Color(0xFFC9F36A)
private val Ember = Color(0xFFFF805C)
private val SurfaceLow = Color(0xFF191D25)
private val SurfaceHigh = Color(0xFF222832)
private val Rule = Color(0xFF343B47)

@Composable
internal fun RustCraftBrandMark() {
    Canvas(
        Modifier.size(39.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ember),
    ) {
        val u = size.minDimension / 12f
        drawRect(Night, Offset(u * 2f, u * 2f), Size(u * 2f, u * 8f))
        drawRect(Night, Offset(u * 4f, u * 2f), Size(u * 4f, u * 2f))
        drawRect(Night, Offset(u * 8f, u * 4f), Size(u * 2f, u * 2f))
        drawRect(Night, Offset(u * 4f, u * 6f), Size(u * 4f, u * 2f))
        drawRect(Night, Offset(u * 7f, u * 8f), Size(u * 2f, u * 2f))
        drawRect(Acid, Offset(u * 3f, u * 3f), Size(u * 2f, u))
    }
}

@Composable
internal fun LauncherDockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Acid else Soft,
            modifier = Modifier.size(19.dp),
        )
        Text(
            label.uppercase(),
            color = if (selected) Bone else Soft,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
internal fun RustCraftHomePage(
    latest: String,
    selectedProfile: String,
    selectedVersion: String,
    onInstall: () -> Unit,
    onVersions: () -> Unit,
    onProfiles: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PLAY / OVERVIEW",
                color = Acid,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text("01—26.3", color = Soft, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        Text(
            "Make room\nfor a new world.",
            color = Bone,
            fontSize = 32.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.8).sp,
        )

        SignalPanel()

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("ACTIVE BUILD")
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Minecraft Java  ${selectedVersion.ifBlank { latest }}",
                            color = Bone,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = onVersions,
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text("BUILDS", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onInstall),
                    color = Acid,
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ENTER THE GAME",
                                color = Night,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                "Install or continue",
                                color = Night.copy(alpha = 0.68f),
                                fontSize = 11.sp,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Open game",
                            tint = Night,
                        )
                    }
                }
                Text(
                    "The native engine verifies and installs its required 26.3 data on first launch.",
                    color = Soft,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onProfiles),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Row(
                Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = SurfaceHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AccountCircle, null, tint = Acid)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("PLAYER SLOT  /  ${if (selectedProfile.isBlank()) "EMPTY" else "ACTIVE"}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selectedProfile.ifBlank { "Choose a profile" },
                        color = Bone,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text("EDIT", color = Ember, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }

        Text(
            "RUSTCRAFT  /  AN INDEPENDENT JAVA-EDITION CLIENT",
            color = Soft,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(vertical = 3.dp),
        )
    }
}

@Composable
private fun SignalPanel() {
    Box(
        Modifier.fillMaxWidth().height(166.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceHigh),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 27.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(Rule.copy(alpha = 0.54f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(Rule.copy(alpha = 0.54f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }

            val route = Path().apply {
                moveTo(size.width * 0.07f, size.height * 0.73f)
                lineTo(size.width * 0.31f, size.height * 0.73f)
                lineTo(size.width * 0.43f, size.height * 0.47f)
                lineTo(size.width * 0.66f, size.height * 0.47f)
                lineTo(size.width * 0.78f, size.height * 0.27f)
                lineTo(size.width * 0.94f, size.height * 0.27f)
            }
            drawPath(route, Acid, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            listOf(
                Offset(size.width * 0.31f, size.height * 0.73f),
                Offset(size.width * 0.66f, size.height * 0.47f),
                Offset(size.width * 0.78f, size.height * 0.27f),
            ).forEach { point ->
                drawCircle(Ember, 5.dp.toPx(), point)
                drawCircle(SurfaceHigh, 2.dp.toPx(), point)
            }
            drawCircle(Acid.copy(alpha = 0.13f), size.minDimension * 0.32f, Offset(size.width * 0.78f, size.height * 0.42f))
            drawCircle(Acid.copy(alpha = 0.34f), size.minDimension * 0.32f, Offset(size.width * 0.78f, size.height * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "WORLD SIGNAL",
                color = Bone,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            )
            Text("SECTOR  /  26.3", color = Soft, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
        Text(
            "ROUTE READY",
            color = Acid,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
        )
    }
}

@Composable
internal fun BuildLibraryPage(
    versions: List<GameVersion>,
    latest: String,
    selectedVersion: String,
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
) {
    val supported = versions.filter { it.id == "26.3" }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Eyebrow("VERSION INDEX  /  VANILLA")
        Spacer(Modifier.height(8.dp))
        Text("Build archive", color = Bone, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("One supported release. No loader layer.", color = Soft, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("SELECTED / RELEASE")
                    Spacer(Modifier.height(5.dp))
                    Text(selectedVersion.ifBlank { latest }, color = Acid, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("Minecraft Java Edition", color = Soft, fontSize = 11.sp)
                }
                Button(onClick = onInstall, enabled = selectedVersion == "26.3") {
                    Text("OPEN ENGINE", fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Eyebrow("AVAILABLE BUILDS")
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(supported, key = { it.id }) { version ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(version.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (version.id == selectedVersion) SurfaceHigh else SurfaceLow,
                    border = BorderStroke(1.dp, if (version.id == selectedVersion) Acid.copy(alpha = 0.6f) else Rule),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (version.id == latest) Acid else Soft))
                        Column(Modifier.weight(1f)) {
                            Text("Release ${version.id}", color = Bone, fontWeight = FontWeight.SemiBold)
                            Text("Vanilla  /  ${version.type}", color = Soft, fontSize = 10.sp)
                        }
                        if (version.id == latest) Eyebrow("CURRENT")
                        if (version.id == selectedVersion) Icon(Icons.Rounded.Check, null, tint = Acid, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContentLibraryPage(
    category: String,
    files: List<ManagedFile>,
    onCategory: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Eyebrow("LOCAL STORAGE  /  APP SANDBOX")
        Spacer(Modifier.height(8.dp))
        Text("Content locker", color = Bone, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Imported add-ons stay on this device.", color = Soft, fontSize = 12.sp)
        Spacer(Modifier.height(15.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(SurfaceLow).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("mods", "resourcepacks", "shaderpacks").forEach { value ->
                val label = when (value) {
                    "resourcepacks" -> "PACKS"
                    "shaderpacks" -> "SHADERS"
                    else -> "MODS"
                }
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (category == value) SurfaceHigh else Color.Transparent)
                        .clickable { onCategory(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (category == value) Acid else Soft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("${files.size} FILE${if (files.size == 1) "" else "S"}")
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onImport,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("IMPORT", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            IconButton(onClick = { onCategory(category) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh files", tint = Soft)
            }
        }

        if (files.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceLow,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.List, null, tint = Ember, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Locker is clear", color = Bone, fontWeight = FontWeight.Bold)
                Text("Add files with the import control.", color = Soft, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(files, key = { it.name }) { file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        color = SurfaceLow,
                        border = BorderStroke(1.dp, Rule),
                    ) {
                        Row(
                            Modifier.padding(start = 13.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Ember))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, color = Bone, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(file.bytes), color = Soft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            }
                            IconButton(onClick = { onRemove(file.name) }) {
                                Icon(Icons.Rounded.Delete, "Remove ${file.name}", tint = Soft)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LauncherSettingsPage(
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    sharedFolder: Boolean,
    importingWorld: Boolean,
    onChooseSharedFolder: () -> Unit,
    onImportWorld: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Eyebrow("SYSTEM  /  PREFERENCES")
        Text("Control room", color = Bone, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Tune the launcher here; game options live in the engine.", color = Soft, fontSize = 12.sp)

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLow),
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("INTERFACE")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Low-motion transitions", color = Bone, fontWeight = FontWeight.SemiBold)
                        Text("Reduce movement between launcher screens.", color = Soft, fontSize = 10.sp)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = onReduceMotion)
                }
                Button(onClick = onSave) { Text("SAVE PREFERENCES") }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("STORAGE  /  ANDROID SAF")
                Text(
                    if (sharedFolder) "Shared folder access is saved." else "Game data stays in app storage.",
                    color = Bone,
                    fontSize = 12.sp,
                )
                Text(
                    "Android keeps game data private by default. RustCraft retains an optional SAF folder grant across app updates when Android permits it; no broad storage permission is requested.",
                    color = Soft,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                OutlinedButton(onClick = onChooseSharedFolder) {
                    Text(if (sharedFolder) "CHANGE SHARED FOLDER" else "CHOOSE SHARED FOLDER")
                }
                Eyebrow("WORLD DATA")
                Text(
                    "Import a Java world ZIP into the engine's saves folder. Existing worlds are never overwritten.",
                    color = Soft,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                FilledTonalButton(onClick = onImportWorld, enabled = !importingWorld) {
                    if (importingWorld) {
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("IMPORTING WORLD")
                    } else {
                        Text("IMPORT WORLD ZIP")
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("ENGINE HANDOFF")
                Text(
                    "Graphics, rendering, touch controls, accounts and gameplay settings are managed by the Rust game engine.",
                    color = Bone,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "Microphone access is not requested by default. Android asks only when an enabled native Voice Chat mod declares microphone access.",
                    color = Soft,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceLow,
            border = BorderStroke(1.dp, Rule),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("ABOUT  /  CREDITS")
                Text("RustCraft Launcher 1.2.0", color = Bone, fontWeight = FontWeight.Bold)
                Text(
                    "Horizon Android Rust engine, MIT licensed. Engine and third-party notices are included with the app.",
                    color = Soft,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    "Minecraft is a Mojang Studios trademark. RustCraft is independent and unaffiliated.",
                    color = Soft,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun Eyebrow(value: String) {
    Text(
        value,
        color = Soft,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
    )
}
