package com.malinskiy.marathon.android.adam

import com.malinskiy.adam.AndroidDebugBridgeServer
import com.malinskiy.adam.AndroidDebugBridgeServerFactory
import com.malinskiy.adam.request.async.AsyncDeviceMonitorRequest
import com.malinskiy.adam.request.devices.ListDevicesRequest
import com.malinskiy.marathon.actor.unboundedChannel
import com.malinskiy.marathon.analytics.internal.pub.Track
import com.malinskiy.marathon.android.AndroidConfiguration
import com.malinskiy.marathon.device.DeviceProvider
import com.malinskiy.marathon.exceptions.NoDevicesException
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.time.Timer
import com.malinskiy.marathon.vendor.VendorConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_WAIT_FOR_DEVICES_TIMEOUT = 30000L
private const val DEFAULT_WAIT_FOR_DEVICES_SLEEP_TIME = 500L

class AdamDeviceProvider(
    private val track: Track,
    private val timer: Timer
) : DeviceProvider, CoroutineScope {
    private val logger = MarathonLogging.logger("AdamDeviceProvider")

    private val channel: Channel<DeviceProvider.DeviceEvent> = unboundedChannel()
    private val bootWaitContext = newFixedThreadPoolContext(4, "AdamDeviceProvider")
    override val coroutineContext: CoroutineContext
        get() = bootWaitContext

    override val deviceInitializationTimeoutMillis: Long = 180_000

    private lateinit var server: AndroidDebugBridgeServer
    private val deviceStateTracker = DeviceStateTracker()

    override suspend fun initialize(vendorConfiguration: VendorConfiguration) {
        if (vendorConfiguration !is AndroidConfiguration) {
            throw IllegalStateException("Invalid configuration $vendorConfiguration passed")
        }

        server = AndroidDebugBridgeServerFactory().build()

        withTimeoutOrNull(DEFAULT_WAIT_FOR_DEVICES_TIMEOUT) {
            while (server.execute(ListDevicesRequest()).isEmpty()) {
                delay(DEFAULT_WAIT_FOR_DEVICES_SLEEP_TIME)
            }
        } ?: throw NoDevicesException("No devices found")

        val deviceEventsChannel = server.execute(AsyncDeviceMonitorRequest(), this)
        bootWaitContext.executor.execute {
            runBlocking {
                while (!deviceEventsChannel.isClosedForReceive) {
                    deviceEventsChannel.receive().forEach {
                        val serial = it.serial
                        val newState = it.state

                        when (deviceStateTracker.update(serial, newState)) {
                            TrackingUpdate.CONNECTED -> {
                                val device =
                                    AdamAndroidDevice(server, deviceStateTracker, serial, track, timer, vendorConfiguration.serialStrategy)
                                channel.send(DeviceProvider.DeviceEvent.DeviceConnected(device))
                            }
                            TrackingUpdate.DISCONNECTED -> {
                                val device =
                                    AdamAndroidDevice(server, deviceStateTracker, serial, track, timer, vendorConfiguration.serialStrategy)
                                channel.send(DeviceProvider.DeviceEvent.DeviceDisconnected(device))
                            }
                            TrackingUpdate.NOTHING_TO_DO -> Unit
                        }
                        logger.debug { "Device ${it.serial} changed state to ${it.state}" }
                    }
                }
            }
        }
    }

    override suspend fun terminate() {
        bootWaitContext.close()
        channel.close()
    }

    override fun subscribe() = channel
}
