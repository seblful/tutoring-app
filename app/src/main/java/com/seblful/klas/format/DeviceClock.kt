package com.seblful.klas.format

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.seblful.klas.domain.time.WallClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The device's clock, read in the device's **current** zone — not the zone captured when a repository or
 * form was constructed.
 */
internal class DeviceClock(private val context: Context, private val time: Clock = Clock.System) : WallClock {
    override fun now(): Instant = time.now()

    override val zone: TimeZone get() = TimeZone.currentSystemDefault()

    val zoneChanges: Flow<TimeZone> = callbackFlow {
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
fun WallClock.zones(): Flow<TimeZone> = if (this is DeviceClock) zoneChanges else flowOf(zone)
