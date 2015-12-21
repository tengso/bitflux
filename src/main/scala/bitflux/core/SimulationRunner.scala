package bitflux.core

import scala.concurrent.ExecutionContext
import com.github.nscala_time.time.Imports._

trait SimulationRunner { self: Context =>
  
  override val isRealtime = false

  override def run(start: DateTime, end: DateTime): Unit = {
    val sources = getSources
    runIt(start, end, sources)
    shutdown()
    
    @annotation.tailrec
    def runIt(start: DateTime, end: DateTime, sources: Seq[SimulationSource[_]]): Unit = {
      // TODO: remember the result of last call to remove duplicate topTick call
      val sortedTopTicks = sources.map(_.next(start, end)).filter(_.nonEmpty).map(_.get._1).sorted
      val nextFromSource = sortedTopTicks.headOption

      val next = if (getCurrentTime.nonEmpty) {
        val current = getCurrentTime.get
        val hasFeedback = getFeedbacks.exists { feedback =>
          feedback.getLastTick.nonEmpty && feedback.getLastTick.get._1 == current
        }
        if (hasFeedback) {
          val nextFromFeedback = current + Context.TimeStep
          if (nextFromSource.nonEmpty) {
            if (nextFromSource.get < nextFromFeedback) nextFromSource else Some(nextFromFeedback)
          }
          else Some(nextFromFeedback)
        }
        else nextFromSource
      }
      else nextFromSource

      if (next.nonEmpty && next.get <= end) {
        setCurrentTime(next.get)

        if (lastTime.nonEmpty) {
          assert(currentTime.get > lastTime.get, s"currentTime: ${currentTime.get}, lastTime: ${lastTime.get}")
        }

        exec()

        setLastTime(next.get)

        runIt(start, end, if (isGraphChanged) getSources else sources)
      }
    }
  }

  private def getSources: Seq[SimulationSource[_]] =
    getChildren.filter(_.isSource).map(_.asInstanceOf[SimulationSource[_]])
}