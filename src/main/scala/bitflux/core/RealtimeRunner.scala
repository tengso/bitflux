package bitflux.core

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import com.github.nscala_time.time.Imports.{ DateTime, richReadableInstant }

trait RealtimeRunner { self: Context =>
  
  override val isRealtime = true

  private val cachedSources = new java.util.IdentityHashMap[Source[_], Boolean]()

  private val queue = new java.util.concurrent.LinkedBlockingQueue[(Source[_], DateTime)]

  def enqueue(newEvent: (Source[_], DateTime)): Unit = queue.put(newEvent)

  class Listener(start: DateTime, end: DateTime) extends Runnable {

    override def run(): Unit = {
      subscribeToSources(start, end)
      runIt()
      shutdown()
      
      @annotation.tailrec
      def runIt(): Unit = {
        val now = DateTime.now
        val wait = end.millis - now.millis
        
        queue.poll(wait, TimeUnit.MILLISECONDS) match {
          case (source, timestamp) => {
            logger.debug(s"received from $source at $now")

            currentTime = Some(now)

            // TODO: time precision
            if (lastTime.nonEmpty) {
              if (currentTime.get == lastTime.get) {
                logger.info(s"current: ${currentTime.get} last: ${lastTime.get}")
              }
            }

            exec()

            lastTime = currentTime
            
            if (isGraphChanged) subscribeToSources(currentTime.get, end)
            
            runIt()
          }
          case _ =>
        }
      }
    }

    private def subscribeToSources(start: DateTime, end: DateTime): Unit = {
      val sources = getChildren.filter(_.isSource).map(s => s.asInstanceOf[RealtimeSource[_]])
      for (source <- sources) {
        if (!cachedSources.containsKey(source)) {
          cachedSources.put(source, true)
          source.subscribe(start, end)
        }
      }
    }
  }

  override def run(start: DateTime, end: DateTime): Unit = {
    executionContext.execute(new Listener(start, end))
    Thread.sleep(end.millis - start.millis)
  }
}
