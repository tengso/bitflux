package bitflux.core

import java.util.concurrent.CountDownLatch

import com.github.nscala_time.time.Imports._

import scala.collection.mutable

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

trait SingleLevelParallelScheduler extends Scheduler { self: Context =>
  
  private case class Execute(now: DateTime, waiter: ActorRef)
  private case class Done() 
  
  private class Executor[T](flow: Flow[T]) extends Actor {
    def receive = {
      case Execute(now, waiter) => {
        flow.invoke()
        waiter ! Done()
        flow.logger.trace("sent reply")
      }
      case _ => {
        logger.error("unknow executor command")
      }
    }
  }
  
  class Waiter extends Actor {
    def receive = {
      case Done() => {
        done.countDown()
        logger.trace("received reply")
      }
      case _ => {
        logger.error("unknow waiter command")
      }
    }
  }
  
  private val executors = new mutable.HashMap[Flow[_], ActorRef]()
  
  private val system = ActorSystem(name)
  
  private def createExecutor(flow: Flow[_]) = system.actorOf(Props(new Executor(flow)))
  
  private val waiter = system.actorOf(Props(new Waiter))
  
  // TODO: synchronized??
  var done: CountDownLatch = _
  
  override def shutdown(): Unit = system.shutdown()
  
  override def step(graphChanged: Boolean): Unit = {
    val (ranks, sorted) = getSortedFlows
    
    for (rank <- ranks) { 
      sorted.get(rank).foreach { flows =>
        done = new CountDownLatch(flows.size)
        for (flow <- flows) {
          if (flow.isSource || flow.getParents.exists(_.isActive)) { 
            val executor = executors.getOrElseUpdate(flow, createExecutor(flow))
            executor ! Execute(getCurrentTime.get, waiter)
            logger.trace(s"rank: $rank invoke $flow")
          }
          else {
            done.countDown()
            logger.trace(s"skipped: $flow with parents: ${flow.getParents(0)}")
          }
        }
      }
      done.await()
    }
  }
}



