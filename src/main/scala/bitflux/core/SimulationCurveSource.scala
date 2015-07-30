package bitflux.core

import com.github.nscala_time.time.Imports._

trait BaseSimulationCurveSource[T] extends Flow[T] with SimulationSource[T] {

  implicit val context: Context
  val data: Curve[T]

  private var index = 0

  react (context) {
    val currentTime = getContext.getCurrentTime
    val value = data.get(index)

    if (value.nonEmpty) {
      val topTime = value.get._1
      val topValue = value.get._2

      logger.trace(s"topTime: $topTime topValue: $topValue now: $currentTime index: $index")

      if (topTime == currentTime.get || getContext.isRealtime) {
        index += 1
        value.get._2
      }
    }
  }

  override def topTick(start: DateTime, end: DateTime): Option[(DateTime, T)] = {
    val tick = data.get(index)

    val ret = tick match {
      case None => {
        logger.debug(s"no more tick")
        None
      }
      case Some((time, value)) => {
        getContext.getCurrentTime match {
          case None => tick
          case Some(currentTime) => {
            // currentTime is the time of the cycle just finished
            forwardTo(currentTime)
            data.get(index)
          }
        }
      }
    }
    ret
  }

  // one tick after
  // TODO: avoid while loop
  private def forwardTo(to: DateTime): Unit = {
    var break = false
    while (!break) {
      val tick = data.get(index)
      tick match {
        case None => {
          break = true
          None
        }
        case Some((time, _)) => {
          if (time <= to) index += 1 else break = true
        }
      }
    }
  }
}

class SimulationCurveSource[T](val data: Curve[T])(implicit val context: Context) extends BaseSimulationCurveSource[T]

/*
class SimulationCurveSource[T](data: Curve[T])(implicit context: Context)
	extends Flow[T] with SimulationSource[T] {
  
  var index = 0

  react (context) {
    val currentTime = getContext.getCurrentTime
    val value = data.get(index)

    if (value.nonEmpty) {
      val topTime = value.get._1
      val topValue = value.get._2

      logger.trace(s"topTime: $topTime topValue: $topValue now: $currentTime index: $index")

      if (topTime == currentTime.get || getContext.isRealtime) {
        index += 1
        value.get._2
      }
    }
  }

  override def topTick(start: DateTime, end: DateTime): Option[(DateTime, T)] = {
    val tick = data.get(index)

    val ret = tick match {
      case None => {
        logger.debug(s"no more tick")
        None
      }
      case Some((time, value)) => {
        getContext.getCurrentTime match {
          case None => tick
          case Some(currentTime) => {
            // currentTime is the time of the cycle just finished
            forwardTo(currentTime)
            data.get(index)
          }
        }
      }
    }
    ret
  }
  
  // one tick after
  // TODO: avoid while loop
  private def forwardTo(to: DateTime): Unit = {
    var break = false
    while (!break) {
      val tick = data.get(index)
      tick match {
        case None => {
          break = true
          None
        }
        case Some((time, _)) => {
          if (time <= to) index += 1 else break = true
        }
      }
    }
  }
}
 */
