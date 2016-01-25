package bitflux.core

class Constant[T](p: => T)(implicit context: Context) extends Flow[T] with RealtimeSource[T] with SimulationSource[T] {
  
  private[this] var ticked = false
  private[this] var sent = false
  
  react(context) {
    if (!sent) {
      sent = true 
      p
    }
  }
  
  override def next(start: Timestamp, end: Timestamp): Option[(Timestamp, T)] = {
    if (!ticked) {
      ticked = true
      if (getContext.getCurrentTime.nonEmpty) Some(now + Context.TimeStep, p) else Some(start, p)
    }
    else None 
  }
  
  // TODO: the timestamp might not be "start"
  override def subscribe(start: Timestamp, end: Timestamp): Unit =
    getContext.asInstanceOf[RealtimeRunner].enqueue((this, start))
}

object Constant {
  def apply[T](p: T)(implicit context: Context) = new Constant(p)
}