package bitflux.core

import java.util.concurrent.TimeUnit

trait RealtimeRunner { self: Context =>
  
  override val isRealtime = true

  private val cachedSources = new java.util.IdentityHashMap[Source[_], Boolean]()

  private val queue = new java.util.concurrent.LinkedBlockingQueue[(Source[_], Timestamp)]

  def enqueue(newEvent: (Source[_], Timestamp)): Unit = queue.put(newEvent)

  class Listener(start: Timestamp, end: Timestamp) extends Runnable {

    override def run(): Unit = {
      subscribeToSources(start, end)
      runIt()
      shutdown()
      
      @annotation.tailrec
      def runIt(): Unit = {
        val now = Timestamp.now
        val wait = end.units - now.units
        
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

    private def subscribeToSources(start: Timestamp, end: Timestamp): Unit = {
      val sources = getChildren.filter(_.isSource).map(s => s.asInstanceOf[RealtimeSource[_]])
      for (source <- sources) {
        if (!cachedSources.containsKey(source)) {
          cachedSources.put(source, true)
          source.subscribe(start, end)
        }
      }
    }
  }

  override def run(start: Timestamp, end: Timestamp): Unit = {
    executionContext.execute(new Listener(start, end))
    Thread.sleep((end.units - start.units) / 1000000)
  }
}
