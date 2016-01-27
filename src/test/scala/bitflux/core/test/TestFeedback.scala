package bitflux.core.test

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global 
import scala.concurrent.duration._

//import com.github.nscala_time.time.Imports._
import org.scalatest.FunSuite
import bitflux.core._
import bitflux.env._

class TestFeedback extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6
  val time5 = time1 + Context.TimeStep * 8
  val time6 = time1 + Context.TimeStep * 10

  test("feedback") {
    class FD(val input: Flow[Int], val feedback: Flow[Int]) extends Flow[Int] {
      react(input) {
        if (feedback.getLastValue.isEmpty) {
          input()
        }
        else {
          input() + feedback()
        }
      }
    }

    val bt = new Simulation(time1, time4) {
      val res = run {
        val times = List(time1, time2, time3, time4)
        val values = List(1, 2, 3, 4)

        val input = CurveSource(Curve(times, values))
        val feedback = new Feedback[Int]()
        val fd = new FD(input, feedback)
        feedback from fd
        fd.setBufferSize(4)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    println(res)

    assert(res.size === 4)
    assert(res(0)._1 === time1)
    assert(res(1)._1 === time2)
    assert(res(2)._1 === time3)
    assert(res(3)._1 === time4)

    assert(res(0)._2 === 1)
    assert(res(1)._2 === 3)
    assert(res(2)._2 === 6)
    assert(res(3)._2 === 10)
  }

  test("scheduler-feedback") {
    class A(val in: Flow[Int], val fd: Flow[Int]) extends Flow[Int] {
      var inited = false

      react(in) {
        if (!inited) {
          inited = true
          in()
        }
      }

      react(fd) {
        2
      }
    }

    val bt = new Simulation(time1, time1 + 2) {
      val res = run {
        val s = CurveSource(Curve(List(time1), List(1)))

        val fd = new Feedback[Int]
        val a = new A(s, fd)
        fd from (a)
        a.setBufferSize(3)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    assert(res.size === 3)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)
    assert(res(1)._1 === time1 + Context.TimeStep)
    assert(res(1)._2 === 2)
    assert(res(2)._1 === time1 + 2 * Context.TimeStep)
    assert(res(2)._2 === 2)
  }

  test("fact") {
    val n = Curve(
      List(time1, time1 + 1, time1 + 2, time1 + 3, time1 + 4),
      List(1, 2, 3, 4, 5))

    val bt = new Simulation(n.headOption.get._1, n.lastOption.get._1) {
      val res = run {

        class Fact(n: Flow[Int], fact_n_1: Flow[Int]) extends Flow[Int] {
          react(n, fact_n_1) {
            if (fact_n_1.isEmpty) n() else n() * fact_n_1()
          }

          override def toString() = "fact"
        }

        val fact_n_1 = new Feedback[Int]("feedback: n_1")
        val fact_n = new Fact(n, fact_n_1)
        fact_n_1 from fact_n
        fact_n.setBufferSize(5)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect
    
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 1)
    assert(result(1)._1 === time1 + 1)
    assert(result(1)._2 === 2)
    assert(result(2)._1 === time1 + 2)
    assert(result(2)._2 === 6)
    assert(result(3)._1 === time1 + 3)
    assert(result(3)._2 === 24)
    assert(result(4)._1 === time1 + 4)
    assert(result(4)._2 === 120)
  }

  test("fab") {
    val n0 = 0
    val n1 = 1

    class Fab(n0: Flow[Int], n_1: Flow[Int], n_2: Flow[Int]) extends Flow[Int] {
      react(n0) {
        n0()
      }

      react(n_1, n_2) {
        n_1() + n_2()
      }

      override def toString() = "fab"
    }

    val bt = new Simulation(time1, time1 + 6) {
      val res = run {
        val n_1 = new Feedback[Int]("feedback: n_1")
        val n_2 = new Feedback[Int]("feedback: n_2")
        val n = new Fab(Constant(n0), n_1, n_2)

        n_1 from n
        n_2 from n.pre(n1)
        n.setBufferSize(7)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result.size === 7)
    assert(result(0)._1 === time1)
    assert(result(0)._2 === 0)
    assert(result(1)._1 === time1 + 1)
    assert(result(1)._2 === 1)
    assert(result(2)._1 === time1 + 2)
    assert(result(2)._2 === 1)
    assert(result(3)._1 === time1 + 3)
    assert(result(3)._2 === 2)
    assert(result(4)._1 === time1 + 4)
    assert(result(4)._2 === 3)
    assert(result(5)._1 === time1 + 5)
    assert(result(5)._2 === 5)
    assert(result(6)._1 === time1 + 6)
    assert(result(6)._2 === 8)
  }
}