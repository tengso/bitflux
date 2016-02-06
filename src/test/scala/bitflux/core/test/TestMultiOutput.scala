package bitflux.core.test

import bitflux.core.{Context, Timestamp, Curve, Flow}
import bitflux.env.Simulation
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class TestMultiOutput extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2

  test("multiple-outputs") {
    case class Trade(price: Double, quantity: Int)

    def volumeAndVWAP(trades: Flow[Trade]): (Flow[Double], Flow[Int]) = {
      val quantities = new Flow[Int] {
        var sumQuantity = 0

        react(trades) {
          sumQuantity += trades().quantity
          sumQuantity
        }
      }

      val vwap = new Flow[Double] {
        var sum = 0.0

        react(trades, quantities) {
          sum += trades().price * trades().quantity
          sum / quantities()
        }
      }

      (vwap.setBufferSize(2), quantities.setBufferSize(2))
    }

    val trades = Curve(
      List(
        time1 -> Trade(100.0, 100),
        time2 -> Trade(200.0, 100)
      ))

    val sim = new Simulation(time1, time2) {
      val res = run {
        volumeAndVWAP(trades)
      }
    }.res

    val res = Await.result(sim, 1000 millisecond)

    assert(res._1.collect.size === 2)
    assert(res._1.collect(0)._1 === time1)
    assert(res._1.collect(0)._2 === 100)
    assert(res._1.collect(1)._1 === time2)
    assert(res._1.collect(1)._2 === 150.0)

    assert(res._2.collect.size === 2)
    assert(res._2.collect(0)._1 === time1)
    assert(res._2.collect(0)._2 === 100)
    assert(res._2.collect(1)._1 === time2)
    assert(res._2.collect(1)._2 === 200)
  }
}
