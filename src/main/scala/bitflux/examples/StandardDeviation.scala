package bitflux.examples

import com.github.nscala_time.time.Imports._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import bitflux.core._
import bitflux.env._
import bitflux.core.Implicits._
import bitflux.combinators.Implicits._

object SD extends App {
  // A simple example of calculating running standard deviation of price events 
  
  /*
   * A stateful flow producing the number of price ticks 
   */
  class Size(input: Flow[Double]) extends Flow[Double] {
    var count = 0.0
    
    react(input) {
      count += 1
      count
    }
  }
  
  /*
   * A stateful flow producing the running sum of price ticks
   */
  class Sum(input: Flow[Double]) extends Flow[Double] {
    var sum = 0.0
    
    react(input) {
      sum += input()
      sum
    }
  }
  
  /*
   * A flow function producing the running average of the input prices
   */
  def mean(input: Flow[Double]): Flow[Double] = {
    new Sum(input) / new Size(input) // use the built-in "/" operator provided for numeric flow
  }
  
  def sd(input: Flow[Double]): Flow[Double] = {
    val sqr = (i: Double) => i * i // this is a normal function returning square of input 
    
    val meanOfSquared = mean(sqr(input)) // "sqr" is lifted to take flow as input and return flow as output
    
    val squaredOfMean = sqr(mean(input)) // "sqr" is lifted to take flow as input again
    
    val diff = meanOfSquared - squaredOfMean // use the built-in "-" operator provided for numeric flow
    
    (scala.math.sqrt _)(diff) // standard math function is lifted to take flow as input and return flow as output
                              // it returns a running square root of every input
  }
  
  // Generate prices for testing
  val baseTime = DateTime.now
  val prices = Curve(Seq(
      baseTime     -> 100.0, 
      baseTime + 1 -> 110.0, 
      baseTime + 2 -> 120.0, 
      baseTime + 3 -> 130.0,
      baseTime + 4 -> 140.0))
      
  // run the function in simulation mode
  val s = new Simulation(baseTime, baseTime + 4) {
    val result = run {
      sd(prices)
    }
  }
  
  // Get the result
  val sdOfPrices = Await.result(s.result, 1000 millisecond).collect
  
  // Validate the result
  def sd2(inputs: List[Double]) = {
    val mean = inputs.sum / inputs.size
    scala.math.sqrt(inputs.map(i => (i - mean) * (i - mean)).sum / inputs.size)
  }
  
  val values = prices.values
  val sds = (1 to values.size).map { i =>
    val input = values.take(i)
    sd2(input)
  }
  
  val sds2 = sdOfPrices.map(_._2)
  assert(sds.size == sds2.size)
  assert((sds zip sds2).forall(x => scala.math.abs(x._1 - x._2) < 0.000001) == true)
}