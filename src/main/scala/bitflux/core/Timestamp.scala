package bitflux.core


import scala.concurrent.duration.Duration
import scala.concurrent.duration._

import java.time.{Instant, LocalDateTime, ZoneId}

object Timestamp {
  def now: Timestamp = new DefaultTimestamp(Instant.now)

  def apply(year: Int, month: Int, day: Int,
            hour: Int = 0, minute: Int = 0, second: Int = 0,
            millis: Int = 0, micros: Int = 0, nanos: Int = 0): Timestamp = {
    val dt = LocalDateTime.of(year, month, day, hour, minute, second, millis * 1000000 + micros * 1000 + nanos)
    val zdt = dt.atZone(ZoneId.systemDefault())
    new DefaultTimestamp(zdt.toInstant)
  }
}

// FIXME: -/+ should take unit
trait Timestamp extends Ordered[Timestamp] {
  def -(interval: Duration): Timestamp
  def +(interval: Duration): Timestamp
  def -(interval: Long): Timestamp = this - (interval millis)
  def +(interval: Long): Timestamp = this + (interval millis)
  def units: Long
}

class DefaultTimestamp(instant: Instant) extends Timestamp {
  override def compare(that: Timestamp): Int =
    if (units == that.units) 0 else if (units > that.units) 1 else -1

  def -(interval: Duration): Timestamp = new DefaultTimestamp(instant.minusNanos(interval.toNanos))
  def +(interval: Duration): Timestamp = new DefaultTimestamp(instant.plusNanos(interval.toNanos))
  override def -(interval: Long): Timestamp = new DefaultTimestamp(instant.minusMillis(interval))
  override def +(interval: Long): Timestamp = new DefaultTimestamp(instant.plusMillis(interval))

  val units: Long = instant.getEpochSecond * 1000000000 + instant.getNano

  override def equals(other: Any): Boolean = other match {
    case that: DefaultTimestamp => that.units == this.units
    case _ => false
  }

  override def hashCode: Int = units.hashCode

  override def toString = instant.toString
}

