package bitflux.core

trait SequentialScheduler extends Scheduler { self: Context =>
  
  override def shutdown(): Unit = {}
  
  override def step(graphChanged: Boolean): Unit = {
    val (ranks, sorted) = getSortedFlows
    
    for (rank <- ranks) {
      val flows = sorted.get(rank).get 
      for (flow <- flows) {
        if (flow.getParents.exists(_.isActive) || flow.isSource ) { 
          flow.invoke()
        }
      }
    }
  }
}