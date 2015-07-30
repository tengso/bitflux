package bitflux.core.test
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

import com.github.nscala_time.time.Imports._
import org.scalatest.FunSuite

import bitflux.core.Implicits._
import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.env._

class TestFlow extends FunSuite {
  val time1 = new DateTime(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6
  val time5 = time1 + Context.TimeStep * 8
  val time6 = time1 + Context.TimeStep * 10

  val timesTrades = List(time1, time2, time3)

  case class Trade(price: Double, quantity: Double)
  case class Quote(bid: Double, ask: Double)

  val trades = List(
    Trade(price = 100, quantity = 200),
    Trade(price = 150, quantity = 100),
    Trade(price = 150, quantity = 100))

  val quotes = List(
    Quote(ask = 101.0, bid = 100), Quote(ask = 102.0, bid = 100),
    Quote(ask = 103.0, bid = 100), Quote(ask = 107.0, bid = 100),
    Quote(ask = 103.0, bid = 100), Quote(ask = 107.0, bid = 100))

  test("Mid") {
    class Mid(quote: Flow[Quote]) extends Flow[Double] {
      react(quote) {
        (quote().bid + quote().ask) / 2
      }
    }

    val quotes = List(
      Quote(ask = 101.0, bid = 100),
      Quote(ask = 102.0, bid = 100),
      Quote(ask = 103.0, bid = 100))
    val timesQuotes = List(time2, time3, time4)

    val bt = new Simulation(time1, time4) {
      val result = run {
        val quote = Curve(timesQuotes, quotes)
        new Mid(quote)
      }
    }.result

    val res = Await.result(bt, 1000 millisecond).collect

    assert(res.size === 3)
    assert(res(0)._1 === time2)
    assert(res(0)._2 === (101 + 100) / 2.0)
    assert(res(1)._1 === time3)
    assert(res(1)._2 === (102 + 100) / 2.0)
    assert(res(2)._1 === time4)
    assert(res(2)._2 === (103 + 100) / 2.0)
  }

  test("VWAP") {
    val trades = List(
      Trade(price = 100.0, quantity = 200),
      Trade(price = 150.0, quantity = 100),
      Trade(price = 150.0, quantity = 100),
      Trade(price = 150.0, quantity = 100),
      Trade(price = 150.0, quantity = 100))

    val timesTrades = List(time1, time2, time3, time3 + 1, time3 + 2)

    def vwap(trade: Flow[Trade]): Flow[Double] = {
      val price = trade(_.price)
      val quantity = trade(_.quantity)

      (price * quantity).sum / quantity.sum
    }

    val bt = new Simulation(time1, time3 + 2) {
      val res = run {
        val trade = Curve(timesTrades, trades)
        vwap(trade)
      }
    }

    val pq = for (trade <- trades) yield {
      trade.price * trade.quantity
    }

    val quantities = for (trade <- trades) yield {
      trade.quantity
    }

    val res = Await.result(bt.res, 1000 millisecond)()
    
    assert(res === pq.sum / quantities.sum)
  }

  test("max") {
    val times = List(time1, time2, time3)
    val values = List(1.0, 5.0, 3.0)

    class Max(a: Flow[Double]) extends Flow[Double] {
      var m: Option[Double] = None
      react(a) {
        val mm = if (m.isEmpty) a() else m.get.max(a())
        m = Some(mm)
        mm
      }
    }

    val bt = new Simulation(time1, time3) {
      val res = run {
        new Max(Curve(times, values))
      }
    }
    
    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 3)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 5)
    assert(res(2)._1 === time3)
    assert(res(2)._2 === 5)

  }
  
  test("constant 1") {
    val bt = new Simulation(time1, time2) {
      val res = run {
        val one = Constant(1.0)
        val two = Constant(2.0)
        one + two
      }
    }
    assert(Await.result(bt.res, 1000 millisecond)() === 3.0)
  }

  test("constant 2") {
    import bitflux.core.Implicits._
    val bt = new Simulation(time1, time2) {
      val res = run {
        val price = CurveSource(Curve(List(time1, time2), List(2.0, 3.0)))
        val two = Constant(2.0)
        price * two
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect;
    assert(res.size === 2)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 4)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 6)
  }

  test("lift field") {
    case class Position(val quantity: Int, val notional: Double)

    val pos1 = Position(quantity = 2, notional = 100.0)
    val pos2 = Position(quantity = 4, notional = 200.0)

    val bt = new Simulation(time1, time2) {
      val res = run {
        val positions = CurveSource(Curve(List(time1, time2), List(pos1, pos2)))
        val quantities = positions(_.quantity)
        quantities * Constant(2)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect
    assert(res.size === 2)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 4)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 8)
  }

  test("placeholder") {
    val bt = new Simulation(time1, time2) {
      val res = run {
        val out = new Placeholder[Int]
        val source = Curve(List(time1, time2), List(1, 2))
        val result = out * Constant(2)
        out.setInput(source)
        result
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 2)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 2)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 4)
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

    val bt = new Simulation(time1, time3) {
      val res = run {
        val s = Curve(List(time1, time2, time3), List(1, 2, 3))

        val a = new A(s, true)
        val aa = new A(a, false)
        new B(aa)
      }
    }

    assert(Await.result(bt.res, 1000 millisecond).collect.size === 0)
  }

  test("seq input") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        class A(val inputs: List[Flow[Int]], val value: List[Int]) extends Flow[Int] {
          react(inputs) {
            inputs map (x => x()) sum
          }
        }

        val a = CurveSource(Curve(List(time1, time2, time3), List(3, 2, 5)))
        val b = CurveSource(Curve(List(time1, time2), List(2, 4)))

        new A(List(a, b), List(1, 2))
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
        new A(input, Map(1 -> 1))
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

  test("test inheritance 1") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        class A(val inputs: Flow[Int]) extends Flow[Int] {
          react(inputs) {
            inputs() * getMultiplier
          }

          def getMultiplier = 2
        }

        class BOM(val in: Flow[Int]) extends A(in) {
          override def getMultiplier = 3
        }

        val c = Curve(List(time1, time2), List(2, 4))
        new BOM(c)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size === 2)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 6)
    assert(result(1)._1 === time2)
    assert(result(1)._2 === 12)
  }

  test("last") {
    val curve = Curve(
      List(time1, time2, time3),
      List(Seq(1), Seq(), Seq(4, 5)))

    val bt = new Simulation(time1, time3) {
      val res = run {
        val in = CurveSource[Seq[Int]](curve)
        in.last
      }
    }
    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size === 2)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 1)
    assert(result(1)._1 === time3)
    assert(result(1)._2 === 5)
  }

  test("product 2") {
    case class Trade(price: Double, quantity: Int)

    val curve = Curve(
      List(time1, time2, time3),
      List(Trade(99.01, 100), Trade(99.02, 200), Trade(99.03, 300)))

    val bt = new Simulation(time1, time3) {
      val res = run {
        val in = CurveSource(curve)

        val price = in(_.price)
        val quantity = in(_.quantity)
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

        in(_.bid)(_.price)
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
      
      (vwap, quantities)
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