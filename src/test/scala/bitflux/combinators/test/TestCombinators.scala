package bitflux.combinators.test

//import com.github.nscala_time.time.Imports._

import org.scalatest.FunSuite

import bitflux.core.Implicits._
import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.env._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TestCombinators extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6
  val time5 = time1 + Context.TimeStep * 8
  val time6 = time1 + Context.TimeStep * 10

  test("min") {
    val times = List(time1, time2, time3)
    val values = List(1, 5, 3)

    val bt = new Simulation(time1, time3) {
      val res = run {
        val min = bitflux.combinators.min(CurveSource(Curve(times, values)))
        min.setBufferSize(3)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 3)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 1)
    assert(res(2)._1 === time3)
    assert(res(2)._2 === 1)

  }

  test("mean") {
    val times = List(time1, time2, time3, time4)
    val values = List(1, 5, 3, -1)

    val bt = new Simulation(time1, time4) {
      val res = run {
        bitflux.combinators.mean(CurveSource(Curve(times, values))).setBufferSize(4)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 4)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 3)
    assert(res(2)._1 === time3)
    assert(res(2)._2 === 3)
    assert(res(3)._1 === time4)
    assert(res(3)._2 === 2)
  }

  test("map 1") {
    val bt = new Simulation(time1, time2) {
      val res = run {
        val input = CurveSource(Curve(List(time1), List(1)))
        input map (_ + 2)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res(0)._1 === time1)
    assert(res(0)._2 === 3)
  }

  test("map 2") {
    val bt = new Simulation(time1, time2) {
      val res = run {
        val input = CurveSource(Curve(List(time1), List(1)))
        val res = input map (_.toString)
        res
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res(0)._1 === time1)
    assert(res(0)._2 === "1")
  }

  test("filter") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        val input = CurveSource(Curve(List(time1, time2, time3), List(1, 2, 3)))
        val res = input filter (_ > 1)
        res.setBufferSize(2)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 2)
    assert(res(0)._1 === time2)
    assert(res(0)._2 === 2)
    assert(res(1)._1 === time3)
    assert(res(1)._2 === 3)
  }

  test("filter2") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        class Filter(input: Flow[Int]) extends Flow[Any] {
          val good = new Output[Int](this)
          val bad = new Output[String](this)

          react(input) {
            if (input() == 1) {
              setValue(good, input())
            } else {
              setValue(bad, input().toString)
            }
          }
        }

        val input = CurveSource(Curve(List(time1, time2, time3), List(1, 2, 3)))

        val res = new Filter(input)
        val good = res.good * Constant(100)
        val bad = res.bad
        (good.setBufferSize(2), bad.setBufferSize(2))
      }
    }

    val res = Await.result(bt.res, 1000 millisecond)

    val goodRes = res._1.collect
    val badRes = res._2.collect

    assert(goodRes.size === 1)
    assert(goodRes(0)._1 === time1)
    assert(goodRes(0)._2 === 100)

    assert(badRes.size === 2)
    assert(badRes(0)._1 === time2)
    assert(badRes(0)._2 === "2")
    assert(badRes(1)._1 === time3)
    assert(badRes(1)._2 === "3")
  }

  test("seq") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        val input = CurveSource(Curve(List(time1, time2, time3), List(Seq(1), Seq(2), Seq(3))))

        class Append(input: Flow[Seq[Int]]) extends Flow[Seq[Int]] {
          react(input) {
            addValue(input().last)
            addValue(input().last + 1)
          }
        }
        val res = new Append(input)
        res.setBufferSize(3)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res(0)._1 === time1)
    assert(res(0)._2 === Seq(1, 2))
    assert(res(1)._1 === time2)
    assert(res(1)._2 === Seq(2, 3))
    assert(res(2)._1 === time3)
    assert(res(2)._2 === Seq(3, 4))
  }

  test("seq 2") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        val input = CurveSource(Curve(
          List(time1, time2, time3),
          List(Seq(1, 1), Seq(2, 1), Seq(3, 2, 1))))

        class Append(input: Flow[Seq[Int]]) extends Flow[Seq[Int]] {
          val ones = new Output[Seq[Int]](this)
          val twos = new Output[Seq[Int]](this)

          ones.setBufferSize(3)
          twos.setBufferSize(2)

          react(input) {
            input() foreach { input =>
              if (input == 1) {
                addValue(ones, input)
              } else if (input == 2) {
                addValue(twos, input)
              }
            }
          }
        }

        new Append(input)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond)

    val ones = res.ones.collect
    val twos = res.twos.collect

    assert(ones(0)._1 === time1)
    assert(ones(0)._2 === Seq(1, 1))
    assert(ones(1)._1 === time2)
    assert(ones(1)._2 === Seq(1))
    assert(ones(2)._1 === time3)
    assert(ones(2)._2 === Seq(1))

    assert(twos(0)._1 === time2)
    assert(twos(0)._2 === Seq(2))
    assert(twos(1)._1 === time3)
    assert(twos(1)._2 === Seq(2))
  }

  test("logic") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        val a = CurveSource(Curve(List(time1, time2, time3), List(3, 2, 5)))
        val b = CurveSource(Curve(List(time1, time2), List(2, 4)))

        val r = a > b

        bitflux.combinators.If(r, a * 2, b * 2).setBufferSize(3)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size == 3)

    assert(res(0)._1 === time1)
    assert(res(0)._2 === 6)
    assert(res(1)._1 === time2)
    assert(res(1)._2 === 8)
    assert(res(2)._1 === time3)
    assert(res(2)._2 === 10)
  }

  test("branch") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val a = Curve(List(time1, time2, time3, time4), List(3, 2, 5, 6))
        bitflux.combinators.branch(a, Set("0", "1", "2", "3", "4", "5"))(_.toString)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond)

    assert(res("0").collect.size === 0)
    assert(res("1").collect.size === 0)
    assert(res("2").collect.size === 1)
    assert(res("2").collect(0)._1 === time2)
    assert(res("2").collect(0)._2 === 2)
    assert(res("3").collect(0)._1 === time1)
    assert(res("3").collect(0)._2 === 3)
    assert(res("4").collect.size === 0)
    assert(res("5").collect(0)._1 === time3)
    assert(res("5").collect(0)._2 === 5)
  }

  test("ignore small move") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val a = Curve(Seq(
          time1 -> 3.0,
          time2 -> 3.1,
          time3 -> 3.2,
          time4 -> 3.3,
          time5 -> 3.4,
          time6 -> 3.5
        ))
        // double has rounding error
        bitflux.combinators.ignoreSmallMove(a, 0.201).setBufferSize(2)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 2)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 3.0)
    assert(res(1)._1 === time4)
    assert(res(1)._2 === 3.3)
  }
}
