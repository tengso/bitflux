package bitflux.combinators.test

//import com.github.nscala_time.time.Imports._

import org.scalatest.FunSuite

import bitflux.core.Implicits._
import bitflux.combinators.Implicits._
import bitflux.core._
import bitflux.env._
import bitflux.combinators._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TestFork extends FunSuite {
  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + Context.TimeStep * 2
  val time3 = time1 + Context.TimeStep * 4
  val time4 = time1 + Context.TimeStep * 6
  val time5 = time1 + Context.TimeStep * 8
  val time6 = time1 + Context.TimeStep * 10

  test("demux") {
    val curve = Curve(List(
      time1 -> Seq(1),
      time2 -> Seq(),
      time3 -> Seq(4, 5, 1),
      time4 -> Seq(4, 5),
      time5 -> Seq(1, 4, 5)
    ))

    val bt = new Simulation(time1, time6) {
      val res = run {
        val in = CurveSource[Seq[Int]](curve)

        val dem = (input: Int) => {
          Some(input.toString)
        }

        val demuxed = demux(in, dem)

        val one = new Placeholder[Double]
        val four = new Placeholder[Double]
        val five = new Placeholder[Double]

        val output = new Flow[Seq[Int]] {
          react(demuxed) {
            demuxed.newKeys.foreach { key =>
              val newFlow = demuxed(key)

              extendFlow(newFlow) {
                val res = newFlow.last.sum
                key match {
                  case "1" => one.setInput(res)
                  case "4" => four.setInput(res)
                  case "5" => five.setInput(res)
                }
              }
            }
          }
        }
        (one.setBufferSize(2), four.setBufferSize(2), five.setBufferSize(2))
      }
    }

    val res = Await.result(bt.res, 1000 millisecond)

    val resOne = res._1.collect
    val resFour = res._2.collect
    val resFive = res._3.collect

    assert(resOne(0)._1 === time3)
    assert(resOne(0)._2 === 1)

    assert(resOne(1)._1 === time5)
    assert(resOne(1)._2 === 2)

    assert(resFour(0)._1 === time4)
    assert(resFour(0)._2 === 4)

    assert(resFour(1)._1 === time5)
    assert(resFour(1)._2 === 8)

    assert(resFive(0)._1 === time4)
    assert(resFive(0)._2 === 5)

    assert(resFive(1)._1 === time5)
    assert(resFive(1)._2 === 10)
  }

  test("mux") {
    val curve = Curve(List(
      time1 -> Seq(1),
      time2 -> Seq(), 
      time3 -> Seq(4, 5, 1),
      time4 -> Seq(4, 5), 
      time5 -> Seq(1, 4, 5)
    ))
    
    val bt = new Simulation(time1, time5) {
      val res = run {
        val in = CurveSource[Seq[Int]](curve)
        val dem = (input: Int) => {
          Some(input.toString)
        }
        val de = demux(in, dem)
        val out = mux(de)
        out.setBufferSize(4)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result(0)._1 === time1)
    assert(result(0)._2.size === 1)
    assert(result(0)._2(0) === 1)
    
    assert(result(1)._1 === time3)
    assert(result(1)._2.size === 3)
    assert(result(1)._2(0) === 5)
    assert(result(1)._2(1) === 1)
    assert(result(1)._2(2) === 4)

    assert(result(2)._1 === time4)
    assert(result(2)._2.size === 2)
    assert(result(2)._2(0) === 5)
    assert(result(2)._2(1) === 4)

    assert(result(3)._1 === time5)
    assert(result(3)._2.size === 3)
    assert(result(3)._2(0) === 5)
    assert(result(3)._2(1) === 1)
    assert(result(3)._2(2) === 4)
  }

  test("fork") {
    val curve = Curve(List(
      time1 -> Seq(1),
      time2 -> Seq(),
      time3 -> Seq(4, 5, 1),
      time4 -> Seq(4, 5), 
      time5 -> Seq(1, 4, 5)
    ))

    val bt = new Simulation(time1, time5) {
      val res = run {
        val keyFinder: KeyMap[Int] = (input: Int) => Some(input.toString)

        val newGraph: ExtendedFlow[Int, Double] = (init: Seq[Int], updates: Flow[Seq[Int]]) => {
          // the constant created here will kick off the running of this new graph
          val start = Constant(init.last).toSeqFlow
          val merged = flattenSeq(
              List(
                start, 
                updates
              )
          )

          merged.last.sum.toSeqFlow
        }

        val graphFinder: ExtendedFlowMap[Int, Double] = (init: Seq[Int]) => Some(newGraph)

        fork(curve, keyFinder, graphFinder).setBufferSize(5)
      }
    }

    val result = Await.result(bt.res, 1000 millisecond).collect

    assert(result(0)._1 === time1 + Context.TimeStep)
    assert(result(0)._2.size === 1)
    assert(result(0)._2.last === 1.0)

    assert(result(1)._1 === time3)
    assert(result(1)._2.size === 1)
    assert(result(1)._2.last === 2.0)

    assert(result(2)._1 === time3 + Context.TimeStep)
    assert(result(2)._2.size === 2)
    assert(result(2)._2(0) === 5.0)
    assert(result(2)._2(1) === 4.0)

    assert(result(3)._1 === time4)
    assert(result(3)._2.size === 2)
    assert(result(3)._2(0) === 10.0)
    assert(result(3)._2(1) === 8.0)

    assert(result(4)._1 === time5)
    assert(result(4)._2.size === 3)
    assert(result(4)._2(0) === 15.0)
    assert(result(4)._2(1) === 3.0)
    assert(result(4)._2(2) === 12.0)
  }
}
