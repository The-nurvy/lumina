package com.example.Lumina.network

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class NearbySyncManager(context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_STAR
    private val serviceId = "com.example.Lumina"

    @SuppressLint("MissingPermission")
    fun startHosting(onClient: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startAdvertising(
            "Host",
            serviceId,
            createConnectionLifecycleCallback(onClient, onMsg),
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        )
    }

    @SuppressLint("MissingPermission")
    fun startDiscovering(onHost: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startDiscovery(
            serviceId,
            createDiscoveryCallback(onHost, onMsg),
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        )
    }

    fun sendPayload(ids: List<String>, bytes: ByteArray) {
        client.sendPayload(ids, Payload.fromBytes(bytes))
    }

    private fun createConnectionLifecycleCallback(
        onClient: (String) -> Unit,
        onMsg: (String, ByteArray) -> Unit
    ) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            client.acceptConnection(id, createPayloadCallback(onMsg))
        }

        override fun onConnectionResult(id: String, res: ConnectionResolution) {
            if (res.status.isSuccess) onClient(id)
        }

        override fun onDisconnected(id: String) {}
    }

    private fun createDiscoveryCallback(
        onHost: (String) -> Unit,
        onMsg: (String, ByteArray) -> Unit
    ) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            client.requestConnection(
                "Client",
                id,
                createConnectionLifecycleCallback({ onHost(it) }, onMsg)
            )
        }

        override fun onEndpointLost(id: String) {}
    }

    private fun createPayloadCallback(onMsg: (String, ByteArray) -> Unit) =
        object : PayloadCallback() {
            override fun onPayloadReceived(id: String, payload: Payload) {
                payload.asBytes()?.let { onMsg(id, it) }
            }

            override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
        }
}
