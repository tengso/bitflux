package bitflux.combinators.test

import com.github.nscala_time.time.Imports._

import org.scalatest.FunSuite

import bitflux.core.Implicits._
import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.env._
import bitflux.combinators._
/*
class TestUtil extends FunSuite {
  val time1 = new DateTime(1972, 11, 17, 0, 0, 0, 0)
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
        min
      }
    }

    val res = bt.res.getAll

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
        bitflux.combinators.mean(CurveSource(Curve(times, values)))
      }
    }

    val res = bt.res.getAll
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

    val out = bt.res.getAll

    assert(out(0)._1 === time1)
    assert(out(0)._2 === 3)
  }

  test("map 2") {
    val bt = new Simulation(time1, time2) {
      val res = run {
        val input = CurveSource(Curve(List(time1), List(1)))
        val res = input map (_.toString)
        res
      }
    }

    val out = bt.res.getAll

    assert(out(0)._1 === time1)
    assert(out(0)._2 === "1")
  }

  test("filter") {
    val bt = new Simulation(time1, time3) {
      val res = run {
        val input = CurveSource(Curve(List(time1, time2, time3), List(1, 2, 3)))
        val res = input filter (_ > 1)
        res
      }
    }

    val out = bt.res.getAll

    assert(out.size === 2)
    assert(out(0)._1 === time2)
    assert(out(0)._2 === 2)
    assert(out(1)._1 === time3)
    assert(out(1)._2 === 3)
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
        (good, bad)
      }
    }

    val goodRes = bt.res._1.getAll
    val badRes = bt.res._2.getAll

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
        res
      }
    }

    val out = bt.res.getAll
    assert(out(0)._1 === time1)
    assert(out(0)._2 === Seq(1, 2))
    assert(out(1)._1 === time2)
    assert(out(1)._2 === Seq(2, 3))
    assert(out(2)._1 === time3)
    assert(out(2)._2 === Seq(3, 4))
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

    val ones = bt.res.ones.getAll
    val twos = bt.res.twos.getAll

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

        bitflux.combinators.If(r, a * 2, b * 2)
      }
    }

    val result = bt.res.getAll

    assert(result.size == 3)

    assert(result(0)._1 === time1)
    assert(result(0)._2 === 6)
    assert(result(1)._1 === time2)
    assert(result(1)._2 === 8)
    assert(result(2)._1 === time3)
    assert(result(2)._2 === 10)
  }

  test("branch") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val a = Curve(List(time1, time2, time3, time4), List(3, 2, 5, 6))
        bitflux.combinators.branch(a, Set("0", "1", "2", "3", "4", "5"))(_.toString)
      }
    }

    assert(bt.res("0").getAll.size === 0)
    assert(bt.res("1").getAll.size === 0)
    assert(bt.res("2").getAll.size === 1)
    assert(bt.res("2").getAll(0)._1 === time2)
    assert(bt.res("2").getAll(0)._2 === 2)
    assert(bt.res("3").getAll(0)._1 === time1)
    assert(bt.res("3").getAll(0)._2 === 3)
    assert(bt.res("4").getAll.size === 0)
    assert(bt.res("5").getAll(0)._1 === time3)
    assert(bt.res("5").getAll(0)._2 === 5)
  }

  test("constants") {
    val bt = new Simulation(time1, time4) {
      val res = run {
        val source = CurveSource(Curve(List(time2, time3), List(1, 2)))
        (source + 1, source * 2, source - 1, source.toDoubles / 2.0, source > 1, source < 1, source === 1)
      }
    }

    assert(bt.res._1.getAll.size === 2)
    assert(bt.res._1.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._1.getAll.map(_._2) === Vector(2, 3))

    assert(bt.res._2.getAll.size === 2)
    assert(bt.res._2.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._2.getAll.map(_._2) === Vector(2, 4))

    assert(bt.res._3.getAll.size === 2)
    assert(bt.res._3.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._3.getAll.map(_._2) === Vector(0, 1))

    assert(bt.res._4.getAll.size === 2)
    assert(bt.res._4.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._4.getAll.map(_._2) === Vector(0.5, 1))

    assert(bt.res._5.getAll.size === 2)
    assert(bt.res._5.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._5.getAll.map(_._2) === Vector(false, true))

    assert(bt.res._6.getAll.size === 2)
    assert(bt.res._6.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._6.getAll.map(_._2) === Vector(false, false))

    assert(bt.res._7.getAll.size === 2)
    assert(bt.res._7.getAll.map(_._1) === Vector(time2, time3))
    assert(bt.res._7.getAll.map(_._2) === Vector(true, false))
  }

  test("demo") {
   
  }

  test("merge") {
  }
}
*/