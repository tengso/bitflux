package bitflux.core

// Feeback can be viewed as a special kind of "Source" that forward data on the next tick
// But it is only invoked at the end of a cycle to retrieve data from dependent flow

// TODO: change variable name of "source"
class Feedback[T](name: String = "bitflux.core.feedback")(implicit context: Context) extends Flow[T] {
  
  var dependent: Option[Flow[T]] = None

  context.addChild(this)
  addParent(context)

  // this is invoked at the end of a time cycle
  // data are copied from dependent flow to the next tick 
  react() {
    if (dependent.nonEmpty) {
      val lastTick = dependent.get.getLastTick
      if (lastTick.nonEmpty && lastTick.get._1 == now) {
        dependent.get()
      }
      else logger.debug(s"feedback ${this} invoked, but source value is not available")
    }
  }

  def from(input: Flow[T]): Unit = dependent = Some(input)

  // only if received data from prior cycle, it becomes active in current cycle
  override def isActive: Boolean = 
    getLastTick.nonEmpty && prior.nonEmpty && getLastTick.get._1 == prior.get
    
  override def toString() = name 
}