package bitflux.core

trait SequentialScheduler extends Scheduler { self: Context =>
  
  override def shutdown(): Unit = {}

  override def step(graphChanged: Boolean): Unit = {
    val cached = getSortedFlowSeq

    // use a while loop here to improve performance because this is a hotspot
    var j = 0
    val s = cached.size
    while (j < s) {
      val flow = cached(j)

      if (flow.isSource) {
        flow.invoke()
      }
      else {
        val len = flow.getParents.size
        var i = 0
        var cont = true
        while (cont && i < len) {
          if (flow.getParents(i).isActive) {
            flow.invoke()
            cont = false
          }
          i += 1
        }
      }
      j += 1
    }
  }
}