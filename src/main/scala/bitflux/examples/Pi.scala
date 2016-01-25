package bitflux.examples

import bitflux.core.{Context, Flow, SimulationSource, Timestamp}
import bitflux.env.Simulation
import scala.concurrent.duration._
import bitflux.combinators.Implicits._

import scala.concurrent.Await

object Pi extends App {

  case class Point(x: Double, y: Double)

  class RandomPointGenerator(implicit context: Context) extends Flow[Point] with SimulationSource[Point] {

    var n: Option[Point] = None

    react(context) {
      if (n.nonEmpty) {
        n.get
      }
    }

    def next(start: Timestamp, end: Timestamp): Option[(Timestamp, Point)] = {
      val now = Timestamp.now
      if (now <= end && now >= start) {
        val p = Point(math.random, math.random)
        n = Some(p)
         Some((now, p))
      }
      else {
        n = None
        None
      }
    }

  }


  val startTime = Timestamp.now

  val data = (1 to 1000000) map { i =>
    val p = Point(math.random, math.random)
    val t = startTime + i
    t -> p
  }


  class Printer[T](input: Flow[T], count: Flow[Double]) extends Flow[Nothing] {
    import bitflux.core.Timer
    val timer = new Timer
    var init = false

    react(input) {
      if (!init) {
        setTimer(1000, timer)
        init = true
      }
    }

    react(timer) {
      println(s"${this.now}: ${input()} ${count()}")
      setTimer(1000, timer)
    }
  }

  val sim = new Simulation(startTime, startTime + 10000, isSequential = true) {
    val result = run {
      val points = new RandomPointGenerator()
      val count = points.count
      val d = points.map(p => {
        math.sqrt(math.pow(p.x - 0.5, 2) + math.pow(p.y - 0.5, 2))
      })
      val dd = d.map( i => {
        if(i <= 0.5) 1 else 0
      })
      val pi = dd.sum / count * 4.0
      new Printer(pi, count)
    }
  }

  println(Timestamp.now)
  val pi = Await.result(sim.result, Duration.Inf)
  println(Timestamp.now)
}
