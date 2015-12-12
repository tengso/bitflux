package bitflux.core

import scala.collection.mutable.ArrayBuffer

trait ReactiveFlow[+T] { self: Flow[T] =>
  
//  private[this] val codeBlocks = new ArrayBuffer[(Seq[Flow[_]], () => Option[T])]
  private[this] val codeBlocks = new ArrayBuffer[(Seq[Flow[_]], () => Result[T])]
  private[this] var rank: Int = _

  private[core] def setRank(rank: Int): Unit = this.rank = rank

  private[core] def invoke(): Boolean = {
    val start = System.nanoTime

    var hasNewValue = false
    
    if (codeBlocks.nonEmpty) {
      for ((p, code) <- codeBlocks) {
        if (ticked(p: _*)) {
          val result = code()
          result match {
            case  ResultValue(v) =>
              setValue(v)
              hasNewValue = true
            case ResultUnit =>
          }
        }
      }
    }
    
    logger.trace(s"took: ${(System.nanoTime - start) / 1000}")
    
    hasNewValue
  }
  
  protected[core] def getRank = rank

  protected[core] def hasValue(p: Flow[_]*): Boolean = 
    p.forall(!_.getLastValue.isEmpty)

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
    inputs.isEmpty || inputs.exists(input => input.isActive || input.isInstanceOf[Context])
  }
}