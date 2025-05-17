package bigtwo.app.bluetooth

import java.io.Serializable

data class BluetoothMessage(
    val senderId: String,
    val content: String
) : Serializable
