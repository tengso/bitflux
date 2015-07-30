package bitflux.env

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import com.github.nscala_time.time.Imports._
import bitflux.core.SequentialRealtimeContext
import bitflux.core.SingleLevelParallelRealtimeContext
import bitflux.core.ParallelRealtimeContext

class Realtime(from: DateTime, to: DateTime, isSequential: Boolean = true)
    (implicit executionContext: ExecutionContext = ExecutionContext.Implicits.global) {

  implicit val context = if (isSequential)
    new SequentialRealtimeContext("bitflux-realtime")
  else
    new SingleLevelParallelRealtimeContext("bitflux-realtime")

  def run[T](code: => T): Future[T] = {
    Future {
      val res = code
      context.run(from, to)
      res

    }
  }
}