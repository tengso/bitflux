package bitflux.core


object Curve {
  
  def apply[T](time: List[Timestamp], data: List[T]) = new Curve(time, data)

  def apply[T](time: Seq[(Timestamp, T)]): Curve[T] = {
    val (times, data) = time.foldRight((List[Timestamp](), List[T]()))((a, b) => (a._1 :: b._1, a._2 :: b._2))
    new Curve(times, data)
  }

  def empty[T] = new Curve(List(), List[T]())

  implicit def convertFromCurve[T](input: Curve[T])(implicit context: Context) = CurveSource[T](input)
}

// "time" might be unsorted
class Curve[T] private (time: List[Timestamp], data: List[T]) {
  
  assert(time.size == data.size, s"${time.size} != ${data.size}")

  private val map = time.zip(data).toMap
  private val keys = time.sorted
  private val pairs = keys.map(time => (time, map(time))).toIndexedSeq

  val size: Int = keys.size

  def isEmpty: Boolean = keys.isEmpty
  def nonEmpty: Boolean = keys.nonEmpty

  def values: List[T] = data
  
  def foreach(f: ((Timestamp, T)) => Unit) = pairs.foreach(f)

  def get(index: Int): Option[(Timestamp, T)] =
    if (size >= index + 1) Some(pairs(index)) else None

  def apply(index: Int): (Timestamp, T) = get(index).get

  // with interpolate, 
  // FIXME: be more efficient
  def apply(time: Timestamp): Option[T] = {
    if (keys.isEmpty) None
    else {
      if (size == 1) {
        if (time >= keys.head) map.get(keys.head) else None
      }
      else {
        val pairs = keys.zip(keys.tail)
        val f = pairs.find(pair => time >= pair._1 && time < pair._2)
        if (f.nonEmpty) {
          map.get(f.get._1)
        }
        else {
          if (lastOption.get._1 <= time) Some(lastOption.get._2) else None
        }
      }
    }
  }

  def headOption: Option[(Timestamp, T)] =
    if (size > 0) get(0) else None

  def lastOption: Option[(Timestamp, T)] =
    if (size > 0) get(size - 1) else None

  override def toString = map.toString()
}

