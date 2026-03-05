package ai.openclaw.android.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.android.ui.mobileAccent
import ai.openclaw.android.ui.mobileAccentSoft
import ai.openclaw.android.ui.mobileBorderStrong
import ai.openclaw.android.ui.mobileCallout
import ai.openclaw.android.ui.mobileCaption1
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
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Pill-shaped input field
      BasicTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        textStyle = mobileBodyStyle().copy(color = mobileText),
        maxLines = 5,
        cursorBrush = SolidColor(mobileAccent),
        decorationBox = { innerTextField ->
          Row(
            modifier =
              Modifier
                .background(Color(0xFFF0F2F5), RoundedCornerShape(24.dp))
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
          ) {
            Box(modifier = Modifier.weight(1f)) {
              if (input.isEmpty()) {
                Text(
                  text = "Enter message here...",
                  style = mobileBodyStyle(),
                  color = mobileTextTertiary,
                )
              }
              innerTextField()
            }
            Box(
              modifier = Modifier.size(32.dp).clickable { onPickImages() },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Default.AttachFile,
                contentDescription = "Attach",
                tint = mobileTextSecondary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        },
      )

      // Circular send / stop button
      Surface(
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
        shape = RoundedCornerShape(999.dp),
        color = if (sendBusy || canSend) mobileAccent else mobileBorderStrong,
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (sendBusy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
            contentDescription = if (sendBusy) "Stop" else "Send",
            tint = if (sendBusy || canSend) Color.White else mobileTextTertiary,
            modifier = Modifier.size(20.dp),
          )
        }
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
private fun mobileBodyStyle() =
  MaterialTheme.typography.bodyMedium.copy(
    fontFamily = ai.openclaw.android.ui.mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  )
