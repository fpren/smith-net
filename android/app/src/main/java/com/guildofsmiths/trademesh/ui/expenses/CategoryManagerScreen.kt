package com.guildofsmiths.trademesh.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

@Composable
fun CategoryManagerScreen(onBack: () -> Unit) {
    val colors = LocalSmithColors.current
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    var addingNew by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        ConsoleHeader(title = "EXPENSE CATEGORIES", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Built-in categories can be renamed or hidden. Custom categories can be deleted if no expenses reference them.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )

            categories.sortedBy { it.sortOrder }.forEach { cat ->
                CategoryRow(cat)
            }

            Spacer(Modifier.height(6.dp))

            if (addingNew) {
                AddCategoryRow(
                    onSave = { name, code, colorHex ->
                        ExpenseCategoryRepository.add(name, code, colorHex)
                        addingNew = false
                    },
                    onCancel = { addingNew = false }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Tokens2.RadiusCard))
                        .border(0.5.dp, colors.ink.copy(alpha = 0.12f), RoundedCornerShape(Tokens2.RadiusCard))
                        .clip(RoundedCornerShape(Tokens2.RadiusCard))
                        .clickable { addingNew = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[+ Add category]", style = SmithType.action.copy(color = colors.accent))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(cat: ExpenseCategoryDef) {
    val colors = LocalSmithColors.current
    var editing by remember(cat.id) { mutableStateOf(false) }
    var name by remember(cat.id) { mutableStateOf(cat.displayName) }
    var code by remember(cat.id) { mutableStateOf(cat.shortCode) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.08f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color swatch
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(parseColor(cat.colorHex, fallback = colors.accent), RoundedCornerShape(Tokens2.RadiusTiny))
        )
        Spacer(Modifier.width(8.dp))
        if (editing) {
            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true,
                    textStyle = SmithType.bodySmall.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth().background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny)).padding(4.dp)
                )
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = code, onValueChange = { code = it.take(4) },
                    singleLine = true,
                    textStyle = SmithType.caption.copy(color = colors.inkMuted),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.width(60.dp).background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny)).padding(4.dp)
                )
            }
            Text("[save]",
                style = SmithType.caption.copy(color = colors.accent),
                modifier = Modifier
                    .clickable {
                        ExpenseCategoryRepository.update(cat.id) { it.copy(displayName = name, shortCode = code.ifBlank { "[?]" }) }
                        editing = false
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text("[x]",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier
                    .clickable {
                        name = cat.displayName; code = cat.shortCode; editing = false
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.shortCode, style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Spacer(Modifier.width(8.dp))
                    Text(cat.displayName, style = SmithType.bodySmall.copy(color = colors.ink))
                    if (cat.builtIn) {
                        Spacer(Modifier.width(6.dp))
                        Text("• built-in", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                    if (cat.hidden) {
                        Spacer(Modifier.width(6.dp))
                        Text("• hidden", style = SmithType.caption.copy(color = colors.attention))
                    }
                }
            }
            Text("[edit]",
                style = SmithType.caption.copy(color = colors.accent),
                modifier = Modifier.clickable { editing = true }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text(if (cat.hidden) "[show]" else "[hide]",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier.clickable {
                    ExpenseCategoryRepository.setHidden(cat.id, !cat.hidden)
                }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            if (!cat.builtIn) {
                Text("[del]",
                    style = SmithType.caption.copy(color = colors.statusError),
                    modifier = Modifier.clickable {
                        ExpenseCategoryRepository.delete(cat.id)
                    }.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AddCategoryRow(
    onSave: (name: String, code: String, colorHex: String) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalSmithColors.current
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.accent, RoundedCornerShape(Tokens2.RadiusCard))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Name", style = SmithType.caption.copy(color = colors.inkMuted))
            BasicTextField(
                value = name, onValueChange = { name = it },
                singleLine = true,
                textStyle = SmithType.bodySmall.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth().background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny)).padding(4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("Short code (e.g. [A])", style = SmithType.caption.copy(color = colors.inkMuted))
            BasicTextField(
                value = code, onValueChange = { code = it.take(4) },
                singleLine = true,
                textStyle = SmithType.caption.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.width(60.dp).background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusTiny)).padding(4.dp)
            )
        }
        Text("[save]",
            style = SmithType.caption.copy(color = colors.accent),
            modifier = Modifier
                .clickable {
                    if (name.isNotBlank()) onSave(name.trim(), code.ifBlank { "[${name.take(1).uppercase()}]" }, "#8C6B2A")
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text("[x]",
            style = SmithType.caption.copy(color = colors.inkMuted),
            modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

// User-entered hex parsing stays literal — the swatch color is user data typed
// into the "custom category color" field, not a theme token. Only the
// parse-failure fallback is a theme color (Smith accent), since that's what
// renders when there's no valid user color to show.
private fun parseColor(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.removePrefix("#")
        val v = clean.toLong(16)
        if (clean.length == 6) Color(0xFF000000 or v) else Color(v)
    } catch (_: Throwable) {
        fallback
    }
}
