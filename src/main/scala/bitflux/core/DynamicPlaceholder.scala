package bitflux.core

class DynamicPlaceholder[T] extends Flow[T] {
  
  var parent: Option[Flow[T]] = None

  // FIXME: change scheduler to skip dynamic placeholder
  override def invoke(): Boolean = true 

  def invokeDirectly(): Boolean = {
    super.invoke()
  }

  react() {
    if (!isActive && parent.nonEmpty) {
      val lastTick = parent.get.getLastTick
      if (lastTick.nonEmpty && parent.get.getLastTick.get._1 == now) {
        parent.get()
      }
    }
  }

  def setParent(input: Flow[T]) {
    parent = Some(input)
    // FIXME
    addParent(input)
    input.addChild(this)
  }
}