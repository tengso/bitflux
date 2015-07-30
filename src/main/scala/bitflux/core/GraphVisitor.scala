package bitflux.core

import scala.collection.mutable

// context is the root of the graph
class GraphVisitor(context: Flow[Nothing]) {
  
  def visit(f: (Flow[_], Boolean) => Unit) {
    val visited = new java.util.IdentityHashMap[Flow[_], Any]()
    val queue = mutable.Queue[Flow[_]]()
    queue.enqueue(context)
    
    while(queue.nonEmpty) {
      val e = queue.dequeue()
      f(e, e.isInstanceOf[Feedback[_]])
      visited.put(e, true)
      
      for (child <- e.getChildren) {
        val parents = child.getParents
        if ((parents.length == 0 || parents.forall(visited.containsKey(_))) && !visited.containsKey(child)) {
            queue.enqueue(child)
        }
      } 
    } 
  }
}