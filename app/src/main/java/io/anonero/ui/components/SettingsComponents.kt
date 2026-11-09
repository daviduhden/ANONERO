package io.anonero.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Reusable settings section header used across the settings surface. */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.padding(vertical = 12.dp, horizontal = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    supporting: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onCheckedChange(!checked) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun SettingsActionItem(
    title: String,
    supporting: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}
