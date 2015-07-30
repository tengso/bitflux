package bitflux.core

import scala.collection.mutable
import com.github.nscala_time.time.Imports._

object DynamicOutput {
  val baseKeyName = "_base"
}

class DynamicOutput[T](implicit context: Context) {
  
  val outputs = mutable.Map[String, Flow[Seq[T]]]()
  private val keysAdded = mutable.Map[String, DateTime]()
  private val children = mutable.ArrayBuffer[Flow[_]]()
  private val parents = mutable.ArrayBuffer[Flow[_]]()
  
  def add(key: String, flow: Flow[Seq[T]]) {
    outputs += (key -> flow)
    if (!keysAdded.contains(key)) {
      keysAdded += (key -> context.getCurrentTime.get)
    }
    
    children.foreach { child =>
      child.addParent(flow)
      flow.addChild(child)
    }
  }
  
  def newKeys() = keysAdded.filter(_._2 == context.getCurrentTime.get).keys 
  
  def apply(key: String) = outputs(key)
  
  def contains(key: String) = outputs.contains(key)
  
  def addBase(base: Flow[Seq[T]]) = 
    outputs += (DynamicOutput.baseKeyName -> base)
  
  def addChild(child: Flow[Seq[T]]) = children.append(child)   
  
  def addParent(parent: Flow[Seq[T]]) = parents.append(parent)   
  
  def values = outputs.values
  
  def keys = outputs.keys.filter(_ != DynamicOutput.baseKeyName)
}