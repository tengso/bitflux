package bitflux.core

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

trait Scheduler { self: Context =>

  private var sorted: Map[Int, Seq[Flow[_]]] = _
  private var ranks: List[Int] = _
  private var feedbacks: Seq[Flow[_]] = _

  private val extendedFlows = new ListBuffer[() => Unit]()
  private var inited = false
  private var changed = false

  private val sortedFlowSeq = ArrayBuffer[Flow[_]]()

  override def getFeedbacks: Seq[Flow[_]] = feedbacks
  override def isGraphChanged: Boolean = changed

  // TODO: change name
  override def exec(): Unit = {
    if (!inited) {
      schedule()
      inited = true
    }

    // val s = System.nanoTime
    // logger.debug(s"START CYCLE: [${getCurrentTime.get}]")

    // walk through the graph
    step(changed)
    populateFeedbacks()

    // logger.debug(s"END CYCLE: Time Spent [${(System.nanoTime - s) / 1000}us]")

    // check if the graph of flows has been changed
    // if yes, re-schedule the execution plan of the graph
    changed = false
    synchronized {
      if (extendedFlows.nonEmpty) {
        extendedFlows.foreach(_())
        extendedFlows.clear()
        schedule()
        changed = true
      }
    }
  }

  override def extendFlow(code: => Unit): Unit = synchronized {
    extendedFlows.append(() => code)
  }

  protected def getSortedFlows: (Seq[Int], Map[Int, Seq[Flow[_]]]) = synchronized {
    (ranks, sorted)
  }

  protected def getSortedFlowSeq: IndexedSeq[Flow[_]] = sortedFlowSeq

  protected def step(isGraphChanged: Boolean): Unit // to be implemented by derived

  // TODO:
  // 1. dependency among feedbacks
  // 2. parallel
  private def populateFeedbacks(): Unit = getFeedbacks.foreach(_.invoke())

  private def schedule(): Unit = {
    val sorter = new GraphSorter(this)
    val result = sorter.sort()
    sorted = result._1
    ranks = sorted.keys.toList.sorted
    feedbacks = result._2.toSet.toSeq

    sortedFlowSeq.clear()
    for (rank <- ranks) {
      val flows = sorted.get(rank).get
      for (flow <- flows) {
        sortedFlowSeq += flow
      }
    }
  }
}

