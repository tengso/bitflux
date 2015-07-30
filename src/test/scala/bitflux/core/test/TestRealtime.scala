package bitflux.core.test

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import com.github.nscala_time.time.Imports._

import org.scalatest.FunSuite

import bitflux.core.Implicits._
import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.env._

class TestFlowRealtime extends FunSuite {
  test("realtime-curve") {
    val now = DateTime.now
    val firstEventTime = now + 1000
    val secondEventTime = now + 2000
    
    val test = new Realtime(now + 1, now + 5000) {
      val res = run {
        val data = CurveSource(Curve(List(firstEventTime, secondEventTime), List(1, 2)))
        data + 1
      }
    }

    val result = Await.result(test.res, 8000 millisecond).collect

    assert(result.size === 2)
    
    assert(result(0)._1.millis - firstEventTime.millis < 3)
    assert(result(0)._2 === 2)
    
    assert(result(1)._1.millis - secondEventTime.millis < 3)
    assert(result(1)._2 === 3)
  }

  test("realtime-constant") {
    val now = DateTime.now

    val test = new Realtime(now + 1, now + 2000) {
      val res = run {
        class Either(val data: Flow[Int], val const: Flow[Int]) extends Flow[Int] {
          react(data) {}

          react(const) {
            const()
          }
        }

        val data = CurveSource(Curve(List(now + 500, now + 1000), List(1, 2)))

        new Either(data, Constant(10))
      }
    }

    val res = Await.result(test.res, 5000 millisecond).collect

    assert(res.size === 1)
    assert(res(0)._2 === 10)
  }

  test("real-time-timer") {
    class SetTimer(val data: Flow[Int]) extends Flow[Int] {
      val timer1 = new Timer
      val timer2 = new Timer
      val timer3 = new Timer

      react(data) {
        if (data() == 1) {
          setTimer(1000, timer1)
          setTimer(2000, timer2)
          setTimer(3000, timer3)
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

    val now = DateTime.now
    val test = new Realtime(now + 50, now + 4000) {
      val res = run {
        new SetTimer(Constant(1))
      }
    }

    val result = Await.result(test.res, 6000 millisecond).collect

    // println(result(0)._1.millis - (now + 50 + 1000).millis)
    assert(result(0)._2 === 2)
    
    // println(result(1)._1.millis - (now + 50 + 2000).millis)
    assert(result(1)._2 === 3)
    
    // println(result(2)._1.millis - (now + 50 + 3000).millis)
    assert(result(2)._2 === 4)
  }
  
  test("realtime paralell session") {
    val now = DateTime.now
    val firstEventTime = now + 1000
    val secondEventTime = now + 2000

    val rt = new Realtime(now + 1, now + 3000, isSequential = false) {
      val res = run{
        val data = CurveSource(
            Curve(List(firstEventTime, secondEventTime), List(1, 2)))
        
        data + 1
      } 
    }

    val res = Await.result(rt.res, 5000 millisecond).collect

    assert(res.size === 2)
    
    // println(res(0)._1.millis - firstEventTime.millis)
    assert(res(0)._2 === 2)
    
    // println(res(1)._1.millis - secondEventTime.millis)
    assert(res(1)._2 === 3)
  }
  
  test("Timer-repeat") {
    class T(i: Flow[Double]) extends Flow[Double] {
      val t = new bitflux.core.Timer()
      var count = 1
      
      react(t) {
        if(count < 10) {
          count += 1
          setTimer(2000, t)
          count
        }
      }
      
      react(i) {
        setTimer(2000, t)
        count 
      }
    }
    
    val r = new bitflux.env.Realtime(DateTime.now, DateTime.now + 2000 * 12) {
      val res = run {
        val i = bitflux.core.Constant(1.0)
        val t = new T(i)
        t
      }
    }.res
    
    val res = Await.result(r, 2000 * 16 millisecond).collect
    
    assert(res.size === 10)
    
    // assert(res(0)._1 === time1)
    assert(res(0)._2 === 1)
    
    // assert(res(1)._1 === time1 + 20)
    assert(res(1)._2 === 2)
    
    // assert(res(2)._1 === time1 + 40)
    assert(res(2)._2 === 3)
    
    // assert(res(3)._1 === time1 + 60)
    assert(res(3)._2 === 4)
    
    // assert(res(4)._1 === time1 + 80)
    assert(res(4)._2 === 5)
    
    // assert(res(5)._1 === time1 + 100)
    assert(res(5)._2 === 6)
    
    // assert(res(6)._1 === time1 + 120)
    assert(res(6)._2 === 7)
    
    // assert(res(7)._1 === time1 + 140)
    assert(res(7)._2 === 8)
    
    // assert(res(8)._1 === time1 + 160)
    assert(res(8)._2 === 9)
    
    // assert(res(9)._1 === time1 + 9 * 20)
    assert(res(9)._2 === 10)
  }
}