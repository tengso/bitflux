package bitflux.core

import scala.collection.mutable

// context is the root of the graph
class GraphSorter(context: Flow[Nothing]) {
  
  def sort(): (Map[Int, Seq[Flow[_]]], Seq[Flow[_]]) = {
    val ranked = mutable.Map[Int, mutable.ArrayBuffer[Flow[_]]]()
    val feedbacks = new mutable.ArrayBuffer[Flow[_]]
    
    val sorting:  (Flow[_], Boolean) => Unit = (flow, isFeedback) => {
      if (isFeedback) feedbacks += flow
      
      val rank = flow.getParents.foldRight(-1)((flow, max) => flow.getRank.max(max)) + 1
      if (rank != 0) {
        flow.setRank(rank)
        ranked.getOrElseUpdate(rank, new mutable.ArrayBuffer[Flow[_]]) += flow
      }
    }
    
    context.setRank(0)
    new GraphVisitor(context).visit(sorting)
    (ranked.toMap, feedbacks)
  } 
  
  def print() = {
    val f: (Flow[_], Boolean) => Unit = (flow, isFeedback) => {
      printf(s"$flow => ${flow.getRank} $isFeedback")
    }
    
    context.setRank(0)
    new GraphVisitor(context).visit(f)
  }
}
