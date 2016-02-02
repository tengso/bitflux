package bitflux.test.util

import org.scalatest.FunSuiteLike
import bitflux.core.{CurveSource, Curve, Flow, Timestamp}
import bitflux.env.Simulation

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class Case[Input, Output](inputs: Seq[(Timestamp, Input)],
                                   outputs: Seq[(Timestamp, Output)],
                                   waitTime: Duration = 1000 millis
                                  )
  extends Simulation(inputs.head._1, inputs.last._1) with FunSuiteLike {

  val test: Flow[Input] => Flow[Output]

  def getResult = run {
    val result = test(CurveSource(Curve(inputs)))
    result.setBufferSize(outputs.size)
    result
  }

  def start(): Unit = {
    val result = Await.result(getResult, waitTime).collect
    assert(result.size == outputs.size)

    result.zip(outputs) foreach { case((timeRealized, realized), (timeExpected, expected)) =>
      assert(timeRealized === timeExpected)
      assert(realized === expected)
    }
  }

  start()
}
