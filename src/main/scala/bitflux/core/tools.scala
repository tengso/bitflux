package bitflux.core

object tools {
 def timeIt[T](code: => T): (T, Double) = {
    val start = System.nanoTime()
    val result = code
    val end = System.nanoTime()
    (result, start - end)
  }
}