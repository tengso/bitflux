package bitflux.core.test

import bitflux.core.{Context, Timestamp, Curve, CurveSource}
import bitflux.env.Simulation
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class TestBufferSize extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6

  test("buffer size 1") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val source = CurveSource(Curve(List(time2, time3, time4), List(1, 2, 3)))
        source.setBufferSize(2)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size == 2)
    assert(res.map(_._1) === Vector(time3, time4))
    assert(res.map(_._2) === Vector(2, 3))
  }

  test("buffer size 2") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val source = CurveSource(Curve(List(time2, time3, time4), List(1, 2, 3)))
        source.setBufferSize(4)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size == 3)
    assert(res.map(_._1) === Vector(time2, time3, time4))
    assert(res.map(_._2) === Vector(1, 2, 3))
  }

}
