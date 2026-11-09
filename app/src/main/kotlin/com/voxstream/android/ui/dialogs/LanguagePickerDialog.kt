package com.voxstream.android.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxstream.core.models.Language

@Composable
fun LanguagePickerDialog(
    currentSource: String,
    currentTarget: String,
    onDismiss: () -> Unit,
    onLanguagesSelected: (source: String, target: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Source, 1 = Target
    var selectedSource by remember { mutableStateOf(currentSource) }
    var selectedTarget by remember { mutableStateOf(currentTarget) }

    val languages = Language.DEFAULT_LIST

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Languages") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Source (${selectedSource.uppercase()})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Target (${selectedTarget.uppercase()})") }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    items(languages) { lang ->
                        val isSelected = if (selectedTab == 0) lang.code == selectedSource else lang.code == selectedTarget
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedTab == 0) selectedSource = lang.code else selectedTarget = lang.code
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (selectedTab == 0) selectedSource = lang.code else selectedTarget = lang.code
                                }
                            )
                            Text(text = "${lang.displayName} (${lang.code})")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onLanguagesSelected(selectedSource, selectedTarget)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
