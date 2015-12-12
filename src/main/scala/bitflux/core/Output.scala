package bitflux.core

// Output is invoked inside a "react" block directly
// It is used for things such as branch an input flow into multiple
// sub-flows based on a map
class Output[T](parent: Flow[_]) extends Flow[T] {
  
  addParent(parent)
  parent.addChild(this)
  logger.debug(s"connected to parent: [$parent]")

  // FIXME: change scheduler to skip Output 
  // FIXME: avoid casting
  
  def pullFromParent(): Unit = {
    if (!isActive) {
      val t = parent.getLastTick
      t.foreach(tick => if (tick._1 == now) setValue(tick._2.asInstanceOf[T]))
    }
  }
}