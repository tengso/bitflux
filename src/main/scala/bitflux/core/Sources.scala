package bitflux.core

case class NewData[T](source: Source[T], timestamp: Timestamp)

trait Source[T] { self: Flow[T] =>
  override def isSource = true
}

trait SimulationSource[T] extends Source[T] { self: Flow[T] =>
  def next(start: Timestamp, end: Timestamp): Option[(Timestamp, T)]
}

trait RealtimeSource[T] extends Source[T] { self: Flow[T] =>
  def subscribe(start: Timestamp, end: Timestamp): Unit
}

class IteratorSource[T](it: Iterator[(Timestamp, T)])(implicit context: Context)
  extends Flow[T] with SimulationSource[T] {
  var nextValue: Option[T] = None
  var index = 0

  react(context) {
    if (nextValue.nonEmpty) {
      nextValue.get
    }
  }

  def next(start: Timestamp, end: Timestamp): Option[(Timestamp, T)] = {
    if (it.hasNext) {
      val (time, value) = it.next()
      if (time <= end && time >= start) {
        nextValue = Some(value)
        Some((time, value))
      }
      else None
    }
    else {
      nextValue = None
      None
    }
  }
}

trait Driver {
  def subscribe(start: Timestamp, end: Timestamp, source: BaseRealtimeSource[_, _]): Unit
}

abstract class BaseRealtimeSource[I, O](context: Context) extends Flow[O] with RealtimeSource[O] {
  
  protected val driver: Driver
  
  protected def generateOutput(incoming: java.util.Iterator[I]): O
  
  private val incoming = new java.util.concurrent.LinkedBlockingQueue[I]
  private val outgoing = new java.util.concurrent.LinkedBlockingQueue[I]
  
  private var start: Timestamp = _
  private var end: Timestamp = _
  
  override def subscribe(start: Timestamp, end: Timestamp) {
    this.start = start
    this.end = end
    
    driver.subscribe(start, end, this)   
    context.executionContext.execute(new Listener)
  }
  
  def enqueue(data: I) {
    incoming.put(data)
  }
  
  react(context) {
    val container = new java.util.ArrayList[I]
    val size = outgoing.drainTo(container)
    if (size > 0) {
      generateOutput(container.iterator)
    }
  }
  
  class Listener extends Runnable {
    
    // FIXME: remove while loop
    override def run() {
      var now = Timestamp.now
      while (now < end) {
        val data = incoming.poll(end.units - now.units, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (data != null) {
          outgoing.put(data)
          context.asInstanceOf[RealtimeRunner].enqueue((BaseRealtimeSource.this, now))
        }
        now = Timestamp.now
      }
    }
  }
}