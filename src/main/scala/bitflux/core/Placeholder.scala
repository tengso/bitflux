package bitflux.core

class Placeholder[T] extends Flow[T] {
  
  var parent: Option[Flow[T]] = None

  react() {
    if (!isActive && parent.nonEmpty) {
      val lastTick = parent.get.getLastTick
      if (lastTick.nonEmpty && lastTick.get._1 == now) {
        parent.get()
      }
    }
  }

  def setInput(input: Flow[T]) {
    parent = Some(input)
    // FIXME
    addParent(input)
    input.addChild(this)
  }

  override def toString = "bitflux.core.Placeholder"
}