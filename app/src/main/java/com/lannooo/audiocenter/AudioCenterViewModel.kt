package com.lannooo.audiocenter

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lannooo.audiocenter.client.ClientService.ClientBinder
import com.lannooo.audiocenter.client.Message
import com.lannooo.audiocenter.client.MessageListener
import com.lannooo.audiocenter.tool.AppUtil.currentDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

class AudioCenterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AudioCenterUiState())
    val uiState: StateFlow<AudioCenterUiState> = _uiState.asStateFlow()
    private val _messageRecords = mutableStateListOf(
        MessageRecord('C', Message.MessageType.NOTIFICATION, "Hello, world!", currentDateTime())
    )
    val messageRecords: List<MessageRecord>
        get() = _messageRecords


    fun updateIp(it: String) {
        _uiState.update { currentState ->
            currentState.copy(ip = it)
        }
    }

    fun updatePort(it: String) {
        _uiState.update { currentState ->
            currentState.copy(port = it)
        }
    }

    fun updateDialog(show: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(showDialog = show)
        }
    }

    private fun addMessageRecord(fromMe: Boolean, type: Message.MessageType, content: String) {
        val record = MessageRecord(
            who = if (fromMe) 'C' else 'S',
            type = type,
            shortContent = content,
            timestamp = currentDateTime()
        )
        _messageRecords.add(record)
        if (_messageRecords.size >= 16) {
            _messageRecords.removeFirst()
        }
    }


    fun sendTestMessage() {
        // launch a coroutine to send a test message to the server
        viewModelScope.launch(Dispatchers.IO) {
            // Call the service to send a test message
            binder?.sendRegisterMessage()
        }
    }

    fun connect() {
        Log.i(TAG, "in Connect: Try to connect to ${_uiState.value.ip}:${_uiState.value.port}")
        if (binder == null) {
            // TODO: show a dialog to inform the user that the service is not available
            return
        }
        // TODO: check if the ip and port are valid
        val ip = _uiState.value.ip
        val port = _uiState.value.port

        // launch a coroutine to connect to the server and update the status, open a dialog if necessary
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { currentState ->
                currentState.copy(networkStatus = ConnectionStatus.CONNECTING)
            }
            var success = false
            withContext(Dispatchers.IO) {
                // Call the service to connect to the server
                success = binder?.connect(ip, port.toInt())!!
            }
            if (success) {
                _uiState.update { currentState ->
                    currentState.copy(
                        networkStatus = ConnectionStatus.READY,
                        showDialog = true,
                        networkMessage = "Connection with ${_uiState.value.ip}:${_uiState.value.port} is established."
                    )
                }
            } else {
                _uiState.update { currentState ->
                    currentState.copy(
                        networkStatus = ConnectionStatus.NONE,
                        showDialog = true,
                        networkMessage = "Failed to connect to ${_uiState.value.ip}:${_uiState.value.port}."
                    )
                }
            }
        }
    }

    fun disconnect() {
        Log.i(TAG, "in Disconnect")
        // launch a coroutine to disconnect from the server and update the status
        viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                // Call the service to disconnect from the server
                binder?.disconnect()
            }
            _uiState.update { currentState ->
                currentState.copy(networkStatus = ConnectionStatus.NONE)
            }
        }
    }

    private val _messageListener =
        MessageListener { fromMe, type, shortContent ->
            viewModelScope.launch(Dispatchers.Main) {
                addMessageRecord(fromMe, type, shortContent)
            }
        }

    private val _serviceBinder: MutableLiveData<ClientBinder?> = MutableLiveData()

    private val _connection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "Service Connected")
            val clientBinder = service as ClientBinder
            clientBinder.setMessageListener(_messageListener)
            _serviceBinder.postValue(clientBinder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "Service Disconnected")
            _serviceBinder.value?.setMessageListener(null)
            _serviceBinder.postValue(null)
        }
    }

    // Kotlin style property getters
    val connection: ServiceConnection
        get() = _connection

    val binder: ClientBinder?
        get() = _serviceBinder.value

    companion object {
        const val TAG = "AudioCenterViewModel"
    }
}