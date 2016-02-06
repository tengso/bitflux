package bitflux.core.test

import bitflux.core._
import bitflux.combinators.Implicits._
import bitflux.env.Simulation
import bitflux.test.util.Case
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class TestLift extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4

  test("lift field") {
    case class Position(quantity: Int, notional: Double)

    val pos1 = Position(quantity = 2, notional = 100.0)
    val pos2 = Position(quantity = 4, notional = 200.0)

    val input = Seq(
      time1 -> pos1,
      time2 -> pos2
    )

    val output = Seq(
      time1 -> 4,
      time2 -> 8
    )

    new Case(input, output) {
      lazy val test = (positions: Flow[Position]) => {
        val quantities = positions(_.quantity)
        quantities * 2
      }
    }
  }

  test("lift 2") {
    case class Trade(price: Double, quantity: Int)

    val input = Seq(
      time1 -> Trade(99.01, 100),
      time2 -> Trade(99.02, 200),
      time3 -> Trade(99.03, 300)
    )

    val bt = new Simulation(time1, time3) {
      val res = run {
        val in = CurveSource(Curve(input))

        val price = in(_.price).setBufferSize(3)
        val quantity = in(_.quantity).setBufferSize(3)
        (price, quantity)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond)._1.collect

    assert(result.size === 3)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 99.01)

    assert(result(1)._1 === time2)
    assert(result(1)._2 === 99.02)

    assert(result(2)._1 === time3)
    assert(result(2)._2 === 99.03)

    val resultQuantity = Await.result(bt.res, 1000 millisecond)._2.collect

    assert(resultQuantity.size === 3)
    assert(resultQuantity(0)._1 === time1)
    assert(resultQuantity(0)._2 === 100)

    assert(resultQuantity(1)._1 === time2)
    assert(resultQuantity(1)._2 === 200)

    assert(resultQuantity(2)._1 === time3)
    assert(resultQuantity(2)._2 === 300)
  }

  test("chained") {
    case class Bid(price: Double, quanitty: Int)
    case class Ask(price: Double, quanitty: Int)
    case class Quote(bid: Bid, ask: Ask)

    val curve = Curve(
      List(time1, time2, time3),
      List(
        Quote(Bid(99.01, 100), Ask(99.02, 100)),
        Quote(Bid(99.02, 200), Ask(99.03, 200)),
        Quote(Bid(99.03, 300), Ask(99.04, 300))))

    val bt = new Simulation(time1, time3) {
      val res = run {
        val in = CurveSource(curve)

        in(_.bid)(_.price).setBufferSize(3)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size === 3)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 99.01)

    assert(result(1)._1 === time2)
    assert(result(1)._2 === 99.02)

    assert(result(2)._1 === time3)
    assert(result(2)._2 === 99.03)
  }
}
