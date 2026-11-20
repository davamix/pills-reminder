package io.github.davamix.pillsreminder.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock, in the phone's time zone at each call (SC-4). `Clock.systemDefaultZone()` keeps the zone it was
 * created with, so it would miss a change of zone while the app runs. On Android, the system resets the default zone
 * in a running app when the zone changes; P5 checks it on a device.
 */
class CurrentZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
