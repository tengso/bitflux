package bitflux.combinators

import bitflux.core.Flow

object Implicits {
  implicit def convertFromFlow[T](input: Flow[T]): FlowOps[T] = new FlowOps[T](input)
}