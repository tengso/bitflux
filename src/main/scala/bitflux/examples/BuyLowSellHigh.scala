package bitflux.examples

import com.github.nscala_time.time.Imports._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import bitflux.core._
import bitflux.env._
import bitflux.combinators.Implicits._


// Demo some bitflux features with a toy trading strategy.
// "Trade" represents crossing of buy and sell orders in market.
// "Quote" represents the current bid/offer price in market.
// Both of them are continuous events that can be naturally modeled as dataflows

object BuyLowSellHigh extends App {
  
  // case class defining the structure of market data
  case class Trade(price: Double, quantity: Int)
  case class Quote(bidPrice: Double, bidQuantity: Int, askPrice: Double, askQuantity: Int)

  // lift the scalar types into dataflow types 
  type Trades = Flow[Trade]
  type Quotes = Flow[Quote]
  type Doubles = Flow[Double]
  type Signals = Flow[Boolean]

  // volume-weighted-average of trade price
  // input and output are flow types 
  def vwap(trades: Trades): Doubles = {
    val prices = trades(_.price) // extract the price field from trades, and make it a flow of prices
    val quantities = trades(_.quantity).toDoubles

    (prices * quantities).sum / quantities.sum // use the methods defined for numeric flows
  }

  // A toy trading strategy that compares the current market price with vwap to decide buy or sell.
  // Inputs and outputs are dataflows.
  // Flow functions are composable.
  def strategy(quotes: Quotes, trades: Trades): (Signals, Signals) = {
    val vwaps = vwap(trades)
    val mids = (quotes(_.bidPrice) + quotes(_.askPrice)) / 2.0

    val buys = mids < vwaps - 0.05
    val sells = mids > vwaps - 0.05

    (buys, sells)
  }

  // a helper function for generating testing data
  // every data-point has an explicit timestamp
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

  val backtest = new Simulation(backtestStartTime, backtestStartTime + 5 * 1000) {
    val result = run {
      val (quotes, trades) = genData(backtestStartTime)
      strategy(quotes, trades)
    }
  }

  // collect the results
  val (backtestBuys, backtestSells) = Await.result(backtest.result, 1000 millisecond) // ._1.collect
  
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
}