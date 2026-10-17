package com.seblful.klas.format

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The device's current zone, not the zone captured when a repository or form was constructed. */
internal class DeviceClock(private val context: Context, private val time: Clock = Clock.systemUTC()) : Clock() {
    override fun instant(): Instant = time.instant()

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = time.withZone(zone)

    val zoneChanges: Flow<ZoneId> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(zone)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(zone)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}

/** Fixed/injected test clocks keep their chosen zone; the device adapter also wakes readers on changes. */
fun Clock.zones(): Flow<ZoneId> = if (this is DeviceClock) zoneChanges else flowOf(zone)
