package bitflux.core

trait ExtensibleFlow[+T] { self: Flow[T] =>
  def extendFlow(output: Flow[_])(code: => Unit): Unit = 
    getContext.extendFlow(code)
}