Bitflux - A Data-flow Library in Scala
===================
Features
-------------
####Simple DSL help model complex reactive applications with data-flow using:
- Composable stateless and stateful flow objects or functions
- Higher-order data-flow that allows adjusting graph of flows at runtime
- Recursive data-flow graph where inputs depends on the last output

####Synchronous event propagation enables:
- Data flowing as a sequence of time-aware events
- Simulation mode for testing production code against recorded data by simply switching the data source
- Deterministic concurrency

-----

Build & Run the demo
-------------
```
sbt compile
sbt run
```
-----
Sample Code 1
-------------
```scala
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
```
-----
Sample Code 2
-------------
```scala
// Demoing some bitflux features using a toy trading strategy.
// "Trade" represents crossing of buy and sell orders in market.
// "Quote" represents the current bid/offer price in market.
// Both of them are continuously changing events, so can be naturally modeled as data-flows.

object BuyLowSellHigh extends App {
  
  // normal case class defining the structure of market data (a.k.a, scalar types)
  case class Trade(price: Double, quantity: Int)
  case class Quote(bidPrice: Double, bidQuantity: Int, askPrice: Double, askQuantity: Int)

  // lift the scalar types into data-flow types that can be used as input and output of flow functions
  type Trades = Flow[Trade]
  type Quotes = Flow[Quote]
  type Doubles = Flow[Double]
  type Signals = Flow[Boolean]

  // volume-weighted-average of trade price
  // input and output are data-flows
  def vwap(trades: Trades): Doubles = {
    val prices = trades(_.price) // take the price field from trade and make it a flow of prices
    val quantities = trades(_.quantity).toDoubles

    (prices * quantities).sum / quantities.sum
  }

  // a toy trading strategy that compares the current market price with vwap to decide buy or sell
  // inputs and outputs are also data-flows 
  // Note that flow functions are composable
  def strategy(quotes: Quotes, trades: Trades): (Signals, Signals) = {
    val vwaps = vwap(trades)
    val mids = (quotes(_.bidPrice) + quotes(_.askPrice)) / 2.0

    val buys = mids < vwaps - 0.05
    val sells = mids > vwaps - 0.05

    (buys, sells)
  }

  // a helper function for generating testing data
  // every data-point has an explicit time of occurrence
  def genData(baseTime: DateTime): (Curve[Quote], Curve[Trade]) = {
    val quotes = Curve(Seq(
      baseTime        -> Quote(180, 100, 181, 100),
      baseTime + 1000 -> Quote(190, 200, 192, 300),
      baseTime + 2000 -> Quote(200, 200, 201, 200),
      baseTime + 3000 -> Quote(170, 300, 172, 400),
      baseTime + 4000 -> Quote(170, 300, 173, 300)))

    val trades = Curve(Seq(
      baseTime + 2500 -> Trade(180, 100),
      baseTime + 3005 -> Trade(190, 200),
      baseTime + 3010 -> Trade(170, 300),
      baseTime + 4040 -> Trade(170, 300)))
      
    (quotes, trades)
  }

  // test the strategy with historical data
  val backtestStartTime = new DateTime(2014, 1, 10, 9, 40, 0)

  import scala.concurrent._
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._
  
  val backtest = new Simulation(backtestStartTime, backtestStartTime + 5 * 1000) {
    val result = run {
      val (quotes, trades) = genData(backtestStartTime)
      strategy(quotes, trades)
    }
  }

  // collect the results
  val (backtestBuys, backtestSells) = Await.result(backtest.result, 1000 millisecond) 
  // val backtestSells = Awati.result(backtest.result._2.collect
  
  println(backtestBuys.collect)
  println(backtestSells.collect)

  // test the strategy with fake real-time data
  // we are using the exact same strategy function, just wiring it in the real-time context
  // and feed it with real-time data
  val realtimeStartTime = DateTime.now

  val realtime = new Realtime(realtimeStartTime, realtimeStartTime + 5 * 1000) {
    val result = run {
      val (quotes, trades) = genData(realtimeStartTime)
      strategy(quotes, trades)
    }
  }

  val (realtimeBuys, realtimeSells) = Await.result(realtime.result, 8 * 1000 millisecond)
  
  println(realtimeBuys.collect)
  println(realtimeSells.collect)
 
  // because the sequences of historical events and real-time events are the same
  // we should expect the same sequence of trading decisions
  assert(backtestBuys.collect.map(_._2) == realtimeBuys.collect.map(_._2))
  assert(backtestSells.collect.map(_._2) == realtimeSells.collect.map(_._2))
}
```


