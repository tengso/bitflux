package bitflux.core.test

import scala.concurrent.duration._

import org.scalatest.FunSuite

import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.test.util.Case

class TestFlow extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6
  val time5 = time1 + Context.TimeStep * 8
  val time6 = time1 + Context.TimeStep * 10


  test("Mid") {
    case class Quote(bid: Double, ask: Double)

    val input = Seq(
      time1 -> Quote(ask = 101.0, bid = 100),
      time2 -> Quote(ask = 102.0, bid = 100),
      time3 -> Quote(ask = 103.0, bid = 100)
    )

    val output = Seq(
      time1 -> (101 + 100) / 2.0,
      time2 -> (102 + 100) / 2.0,
      time3 -> (103 + 100) / 2.0
    )

    new Case(input, output) {
      class Mid(quote: Flow[Quote]) extends Flow[Double] {
        react(quote) {
          (quote().bid + quote().ask) / 2
        }
      }

      lazy val test = (quote: Flow[Quote]) => {
        new Mid(quote)
      }
    }
  }

  test("VWAP") {
    case class Trade(price: Double, quantity: Double)

    val nRuns: Long = 100000
    val trades = (1L to nRuns).map(i => Trade(price = 100, quantity = 200)).toList
    val times = (1L to nRuns).map(i => time1 + i).toList

    val pq = for (trade <- trades) yield {
      trade.price * trade.quantity
    }

    val quantities = for (trade <- trades) yield {
      trade.quantity
    }

    val input = times.zip(trades)
    val output = Seq(times.last -> pq.sum / quantities.sum)

    new Case(input, output, 2000 millis) {
      def vwap(trade: Flow[Trade]): Flow[Double] = {
        val price = trade(_.price)
        val quantity = trade(_.quantity)

        (price * quantity).sum / quantity.sum
      }

      lazy val test = (trades: Flow[Trade]) => vwap(trades)
    }
  }

  test("max") {
    class Max(a: Flow[Double]) extends Flow[Double] {
      var m: Option[Double] = None
      react(a) {
        val mm = if (m.isEmpty) a() else m.get.max(a())
        m = Some(mm)
        mm
      }
    }

    val input = Seq(
      time1 -> 1.0,
      time2 -> 5.0,
      time3 -> 3.0
    )

    val output = Seq(
      time1 -> 1.0,
      time2 -> 5.0,
      time3 -> 5.0
    )

    new Case(input, output) {
      lazy val test = (input: Flow[Double]) => {
        new Max(input)
      }
    }
  }

  test("scheduler") {
    class A(val in: Flow[Int], set: Boolean) extends Flow[Int] {
      react(in) {
        if (set) {
          in()
        }
      }
    }

    class B(val in: Flow[Int]) extends Flow[Int] {
      react() {
        // should never be invoked
        assert(false)
      }
    }

    val input = Seq(
      time1 -> 1,
      time2 -> 2,
      time3 -> 3
    )

    val output = Seq[(Timestamp, Int)]()

    new Case(input, output) {
      lazy val test = (input: Flow[Int]) => {
        val a = new A(input, true)
        val aa = new A(a, false)
        new B(aa)
      }
    }
  }

  test("test inheritance") {
    val input = Seq(
      time1 -> 2,
      time2 -> 4
    )

    val output = Seq(
      time1 -> 6,
      time2 -> 12
    )

    class A(val inputs: Flow[Int]) extends Flow[Int] {
      react(inputs) {
        inputs() * getMultiplier
      }

      def getMultiplier = 2
    }

    class BOM(val in: Flow[Int]) extends A(in) {
      override def getMultiplier = 3
    }

   new Case(input, output) {
      lazy val test = (input: Flow[Int]) => {
        new BOM(input)
      }
    }
  }

  test("last") {
    val input = Seq(
      time1 -> Seq(1),
      time2 -> Seq(),
      time3 -> Seq(4, 5)
    )

    val output = Seq(
      time1 -> 1,
      time3 -> 5
    )

    new Case(input, output) {
      lazy val test = (input: Flow[Seq[Int]]) => {
        input.last
      }
    }
  }
}