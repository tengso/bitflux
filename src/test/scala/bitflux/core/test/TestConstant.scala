package bitflux.core.test

import bitflux.core._
import bitflux.combinators.Implicits._
import bitflux.env.Simulation
import bitflux.test.util.Case
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class TestConstant extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6

  test("constant 1") {
    val input = Seq(
      time1 -> 1.0
    )

    val output = Seq(
      time1 -> 3.0
    )

    new Case(input, output) {
      lazy val test = (input: Flow[Double]) => {
        val one = Constant(1.0)
        val two = Constant(2.0)
        one + two
      }
    }
  }

  test("constant 2") {
    val input = Seq(
      time1 -> 2.0,
      time2 -> 3.0
    )

    val output = Seq(
      time1 -> 4.0,
      time2 -> 6.0
    )

    new Case(input, output) {
      lazy val test = (price: Flow[Double]) => {
        val two = Constant(2.0)
        price * two
      }
    }
  }

  test("constants") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val source = CurveSource(Curve(List(time2, time3), List(1, 2)))
        ((source + 1).setBufferSize(2),
          (source * 2).setBufferSize(2),
          (source - 1).setBufferSize(2),
          (source.toDoubles / 2.0).setBufferSize(2),
          (source > 10).setBufferSize(2),
          (source <= 1).setBufferSize(2),
          (source === 1).setBufferSize(2)
          )
      }
    }

    val res = Await.result(bt.res, 1000 millisecond)

    assert(res._1.collect.size === 2)
    assert(res._1.collect.map(_._1) === Vector(time2, time3))
    assert(res._1.collect.map(_._2) === Vector(2, 3))

    assert(res._2.collect.size === 2)
    assert(res._2.collect.map(_._1) === Vector(time2, time3))
    assert(res._2.collect.map(_._2) === Vector(2, 4))

    assert(res._3.collect.size === 2)
    assert(res._3.collect.map(_._1) === Vector(time2, time3))
    assert(res._3.collect.map(_._2) === Vector(0, 1))

    assert(res._4.collect.size === 2)
    assert(res._4.collect.map(_._1) === Vector(time2, time3))
    assert(res._4.collect.map(_._2) === Vector(0.5, 1))

    assert(res._5.collect.size === 2)
    assert(res._5.collect.map(_._1) === Vector(time2, time3))
    assert(res._5.collect.map(_._2) === Vector(false, false))

    assert(res._6.collect.size === 2)
    assert(res._6.collect.map(_._1) === Vector(time2, time3))
    assert(res._6.collect.map(_._2) === Vector(true, false))

    assert(res._7.collect.size === 2)
    assert(res._7.collect.map(_._1) === Vector(time2, time3))
    assert(res._7.collect.map(_._2) === Vector(true, false))
  }
}
