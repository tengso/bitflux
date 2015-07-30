package bitflux.core.test

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import bitflux.core._
import bitflux.env._
import bitflux.combinators._

import org.scalatest.FunSuite

import com.github.nscala_time.time.Imports._

class TestTimer extends FunSuite {
  val time1 = new DateTime(1972, 11, 17, 9, 10, 0)
  
  test("timer") {
    class Output(val timer1: Alarm, val timer2: Alarm, data: Flow[Int]) 
        extends Flow[Double] {
      react(timer1) {
        data()
      }

      react(timer2) {
        data()
      }
    }

    val bt = new Simulation(time1, time1 + 6) {
      val res = run {
        val data = Curve(List(time1, time1 + 2), List(1, 5))
        val t1 = new Alarm(time1 + 1)
        val t2 = new Alarm(time1 + 5)
        new Output(t1, t2, data)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect
    assert(result.size === 2)
    assert(result(0)._1 === time1 + 1)
    assert(result(0)._2 === 1)
    assert(result(1)._1 === time1 + 5)
    assert(result(1)._2 === 5)
  }

  test("timer2") {
    class SetTimer(val data: Flow[Int]) extends Flow[Int] {
      val timer1 = new Timer
      val timer2 = new Timer
      val timer3 = new Timer

      react(data) {
        if (data() == 1) {
          setTimer(1, timer1)
          setTimer(2, timer2)
          setTimer(3, timer3)
        }
      }

      react(timer1) {
        2
      }

      react(timer2) {
        3
      }

      react(timer3) {
        4
      }
    }

    val bt = new Simulation(time1, time1 + 6) {
      val res = run {
        val data = Curve(List(time1, time1 + 1), List(1, 5))
        new SetTimer(data)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect
    
    assert(result.size === 3)
    assert(result(0)._1 === time1 + 1)
    assert(result(0)._2 === 2)
    assert(result(1)._1 === time1 + 2)
    assert(result(1)._2 === 3)
    assert(result(2)._1 === time1 + 3)
    assert(result(2)._2 === 4)
  }

  test("Timer-repeat") {
    class T(i: Flow[Double]) extends Flow[Double] {
      val t = new bitflux.core.Timer()
      var count = 1

      react(t) {
        if (count < 10) {
          count += 1
          setTimer(20, t)
          count
        }
      }

      react(i) {
        setTimer(20, t)
        count
      }
    }

    val r = new Simulation(time1, time1 + 500) {
      val res = run {
        val i = Constant(1.0)
        val t = new T(i)
        t
      }
    }.res

    val res = Await.result(r, 1000 millisecond).collect
    
    assert(res.size === 10)
    assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)

    assert(res(1)._1 === time1 + 20)
    assert(res(1)._2 === 2)

    assert(res(2)._1 === time1 + 40)
    assert(res(2)._2 === 3)

    assert(res(3)._1 === time1 + 60)
    assert(res(3)._2 === 4)

    assert(res(4)._1 === time1 + 80)
    assert(res(4)._2 === 5)

    assert(res(5)._1 === time1 + 100)
    assert(res(5)._2 === 6)

    assert(res(6)._1 === time1 + 120)
    assert(res(6)._2 === 7)

    assert(res(7)._1 === time1 + 140)
    assert(res(7)._2 === 8)

    assert(res(8)._1 === time1 + 160)
    assert(res(8)._2 === 9)

    assert(res(9)._1 === time1 + 9 * 20)
    assert(res(9)._2 === 10)
  }
}