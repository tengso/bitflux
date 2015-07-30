package bitflux.core

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.nscala_time.time.Imports._

trait Context extends Flow[Nothing] {
  val name: String
  val isRealtime: Boolean
  implicit val executionContext: ExecutionContext
  
  protected var currentTime: Option[DateTime] = None
  protected var lastTime: Option[DateTime] = None
  
  def run(start: DateTime, end: DateTime): Unit 
  
  def getCurrentTime: Option[DateTime] = currentTime  
  def setCurrentTime(time: DateTime): Unit = currentTime = Some(time)
  
  def getLastTime: Option[DateTime] = lastTime  
  def setLastTime(time: DateTime): Unit = lastTime = Some(time) 
  
  @volatile
  protected[core] def isGraphChanged: Boolean
  
  protected def exec(): Unit
  
  protected def getFeedbacks: Seq[Flow[_]]
  
  protected[core] def extendFlow(code: => Unit)
  
  protected def shutdown(): Unit
}

object Context {
  val TimeStep = 1 // in milli-seconds
}

class ParallelSimulationContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with SimulationRunner 
    with ParallelScheduler
    
class ParallelRealtimeContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with RealtimeRunner 
    with ParallelScheduler
    
class SingleLevelParallelSimulationContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with SimulationRunner 
    with SingleLevelParallelScheduler
    
class SingleLevelParallelRealtimeContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with RealtimeRunner 
    with SingleLevelParallelScheduler
    
class SequentialSimulationContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with SimulationRunner 
    with SequentialScheduler
    
class SequentialRealtimeContext(val name: String)(implicit val executionContext: ExecutionContext) 
    extends Context 
    with RealtimeRunner 
    with SequentialScheduler
    
    