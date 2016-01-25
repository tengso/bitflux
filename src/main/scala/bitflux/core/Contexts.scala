package bitflux.core

import scala.concurrent.ExecutionContext

trait Context extends Flow[Nothing] {
  val name: String
  val isRealtime: Boolean
  implicit val executionContext: ExecutionContext
  
  protected var currentTime: Option[Timestamp] = None
  protected var lastTime: Option[Timestamp] = None
  
  def run(start: Timestamp, end: Timestamp): Unit
  
  def getCurrentTime: Option[Timestamp] = currentTime
  def setCurrentTime(time: Timestamp): Unit = currentTime = Some(time)
  
  def getLastTime: Option[Timestamp] = lastTime
  def setLastTime(time: Timestamp): Unit = lastTime = Some(time)
  
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
    
    