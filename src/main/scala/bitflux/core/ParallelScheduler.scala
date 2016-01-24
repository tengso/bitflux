package bitflux.core

import scala.collection.mutable
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Actor
import java.util.concurrent.CountDownLatch

trait ParallelScheduler extends Scheduler { self: Context =>

  sealed trait Input
  private case class InputTicked(latch: CountDownLatch) extends Input
  private case class InputNoTick(latch: CountDownLatch) extends Input
  private case class Done(flow: Flow[_], latch: CountDownLatch)

  private class Executor[T](flow: Flow[T]) extends Actor {
    var inputTicked = false
    var ticks = 0

    private def reset(): Unit = {
      inputTicked = false
      ticks = 0
    }

    def receive = {
      case InputTicked(latch) => {
        flow.logger.trace("input ticked")
        ticks += 1
        inputTicked = true

        if (ticks == flow.getParents.size) {
          val msg = if (flow.invoke()) InputTicked(latch) else InputNoTick(latch)

          flow.getChildren.foreach(getExecutor(_) ! msg)
          if (!flow.isInstanceOf[DynamicPlaceholder[_]]) {
            waiter ! Done(flow, latch)
          }

          reset()
        }
      }
      case InputNoTick(latch) => {
        flow.logger.trace("input no tick")
        ticks += 1

        if (ticks == flow.getParents.size) {
          val msg = if (inputTicked && flow.invoke()) InputTicked(latch) else InputNoTick(latch)

          flow.getChildren.foreach(getExecutor(_) ! msg)
          if (!flow.isInstanceOf[DynamicPlaceholder[_]]) {
            waiter ! Done(flow, latch)
          }

          reset()
        }
      }
      case m @ _ => {
        flow.logger.error(s"unknown message: $m")
      }
    }
  }

  override def shutdown(): Unit = system.shutdown()

  private val system = ActorSystem(name)

  private val executors = new mutable.HashMap[Flow[_], ActorRef]()

  private def createExecutor(flow: Flow[_]): ActorRef = system.actorOf(Props(new Executor(flow)))

  private def getExecutor(flow: Flow[_]): ActorRef = synchronized {
    executors.getOrElseUpdate(flow, createExecutor(flow))
  }

  private val waiter = system.actorOf(Props(new Waiter))

  private class Waiter extends Actor {
    def receive = {
      case Done(flow, latch) => {
        latch.countDown()
        logger.trace(s"received reply from $flow")
      }
      case m @ _ => {
        logger.error(s"unknown waiter command: $m")
      }
    }
  }

  private var numberOfFlows = -1

  override def step(graphChanged: Boolean): Unit = {
    val (feedbacks, sorted) = getSortedFlows

    if (numberOfFlows < 0 || graphChanged) {
      numberOfFlows = sorted.values.flatten.count(flow => !flow.isInstanceOf[DynamicPlaceholder[_]] &&
        !flow.isInstanceOf[Feedback[_]])
    }

    val latch = new CountDownLatch(numberOfFlows)

    getChildren.foreach { child =>
      if (child.isInstanceOf[Feedback[_]]) {
        val msg = if (child.isActive) InputTicked(latch) else InputNoTick(latch)
        child.getChildren.foreach(getExecutor(_) ! msg)
      }
      else {
        getExecutor(child) ! InputTicked(latch)
      }
    }

    latch.await()
  }
}


