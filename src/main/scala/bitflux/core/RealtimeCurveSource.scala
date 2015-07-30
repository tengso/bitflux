package bitflux.core

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime

class RealtimeCurveSource[T](data: Curve[T])(implicit context: Context)
    extends Flow[T] 
    with RealtimeSource[T] {

  // FIXME: synchronized
  @volatile var current = -1
  var last: Option[Int] = None

  // FIXME: don't collapse data
  react(context) {
    if (current >= 0 && current < data.size) {
      if (last.isEmpty || last.get < current) {
        last = Some(current)
        data(current)._2
      }
    }
  }

  override def subscribe(start: DateTime, end: DateTime): Unit = {
    val self = this
    val realTimeContext = context.asInstanceOf[RealtimeRunner]

    val timer = new Runnable() {
      override def run() {
        data.foreach {
          case (time, _) =>
            val waitTime = time.millis - DateTime.now.millis
            if (waitTime > 0) {
              Thread.sleep(waitTime)
            }
            realTimeContext.enqueue((self, DateTime.now))
            current += 1
        }
      }
    }

    context.executionContext.execute(timer)
  }
}

