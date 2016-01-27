package bitflux.core

import scala.concurrent.duration._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

class Flow[+T] extends ExtensibleFlow[T] with ReactiveFlow[T] {

  trait Result[+S]
  case class ResultValue[S](value: S) extends Result[S]
  case object ResultUnit extends Result[Nothing]

  protected implicit def valueToSome[S](value: S): ResultValue[S] = ResultValue(value)
  protected implicit def unitToNone[S](value: Unit): Result[S] = ResultUnit

  private[this] val curve = new ArrayBuffer[(Timestamp, T)]()
  lazy val logger = LoggerFactory.getLogger(toString)

  private val children = new ArrayBuffer[Flow[_]]
  private val parents = new ArrayBuffer[Flow[_]]
  lazy private val context: Context = findContext(this).asInstanceOf[Context]
  private val connected = new java.util.IdentityHashMap[Flow[_], Int]

  private var bufferSize = 1

  def isSource = false

  private[bitflux] def getContext = context
  private[core] def getChildren = children.toSeq
  // toSeq is slow, cache the result?
  private[core] def getParents = parents

  private[core] def addChild(child: Flow[_]) = if (!children.contains(child)) children.append(child)
  private[core] def addParent(parent: Flow[_]) = {
    if (!parents.contains(parent)) {
       logger.debug(s"connected to parent: [$parent]")
      parents.append(parent)
    }
  }

  // experimental
  private[core] def removeTick(t: Timestamp): Unit = {
    var break = false

    while (curve.nonEmpty && !break) {
      getLastTick match {
        case Some((time, _)) => {
          if (time == t) {
            curve.remove(curve.size - 1)
            break
          } else {
            break = true
          }
        }
        case None => break = true
      }
    }
  }

  def setBufferSize(size: Int): Flow[T] =  {
    bufferSize = size
    this
  }

  def getLastTick: Option[(Timestamp, T)] = curve.lastOption

  def isEmpty: Boolean = getLastTick.isEmpty
  
  def nonEmpty: Boolean = !isEmpty

  def isActive: Boolean = curve.nonEmpty && curve.last._1 == now

  def collect: Vector[(Timestamp, T)] = curve.toVector
  
  def getAll: Vector[(Timestamp, T)] = collect

  def getLastValue: Option[T] = {
    val lastTick = getLastTick
    if (lastTick.nonEmpty) Some(lastTick.get._2) else None
  }

  def apply() = getLastValue.get

  def map[S](f: T => S): Flow[S] = {
    val self = this

    new Flow[S] {
      react(self) {
        f(self())
      }

      override def toString = "bitflux.core.map"
    }
  }

  def apply[S](f: T => S): Flow[S] = map(f)

  def pre[S >: T](default: S): Flow[S] = preImpl(Some(default))
  def pre: Flow[T] = preImpl(None)

  private def preImpl[S >: T](default: Option[S]): Flow[S] = {
    val self = this

    new Flow[S] {
      var p: Option[S] = None

      react(self) {
        val output = if (p.nonEmpty) p else if (default.nonEmpty) default else None
        p = Some(self())
        if (output.nonEmpty) {
          output.get
        }
      }

      override def toString = "bitflux.core.pre"
    }
  }

  protected[this] def setValue(value: T): Unit = {
    val tick = (context.getCurrentTime.get, value)

    if (curve.isEmpty || curve.size < bufferSize) {
      curve.append(tick)
    }
    else {
      if (bufferSize == 1) {
        curve(0) = tick
      }
      else {
        curve.remove(0)
        curve.append(tick)
      }
    }

//    logger.debug(s"set new value: $value")
  }

  // experiment
  // TODO:: check performance
  protected[this] def addValue[Element](elem: Element)
                                       (implicit ev: Seq[Element] =:= T, ev2: T =:= Seq[Element]): Unit = {
    if (curve.nonEmpty && curve.last._1 == now) {
      val value = curve.last._2
      val newValue = value ++ Seq(elem)
      curve.remove(curve.size - 1)
      curve.append((now, newValue))
    } else {
      curve.append((now, Seq(elem)))
    }
  }

  // experiment
  // TODO:: check performance
  protected[this] def setValue[S <: T](output: Output[S], value: S): Unit = {
    setValue(value)
    output.pullFromParent()
  }

  // experiment
  // TODO: check performance
  protected[this] def addValue[S <: T, Element](output: Output[S], elem: Element)
                                               (implicit ev: T =:= Seq[Element], ev2: Seq[Element] =:= T): Unit = {
    // remove the current tick, if there is one already
    val current = getLastTick
    if (current.nonEmpty) {
      if (current.get._1 == now) {
        curve.remove(curve.size - 1)
      }
    }

    val existing = output.getLastTick
    if (existing.nonEmpty && existing.get._1 == now) {
      // move the value from output flow to this flow
      setValue(existing.get._2)
      output.removeTick(now)
    }

    addValue(elem)
    output.pullFromParent()
  }

  private[core] def init(inputs: Map[_, Flow[_]]): Unit = inputs.values.foreach(addInput)

  private[core] def init(inputs: DynamicOutput[_]): Unit = inputs.values.foreach(addInput(_))

  private[core] def init(inputs: List[Flow[_]]): Unit = inputs.foreach(addInput)

  private[core] def init(inputs: Flow[_]*): Unit = inputs.foreach(addInput)

  private[core] def addInput(input: Flow[_]): Unit = {
    if (!isInstanceOf[Placeholder[T]] && !connected.containsKey(input) && !input.isInstanceOf[Timer]) {
      input.addChild(this)
      addParent(input)
      connected.put(input, 1)
    }
  }

  /*
  private def getInputs = {
    import scala.reflect.runtime.{universe => ru}
    val m = ru.runtimeMirror(getClass.getClassLoader)
    val symbol = m.reflect(this).symbol
    val typ = symbol.toType
    val cons = typ.declaration(ru.nme.CONSTRUCTOR).asMethod
    for(params <- cons.paramss) {
      for(param <- params) {
        println(param.fullName)
        val base = ru.typeOf[Flow[_]]
        println(param.typeSignature.weak_<:<(base))
        // println(param.typeSignature.isInstanceOf[Flow[_]])
      }
    }
  }
  */

  private[this] def findContext(flow: Flow[_]): Flow[_] = if (flow.getParents.isEmpty) flow else findContext(flow.parents(0))

  protected[this] def now: Timestamp = getContext.getCurrentTime.get
  
  protected[this] def prior: Option[Timestamp] = getContext.getLastTime

  // TODO: interval must be greater than zero 
  protected[this] def setTimer(interval: Int, timer: Timer): Unit = {
    extendFlow(timer) {
      val t = new Alarm(now + (interval millis))(getContext)
      timer.setInput(t)
      addParent(timer)
      timer.addChild(this)
    }
  }
  
  override def toString = getClass.getName
}





