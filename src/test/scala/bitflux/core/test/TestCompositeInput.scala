package bitflux.core.test

import bitflux.core._
import bitflux.env.Simulation
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class TestCompositeInput extends FunSuite {

  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4

  test("seq input") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        class A(val inputs: List[Flow[Int]], val value: List[Int]) extends Flow[Int] {
          react(inputs) {
            inputs.map(_()).sum
          }
        }

        val a = CurveSource(Curve(List(time1, time2, time3), List(3, 2, 5)))
        val b = CurveSource(Curve(List(time1, time2), List(2, 4)))

        new A(List(a, b), List(1, 2)).setBufferSize(3)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size == 3)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 5)
    assert(result(1)._1 === time2)
    assert(result(1)._2 === 6)
    assert(result(2)._1 === time3)
    assert(result(2)._2 === 9)
  }

  test("map input") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        class A(val inputs: Map[String, Flow[Int]], value: Map[Int, Int]) extends Flow[Int] {
          react(inputs) {
            inputs("a")() + inputs("b")()
          }
        }

        val a = CurveSource(Curve(List(time1, time2, time3), List(3, 2, 5)))
        val b = CurveSource(Curve(List(time1, time2), List(2, 4)))

        val input = Map("a" -> a, "b" -> b)
        new A(input, Map(1 -> 1)).setBufferSize(3)
      }
    }
    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size == 3)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 5)
    assert(result(1)._1 === time2)
    assert(result(1)._2 === 6)
    assert(result(2)._1 === time3)
    assert(result(2)._2 === 9)
  }

}
