package bitflux.core


trait SimulationRunner { self: Context =>
  
  override val isRealtime = false

  override def run(start: Timestamp, end: Timestamp): Unit = {
    val sources = getSources
    runIt(start, end, sources)
    shutdown()
    
    @annotation.tailrec
    def runIt(start: Timestamp, end: Timestamp, sources: Seq[SimulationSource[_]]): Unit = {
      // TODO: remember the result of last call to remove duplicate topTick call
      // use a while loop here to improve performance as this is a hotspot
      val sourceSize = sources.size
      var i = 0
      var sortedTopTicks: Option[Timestamp] = None
      while (i < sourceSize) {
        val source = sources(i)
        val tick = source.next(start , end)
        if (tick.nonEmpty) {
          val t = tick.get._1
          if (sortedTopTicks.isEmpty || t < sortedTopTicks.get) {
            sortedTopTicks = Some(t)
          }
        }
        i += 1
      }

      val nextFromSource = sortedTopTicks

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
//          assert(currentTime.get > lastTime.get, s"currentTime: ${currentTime.get}, lastTime: ${lastTime.get}")
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