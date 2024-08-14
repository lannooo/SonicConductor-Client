package com.lannooo.audiocenter

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringError
import androidx.compose.material.icons.rounded.WifiTetheringOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lannooo.audiocenter.ui.theme.AudioCenterTheme
import java.util.Locale


enum class ConnectionUiState(@StringRes val desc: Int, val icon: ImageVector, val color: Color) {
    NONE(
        R.string.connect_status_none,
        icon = Icons.Rounded.WifiTetheringOff,
        color = Color.Gray
    ),
    CONNECTING(
        R.string.connect_status_connecting,
        icon = Icons.Rounded.WifiFind,
        color = Color.Blue
    ),
    READY(
        R.string.connect_status_ready,
        icon = Icons.Rounded.WifiTethering,
        color = Color.Green
    ),
    LOST(
        R.string.connect_status_lost,
        icon = Icons.Rounded.WifiTetheringError,
        color = Color.Red
    )
}

fun enableTextFieldInput(state: ConnectionStatus): Boolean {
    return state == ConnectionStatus.NONE || state == ConnectionStatus.LOST
}

fun enableConnectBtn(state: ConnectionStatus): Boolean {
    return state == ConnectionStatus.NONE || state == ConnectionStatus.LOST
}

fun enableDisconnectBtn(state: ConnectionStatus): Boolean {
    return state == ConnectionStatus.READY
}

fun enableSendBtn(state: ConnectionStatus): Boolean {
    return state == ConnectionStatus.READY
}

fun connectionStatusToState(status: ConnectionStatus): ConnectionUiState {
    return when (status) {
        ConnectionStatus.NONE -> ConnectionUiState.NONE
        ConnectionStatus.CONNECTING -> ConnectionUiState.CONNECTING
        ConnectionStatus.READY -> ConnectionUiState.READY
        ConnectionStatus.LOST -> ConnectionUiState.LOST
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlTopBar(modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        modifier = modifier
    )
}

@Composable
fun InformationPart(records: List<MessageRecord>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp)
    ) {
        items(records) {
            MessageRecordItem(
                it.who,
                it.type.name.lowercase(),
                it.shortContent,
                it.timestamp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MessageRecordItem(
    who: Char,
    msgType: String,
    shortContent: String,
    timeStr: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.ChatBubble,
            contentDescription = stringResource(
                if (who == 'C') R.string.message_record_from_client else R.string.message_record_from_server
            ),
            tint = if (who == 'C') Color.Magenta else Color.Blue,
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .align(Alignment.Top)
        )
        Column {
            Row {
                Text(
                    text = msgType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Row {
                Text(
                    text = shortContent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WelcomePart(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(id = R.string.welcome),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = modifier.weight(1f)
        )
        Column {
            Row {
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = stringResource(R.string.message_record_from_client),
                    tint = Color.Magenta,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically)
                )
                Text(text = "From Me", style = MaterialTheme.typography.labelSmall)
            }
            Row {
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = stringResource(R.string.message_record_from_server),
                    tint = Color.Blue,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically)
                )
                Text(text = "From Server", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FootnotePart(modifier: Modifier = Modifier) {
    val from = stringResource(R.string.publisher)
    Text(
        text = "Developed by $from",
        fontSize = 18.sp,
        color = Color.DarkGray,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun ActionExtraPart(
    networkStatus: ConnectionStatus,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Button(
            onClick = onSendClick,
            modifier = Modifier
                .padding(8.dp, 8.dp)
                .fillMaxWidth(),
            enabled = enableSendBtn(networkStatus)
        ) {
            Text(text = stringResource(R.string.send))
        }
    }
}

@Composable
fun ConnectionPart(
    networkStatus: ConnectionStatus,
    onConnectClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        val state = connectionStatusToState(networkStatus)
        Icon(
            imageVector = state.icon,
            contentDescription = stringResource(state.desc),
            tint = state.color,
            modifier = Modifier
                .size(48.dp)
                .padding(8.dp, 0.dp)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onCloseClick,
            enabled = enableDisconnectBtn(networkStatus),
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = stringResource(R.string.disconnect))
        }
//        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onConnectClick,
            enabled = enableConnectBtn(networkStatus),
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = stringResource(R.string.connect))
        }
    }
}

@Composable
fun InputFieldPart(
    ip: String,
    port: String,
    networkStatus: ConnectionStatus,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        TextField(
            value = ip,
            onValueChange = onIpChange,
            enabled = enableTextFieldInput(networkStatus),
            label = { Text(stringResource(R.string.ip_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.weight(2f)
        )
        Spacer(modifier = Modifier.size(8.dp))
        TextField(
            value = port,
            onValueChange = onPortChange,
            enabled = enableTextFieldInput(networkStatus),
            label = { Text(stringResource(R.string.port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ConnectDialog(msg: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = modifier
            ) {
                Text(text = stringResource(R.string.connect_alert_confirm))
            }
        },
        title = { Text(text = stringResource(R.string.connect_alert_title)) },
        text = { Text(text = stringResource(R.string.connect_alert_message, msg)) },
        modifier = modifier
    )
}


@Composable
fun AudioCenterApp(acViewModel: AudioCenterViewModel = viewModel()) {
    // standard ViewModel-UI implementation
    val acUiState by acViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AudioControlTopBar()
        }
    ) { innerPadding ->
        Surface(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp, 0.dp)
//                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WelcomePart(modifier = Modifier.fillMaxWidth())
                InformationPart(
                    acViewModel.messageRecords,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                InputFieldPart(
                    acUiState.ip,
                    acUiState.port,
                    acUiState.networkStatus,
                    onIpChange = { acViewModel.updateIp(it) },
                    onPortChange = { acViewModel.updatePort(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                // actions from UI
                ConnectionPart(
                    acUiState.networkStatus,
                    onConnectClick = { acViewModel.connect() },
                    onCloseClick = { acViewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth()
                )
                ActionExtraPart(
                    acUiState.networkStatus,
                    onSendClick = { acViewModel.sendTestMessage() },
                    modifier = Modifier.fillMaxWidth()
                )
                FootnotePart(modifier = Modifier.fillMaxWidth())
            }
            if (acUiState.showDialog) {
                ConnectDialog(
                    msg = acUiState.networkMessage,
                    onDismiss = { acViewModel.updateDialog(false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "My Preview")
@Composable
fun AudioCenterAppPreview() {
    AudioCenterTheme {
        AudioCenterApp()
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dark Preview")
@Composable
fun AudioCenterAppDarkPreview() {
    AudioCenterTheme(darkTheme = true) {
        AudioCenterApp()
    }
}