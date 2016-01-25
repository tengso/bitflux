package bitflux.core

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

  override def subscribe(start: Timestamp, end: Timestamp): Unit = {
    val self = this
    val realTimeContext = context.asInstanceOf[RealtimeRunner]

    val timer = new Runnable() {
      override def run() {
        data.foreach {
          case (time, _) =>
            val waitTime = (time.units - Timestamp.now.units) / 1000000
            if (waitTime > 0) {
              Thread.sleep(waitTime)
            }
            realTimeContext.enqueue((self, Timestamp.now))
            current += 1
        }
      }
    }

    context.executionContext.execute(timer)
  }
}

