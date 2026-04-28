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
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.ExpenseCategoryDef

@Composable
fun CategoryManagerScreen(onBack: () -> Unit) {
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    var addingNew by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background)) {
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
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
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
                        .background(ConsoleTheme.accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { addingNew = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("[+ Add category]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(cat: ExpenseCategoryDef) {
    var editing by remember(cat.id) { mutableStateOf(false) }
    var name by remember(cat.id) { mutableStateOf(cat.displayName) }
    var code by remember(cat.id) { mutableStateOf(cat.shortCode) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color swatch
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(parseColor(cat.colorHex), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        if (editing) {
            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true,
                    textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    modifier = Modifier.fillMaxWidth().background(ConsoleTheme.background, RoundedCornerShape(2.dp)).padding(4.dp)
                )
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = code, onValueChange = { code = it.take(4) },
                    singleLine = true,
                    textStyle = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    modifier = Modifier.width(60.dp).background(ConsoleTheme.background, RoundedCornerShape(2.dp)).padding(4.dp)
                )
            }
            Text("[save]",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable {
                        ExpenseCategoryRepository.update(cat.id) { it.copy(displayName = name, shortCode = code.ifBlank { "[?]" }) }
                        editing = false
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text("[x]",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable {
                        name = cat.displayName; code = cat.shortCode; editing = false
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.shortCode, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                    Spacer(Modifier.width(8.dp))
                    Text(cat.displayName, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                    if (cat.builtIn) {
                        Spacer(Modifier.width(6.dp))
                        Text("• built-in", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                    if (cat.hidden) {
                        Spacer(Modifier.width(6.dp))
                        Text("• hidden", style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning))
                    }
                }
            }
            Text("[edit]",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable { editing = true }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Text(if (cat.hidden) "[show]" else "[hide]",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.clickable {
                    ExpenseCategoryRepository.setHidden(cat.id, !cat.hidden)
                }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            if (!cat.builtIn) {
                Text("[del]",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
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
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.accent, RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Name", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            BasicTextField(
                value = name, onValueChange = { name = it },
                singleLine = true,
                textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                modifier = Modifier.fillMaxWidth().background(ConsoleTheme.background, RoundedCornerShape(2.dp)).padding(4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("Short code (e.g. [A])", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            BasicTextField(
                value = code, onValueChange = { code = it.take(4) },
                singleLine = true,
                textStyle = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                modifier = Modifier.width(60.dp).background(ConsoleTheme.background, RoundedCornerShape(2.dp)).padding(4.dp)
            )
        }
        Text("[save]",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
            modifier = Modifier
                .clickable {
                    if (name.isNotBlank()) onSave(name.trim(), code.ifBlank { "[${name.take(1).uppercase()}]" }, "#8C6B2A")
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text("[x]",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

private fun parseColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val v = clean.toLong(16)
        if (clean.length == 6) Color(0xFF000000 or v) else Color(v)
    } catch (_: Throwable) {
        Color(0xFF8C6B2A)
    }
}
