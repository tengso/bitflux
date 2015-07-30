package bitflux

import scala.math.Numeric.Implicits._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import bitflux.core._

import bitflux.combinators.Implicits._
import bitflux.core.Implicits._

package object combinators {

  def demux[T](inputs: Flow[Seq[T]], dem: T => Option[String]): DynamicOutput[T] = {
    val outputs = new DynamicOutput[T]()(inputs.getContext)
    val values = mutable.Map[String, ListBuffer[T]]()

    val base = new Flow[Seq[T]] {
      react(inputs) {
        values.clear()

        inputs() foreach { input =>
          dem(input) match {
            case Some(key) => {
              if (!outputs.contains(key)) {
                val output = new DynamicPlaceholder[Seq[T]]
                outputs.add(key, output)
                // FIXME 
                output.setParent(this)
              }
              val value = values.getOrElseUpdate(key, new ListBuffer[T]())
              value.append(input)
            }
            case None => {}
          }
        }

        var res: Option[Seq[T]] = None
        values.keys foreach { key =>
          val value = values(key).toList
          res = Some(value)
          setValue(value)
          val output = outputs(key)
          // FIXME:
          output match {
            case dynamic: DynamicPlaceholder[Seq[T]] => dynamic.invokeDirectly()
          }
        }
        res
      }

      override def toString = "bitflux.combinators.demux"
    }
    outputs.addBase(base)
    outputs
  }

  def last[S, E](input: Flow[S])(implicit ev: SeqLike[S, E]) = new Flow[E] {
    react(input) {
      ev.lastOption(input()) match {
        case Some(value) => value
        case None =>
      }
    }

    override def toString = "bitflux.combinators.last"
  }

  def logger[T](input: Flow[T]) = new Flow[T] {
    react(input) {
      logger.warn("log: " + input())
      input()
    }

    override def toString = "bitflux.combinators.logger"
  }

  def flatten[T](inputs: List[Flow[Seq[T]]]) = new Flow[Seq[T]] {
    react(inputs) {
      inputs.filter(_.isActive).flatMap(input => input()).toSeq
    }

    override def toString = "bitflux.combinators.flatten"
  }

  def mux[T](inputs: DynamicOutput[T]): Flow[Seq[T]] = new Flow[Seq[T]] {
    inputs.addChild(this)

    react(inputs) {
      inputs.keys.filter(inputs(_).isActive).flatMap(inputs(_)()).toSeq
    }

    override def toString = "bitflux.combinators.mux"
  }

  type ExtendedFlow[I, O] = (Seq[I], Flow[Seq[I]]) => Flow[Seq[O]]
  type ExtendedFlowMap[I, O] = Seq[I] => Option[ExtendedFlow[I, O]]
  type KeyMap[I] = I => Option[String]

  def fork[I, O](input: Flow[Seq[I]], keyMap: KeyMap[I], flowMap: ExtendedFlowMap[I, O]): Flow[Seq[O]] = {
    def forkImpl[I, O](input: DynamicOutput[I])(flowMap: ExtendedFlowMap[I, O])(implicit context: Context) = {
      val output = new DynamicOutput[O]()

      new Flow[I] {
        react(input) {
          input.newKeys foreach { key =>
            logger.debug(s"new key: $key")
            val init = input(key)()
            val demuxed = input(key)

            extendFlow(demuxed) {
              flowMap(init) match {
                case Some(graph) => {
                  val result = graph(init, demuxed)
                  output.add(key, result)
                }
                case _ => {
                  logger.error(s"$init not found")
                  throw new Exception("stop")
                }
              }
            }
          }
        }

        override def toString = "bitflux.combinators.fork"
      }

      bitflux.combinators.mux(output)
    }
    
    val demuxed = demux(input, keyMap)
    forkImpl(demuxed)(flowMap)(input.getContext)
  }

  def toSeq[T](input: Flow[T]) = new Flow[Seq[T]] {
    react(input) {
      Seq(input())
    }

    override def toString = "bitflux.combinators.toSeq"
  }

  def map[S, T](input: Flow[S], f: S => T) = new Flow[T] {
    react(input) {
      f(input())
    }

    override def toString = "bitflux.combinators.map"
  }

  def filter[T](input: Flow[T], f: T => Boolean) = new Flow[T] {
    react(input) {
      val v = input()
      if (f(v)) {
        input()
      }
    }

    override def toString = "bitflux.combinators.map"
  }

  def sum[T: Numeric](p: Flow[T]) = new Flow[Double] {
    var cum = 0.0

    react(p) {
      cum += p().toDouble
      cum
    }

    override def toString = "bitflux.combinators.sum"
  }

  def times[T](p1: Flow[T], p2: Flow[T])(implicit num: Numeric[T]) = new Flow[T] {
    react(p1, p2) {
      if (hasValue(p1, p2)) {
        num.times(p1(), p2())
      }
    }

    override def toString = "bitflux.combinators.times"
  }

  def plus[T](p1: Flow[T], p2: Flow[T])(implicit num: Numeric[T]) = new Flow[T] {
    react(p1, p2) {
      if (hasValue(p1, p2)) {
        num.plus(p1(), p2())
      }
    }

    override def toString = "bitflux.combinators.plus"
  }

  def minus[T](p1: Flow[T], p2: Flow[T])(implicit num: Numeric[T]) = new Flow[T] {
    react(p1, p2) {
      if (hasValue(p1, p2)) {
        num.minus(p1(), p2())
      }
    }

    override def toString = "bitflux.combinators.minus"
  }

  def toDouble[T: Numeric](p: Flow[T]) = new Flow[Double] {
    react(p) {
      p().toDouble
    }

    override def toString = "bitflux.combinators.toDouble"
  }

  def toInt[T: Numeric](p: Flow[T]) = new Flow[Int] {
    react(p) {
      p().toInt
    }

    override def toString = "bitflux.combinators.toInt"
  }

  def div[T: Numeric](p1: Flow[T], p2: Flow[T]) = new Flow[Double] {
    react(p1, p2) {
      if (hasValue(p1, p2)) {
        p1().toDouble / p2().toDouble
      }
    }

    override def toString = "bitflux.combinators.div"
  }

  def min[T: Ordering](input: Flow[T])(implicit ord: Ordering[T]) = new Flow[T] {
    var minValue: Option[T] = None

    react(input) {
      minValue = minValue match {
        case Some(value) => Some(ord.min(value, input()))
        case None => Some(input())
      }
      minValue.get
    }

    override def toString = "bitflux.combinators.min"
  }

  def min2[T: Ordering](input1: Flow[T], input2: Flow[T])(implicit ord: Ordering[T]) = new Flow[T] {
    react(input1, input2) {
      if (input1.nonEmpty && input2.nonEmpty) {
        ord.min(input1(), input2())
      }
    }

    override def toString = "bitflux.combinators.min2"
  }

  def max[T: Ordering](input: Flow[T])(implicit ord: Ordering[T]) = new Flow[T] {
    var minValue: Option[T] = None

    react(input) {
      minValue = minValue match {
        case Some(value) => Some(ord.max(value, input()))
        case None => Some(input())
      }
      minValue.get
    }

    override def toString = "bitflux.combinators.max"
  }

  def max2[T: Ordering](input1: Flow[T], input2: Flow[T])(implicit ord: Ordering[T]) = new Flow[T] {
    react(input1, input2) {
      if (input1.nonEmpty && input2.nonEmpty) {
        ord.max(input1(), input2())
      }
    }

    override def toString = "bitflux.combinators.max2"
  }

  def count[T](input: Flow[T]) = new Flow[Double] {
    var count = 0

    react(input) {
      count += 1
      count
    }

    override def toString = "bitflux.combinators.count"
  }

  def mean[T: Numeric](input: Flow[T]) = sum(input) / count(input)

  def log(input: Flow[Double]) = new Flow[Double] {
    react(input) {
      scala.math.log(input())
    }

    override def toString = "bitflux.combinators.ln"
  }

  def sqrt(input: Flow[Double]) = new Flow[Double] {
    react(input) {
      scala.math.sqrt(input())
    }

    override def toString = "bitflux.combinators.sqrt"
  }

  def exp(input: Flow[Double]) = new Flow[Double] {
    react(input) {
      scala.math.exp(input())
    }

    override def toString = "bitflux.combinators.exp"
  }

  def gt[T](a: Flow[T], b: Flow[T])(implicit ord: Ordering[T]) = new Flow[Boolean] {
    react(a, b) {
      if (hasValue(a, b)) {
        ord.gt(a(), b())
      }
    }

    override def toString = "bitflux.combinators.>"
  }

  def gteq[T](a: Flow[T], b: Flow[T])(implicit ord: Ordering[T]) = new Flow[Boolean] {
    react(a, b) {
      if (hasValue(a, b)) {
        ord.gteq(a(), b())
      }
    }

    override def toString = "bitflux.combinators.>="
  }

  def lt[T](a: Flow[T], b: Flow[T])(implicit ord: Ordering[T]) = new Flow[Boolean] {
    react(a, b) {
      if (hasValue(a, b)) {
        ord.lt(a(), b())
      }
    }

    override def toString = "bitflux.combinators.<"
  }

  def lteq[T](a: Flow[T], b: Flow[T])(implicit ord: Ordering[T]) = new Flow[Boolean] {
    react(a, b) {
      if (hasValue(a, b)) {
        ord.lteq(a(), b())
      }
    }

    override def toString = "bitflux.combinators.<="
  }

  def equiv[T](a: Flow[T], b: Flow[T])(implicit ord: Ordering[T]) = new Flow[Boolean] {
    react(a, b) {
      if (hasValue(a, b)) {
        ord.equiv(a(), b())
      }
    }

    override def toString = "bitflux.combinators.=="
  }

  def If[T](l: Flow[Boolean], a: Flow[T], b: Flow[T]) = new Flow[T] {
    react(l, a, b) {
      if (hasValue(l, a, b)) {
        if (l()) a() else b()
      }
    }

    override def toString = "bitflux.combinators.if"
  }

  def branch[T, K](input: Flow[T], keys: Set[K])(f: T => K): Map[K, Flow[T]] = {
    val flow = new Flow[T] {
      val outputs = keys.map(_ -> new Output[T](this)).toMap

      react(input) {
        val v = input()
        setValue(v)
        val k = f(v)
        outputs.get(k).flatMap(_.pullFromParent())
      }

      override def toString = "bitflux.combinators.branch"
    }

    flow.outputs
  }

  def merge[K, T](flows: Map[K, Flow[T]]): Flow[Map[K, T]] = {
    new Flow[Map[K, T]] {
      val keys = flows.keys

      react(flows) {
        var output = Map[K, T]()
        keys.foreach { key =>
          if (flows(key).isActive) {
            output += (key -> flows(key)())
          }
        }
        output
      }
    }
  }
}

