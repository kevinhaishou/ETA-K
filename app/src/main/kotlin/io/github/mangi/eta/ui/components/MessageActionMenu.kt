package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu

internal data class MessageActionItem(
    val key: Int,
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val dividerBefore: Boolean = false,
)

internal fun messageActionMenuItems(
    actions: List<MessageActionItem>,
    destructiveColor: Color,
): List<CouiMenuItem<Int>> = actions.map { action ->
    CouiMenuItem(
        value = action.key,
        text = action.label,
        icon = action.icon,
        iconTint = if (action.destructive) destructiveColor else null,
        dividerBefore = action.dividerBefore,
    )
}

@Composable
internal fun MessageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MessageActionItem>,
    destructiveColor: Color,
    onAction: (Int) -> Unit,
) {
    Menu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = messageActionMenuItems(actions, destructiveColor),
        onItemClick = { item ->
            onDismiss()
            onAction(item.value)
        },
    )
}