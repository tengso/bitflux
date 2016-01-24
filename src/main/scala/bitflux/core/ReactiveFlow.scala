package bitflux.core

import scala.collection.mutable.ArrayBuffer

trait ReactiveFlow[+T] { self: Flow[T] =>
  
  private[this] val codeBlocks = new ArrayBuffer[(Seq[Flow[_]], () => Result[T])]
  private[this] var rank: Int = _

  private[core] def setRank(rank: Int): Unit = this.rank = rank

  private[core] def invoke(): Boolean = {
//    val start = System.nanoTime

    var hasNewValue = false

    // use while loop here to improve performance, as this is a hotspot
    val size = codeBlocks.size
    var i = 0
    while (i < size) {
      codeBlocks(i) match { case (p, code) =>
        val ticked = if (p.length == 1) {
          p(0).isActive || p(0).isInstanceOf[Context]
        }
        else {
          p.isEmpty || p.exists(input => input.isActive || input.isInstanceOf[Context])
        }
        if (ticked) {
          val result = code()
          result match {
            case  ResultValue(v) =>
              setValue(v)
              hasNewValue = true
            case ResultUnit =>
          }
        }
      }
      i += 1
    }

//    logger.trace(s"took: ${(System.nanoTime - start) / 1000}")
    
    hasNewValue
  }
  
  protected[core] def getRank = rank

  protected[core] def hasValue(p: Flow[_]*): Boolean = 
    p.forall(_.getLastValue.nonEmpty)

  protected[this] def react(p: Flow[_]*)(code: => Result[T]): Unit = {
    init(p: _*)
    val paramCode = (p, () => code)
    codeBlocks.append(paramCode)
  }

  protected[this] def react(map: Map[_, Flow[_]])(code: => Result[T]): Unit = {
    init(map)
    val p = map.values.toSeq
    val paramCode = (p, () => code)
    codeBlocks.append(paramCode)
  }

  protected[this] def react(map: DynamicOutput[_])(code: => Result[T]): Unit = {
    init(map)
    val p = map.values.toSeq
    val paramCode = (p, () => code)
    codeBlocks.append(paramCode)
  }

  protected[this] def react(list: List[Flow[_]])(code: => Result[T]): Unit = {
    init(list)
    val paramCode = (list, () => code)
    codeBlocks.append(paramCode)
  }

  // any of the input ticked, return true
  private def ticked(inputs: Flow[_]*): Boolean = {
    // use a simpler check for a common case of input size == 1
    if (inputs.length == 1) {
      inputs.head.isActive || inputs.head.isInstanceOf[Context]
    }
    else {
      inputs.isEmpty || inputs.exists(input => input.isActive || input.isInstanceOf[Context])
    }
  }
}