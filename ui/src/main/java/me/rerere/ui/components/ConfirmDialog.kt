package me.rerere.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ProjetoIAConfirmDialog(
    show: Boolean,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
) {
    if (!show) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape), elevation = null) {
                Text(dismissText)
            }
        }
    )
}
