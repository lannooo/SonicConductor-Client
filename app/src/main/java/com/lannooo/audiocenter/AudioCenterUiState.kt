package com.lannooo.audiocenter

import com.lannooo.audiocenter.client.Message

data class AudioCenterUiState(
    val ip: String = "192.168.57.86",
    val port: String = "6666",

    val networkStatus: ConnectionStatus = ConnectionStatus.NONE,
    val showDialog: Boolean = false,
    val networkMessage: String = ""
)

data class MessageRecord(
    val who: Char,   // C or S
    val type: Message.MessageType,
    val shortContent: String,
    val timestamp: String
)

enum class ConnectionStatus {
    NONE,
    CONNECTING,
    READY,
    // TODO: update to Lost if connection is terminated from server side or other reasons
    LOST
}