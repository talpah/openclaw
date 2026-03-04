package ai.openclaw.android.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.android.ui.mobileAccent
import ai.openclaw.android.ui.mobileAccentSoft
import ai.openclaw.android.ui.mobileBorder
import ai.openclaw.android.ui.mobileBorderStrong
import ai.openclaw.android.ui.mobileCallout
import ai.openclaw.android.ui.mobileCaption1
import ai.openclaw.android.ui.mobileSurface
import ai.openclaw.android.ui.mobileText
import ai.openclaw.android.ui.mobileTextSecondary
import ai.openclaw.android.ui.mobileTextTertiary

@Composable
fun ChatComposer(
  healthOk: Boolean,
  pendingRunCount: Int,
  attachments: List<PendingImageAttachment>,
  onPickImages: () -> Unit,
  onRemoveAttachment: (id: String) -> Unit,
  onAbort: () -> Unit,
  onSend: (text: String) -> Unit,
  initialText: String = "",
) {
  var input by rememberSaveable { mutableStateOf("") }

  LaunchedEffect(initialText) {
    if (initialText.isNotEmpty()) input = initialText
  }

  val canSend = pendingRunCount == 0 && (input.trim().isNotEmpty() || attachments.isNotEmpty()) && healthOk
  val sendBusy = pendingRunCount > 0

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (attachments.isNotEmpty()) {
      AttachmentsStrip(attachments = attachments, onRemoveAttachment = onRemoveAttachment)
    }

    if (!healthOk) {
      Text(
        text = "Gateway is offline. Connect first in the Connect tab.",
        style = mobileCallout,
        color = ai.openclaw.android.ui.mobileWarning,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Enter message here...", style = mobileBodyStyle(), color = mobileTextTertiary) },
        minLines = 1,
        maxLines = 5,
        textStyle = mobileBodyStyle().copy(color = mobileText),
        shape = RoundedCornerShape(14.dp),
        colors = chatTextFieldColors(),
        leadingIcon = {
          IconButton(onClick = onPickImages, modifier = Modifier.size(36.dp)) {
            Icon(
              Icons.Default.AttachFile,
              contentDescription = "Attach",
              tint = mobileTextSecondary,
              modifier = Modifier.size(20.dp),
            )
          }
        },
      )

      Button(
        onClick = {
          if (sendBusy) {
            onAbort()
          } else {
            val text = input
            input = ""
            onSend(text)
          }
        },
        enabled = sendBusy || canSend,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = mobileAccent,
            contentColor = Color.White,
            disabledContainerColor = mobileBorderStrong,
            disabledContentColor = mobileTextTertiary,
          ),
        border = BorderStroke(1.dp, if (sendBusy || canSend) Color(0xFF154CAD) else mobileBorderStrong),
        contentPadding = PaddingValues(0.dp),
      ) {
        Icon(
          imageVector = if (sendBusy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
          contentDescription = if (sendBusy) "Stop" else "Send",
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun AttachmentsStrip(
  attachments: List<PendingImageAttachment>,
  onRemoveAttachment: (id: String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (att in attachments) {
      AttachmentChip(
        fileName = att.fileName,
        onRemove = { onRemoveAttachment(att.id) },
      )
    }
  }
}

@Composable
private fun AttachmentChip(fileName: String, onRemove: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = mobileAccentSoft,
    border = BorderStroke(1.dp, mobileBorderStrong),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = fileName,
        style = mobileCaption1,
        color = mobileText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, mobileBorderStrong),
      ) {
        Text(
          text = "×",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
          color = mobileTextSecondary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun chatTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurface,
    unfocusedContainerColor = mobileSurface,
    focusedBorderColor = mobileAccent,
    unfocusedBorderColor = mobileBorder,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )

@Composable
private fun mobileBodyStyle() =
  MaterialTheme.typography.bodyMedium.copy(
    fontFamily = ai.openclaw.android.ui.mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  )
