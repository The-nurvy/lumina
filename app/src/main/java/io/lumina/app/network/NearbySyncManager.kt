package io.lumina.app.network

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbySyncManager(context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_STAR
    
    @SuppressLint("MissingPermission")
    fun startHosting(onClient: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startAdvertising("Host", "io.lumina.app", object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                client.acceptConnection(id, buildPayloadCallback(onMsg))
            }
            override fun onConnectionResult(id: String, res: ConnectionResolution) { if (res.status.isSuccess) onClient(id) }
            override fun onDisconnected(id: String) {}
        }, AdvertisingOptions.Builder().setStrategy(strategy).build())
    }

    @SuppressLint("MissingPermission")
    fun startDiscovering(onHost: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startDiscovery("io.lumina.app", object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                client.requestConnection("Client", id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) { client.acceptConnection(id, buildPayloadCallback(onMsg)) }
                    override fun onConnectionResult(id: String, res: ConnectionResolution) { if (res.status.isSuccess) onHost(id) }
                    override fun onDisconnected(id: String) {}
                })
            }
            override fun onEndpointLost(id: String) {}
        }, DiscoveryOptions.Builder().setStrategy(strategy).build())
    }

    private fun buildPayloadCallback(onMsg: (String, ByteArray) -> Unit) = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) { payload.asBytes()?.let { onMsg(id, it) } }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    fun sendPayload(ids: List<String>, bytes: ByteArray) { client.sendPayload(ids, Payload.fromBytes(bytes)) }
}
