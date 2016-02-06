package bitflux.core.test

import bitflux.core._
import bitflux.combinators.Implicits._
import bitflux.test.util.Case
import org.scalatest.FunSuite

class TestPlaceholder extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2

  test("placeholder") {
    val input = Seq(
      time1 -> 1,
      time2 -> 2
    )

    val output = Seq(
      time1 -> 2,
      time2 -> 4
    )

    new Case(input, output) {
      lazy val test = (source: Flow[Int]) => {
        val out = new Placeholder[Int]
        val result = out * Constant(2)
        out.setInput(source)
        result
      }
    }
  }

}
