package com.sensora.app.ui.health

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sensora.app.R
import com.sensora.app.core.health.Urgency
import com.sensora.app.ui.theme.BadColor
import com.sensora.app.ui.theme.GoodColor
import com.sensora.app.ui.theme.LimitedColor

fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

@StringRes
fun urgencyText(u: Urgency): Int = when (u) {
    Urgency.NORMAL -> R.string.urg_normal
    Urgency.DISCUSS -> R.string.urg_discuss
    Urgency.SOON -> R.string.urg_soon
    Urgency.URGENT -> R.string.urg_urgent
}

fun urgencyColor(u: Urgency): Color = when (u) {
    Urgency.NORMAL -> GoodColor
    Urgency.DISCUSS -> Color(0xFF1E88E5)
    Urgency.SOON -> LimitedColor
    Urgency.URGENT -> BadColor
}

@Composable
fun UrgencyBanner(u: Urgency) {
    val color = urgencyColor(u)
    val icon = when (u) { Urgency.NORMAL -> Icons.Filled.CheckCircle; Urgency.DISCUSS -> Icons.Filled.Info; Urgency.SOON -> Icons.Filled.Warning; Urgency.URGENT -> Icons.Filled.Error }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(urgencyText(u)), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ResultCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Note(@StringRes id: Int) = Text(stringResource(id), style = MaterialTheme.typography.bodySmall)
