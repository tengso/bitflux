package bitflux.core.test

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.FunSuite

//import com.github.nscala_time.time.Imports._

import bitflux.core._
import bitflux.combinators.Implicits._
import bitflux.env._

class TestDynamic extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 10, 1, 2, 3, 4, 5)
  val time2 = time1 + 2000
  val time3 = time1 + 3000
  val time4 = time1 + 4000
  val time5 = time1 + 5000

  test("extend") {
    val bt = new Simulation(time1, time5) {
      val res = run {
        val output = new Placeholder[Int]

        class S(input: Flow[Int]) extends Flow[Int] {
          react(input) {
            if (input() == 2) {
              extendFlow(output) {
                val c = Curve(List(time3, time4, time5), List(4, 6, 8))
                output.setInput(c)
              }
            }
            input()
          }
        }

        val source = Curve(List(time1, time2), List(1, 2))
        new S(source)
        (output * Constant(2)).setBufferSize(3)
      }
    }

    val res = Await.result(bt.res, 1000 millisecond).collect

    println(res)
    assert(res.size === 3)
    assert(res(0)._1 === time3)
    assert(res(0)._2 === 8)
    assert(res(1)._1 === time4)
    assert(res(1)._2 === 12)
    assert(res(2)._1 === time5)
    assert(res(2)._2 === 16)
  }
}