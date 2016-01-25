package bitflux.core

class TimerTick
object TimerTick extends TimerTick

class Timer extends Placeholder[TimerTick]

class Alarm(time: Timestamp)(implicit val context: Context)
  extends Flow[TimerTick]
  with RealtimeSource[TimerTick]
  with SimulationSource[TimerTick] {

  override val isSource = true

  var ticked = false
  var scheduled = false

  react(context) {
    // FIXME: remove time match in simulation mode 
    if (scheduled && !ticked && (context.isRealtime || now == time)) {
      ticked = true
      TimerTick
    }
  }

  override def next(start: Timestamp, end: Timestamp): Option[(Timestamp, TimerTick)] = {
    if (!ticked && time >= start && time <= end) {
      scheduled = true
      Some((time, TimerTick))
    }
    else
      None
  }

  override def subscribe(start: Timestamp, end: Timestamp): Unit = {
    val task = new java.util.TimerTask {
      override def run() = {
//         logger.debug(s"sent timer event: ${DateTime.now}")
        scheduled = true
        val realtimeContext = getContext.asInstanceOf[RealtimeRunner]
        realtimeContext.enqueue((Alarm.this, time))
      }
    }

    new java.util.Timer().schedule(task, (time.units - now.units) / 1000000)
  }
}  

