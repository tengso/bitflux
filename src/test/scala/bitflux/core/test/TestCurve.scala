package bitflux.core.test

import org.scalatest.FunSuite
import bitflux.core._

class TestCurve extends FunSuite {

  val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
  val time2 = time1 + 1
  val time3 = time1 + 3
  
  val times = List(time1, time2, time3)
		  
  val data = List(1, 2, 3)
      
  test("head") {
    val ts = Curve(times, data)
    val (time, value) = ts.headOption.get
    assert(time === time1)
    assert(value === 1)
  }
  
  test("last") {
    val ts = Curve(times, data)
    val (time, value) = ts.lastOption.get
    assert(time === time3)
    assert(value === 3)
  }
  
  test("apply") {
    val time1 = Timestamp(1972, 11, 17, 0, 0, 0, 0)
    val time2 = time1 + 2
    val time3 = time1 + 4
  
    val times = List(time1, time2 , time3)
    val data = List(1, 2, 3)
      
    var ts = Curve(times, data)

    var value = ts(time1 - 10)
    assert(value.isEmpty)

    value = ts(time1)
    assert(value.nonEmpty)
    assert(value.get === 1)

    value = ts(time2 + 1)
    assert(value.nonEmpty)
    assert(value.get === 2)
    
    value = ts(time1 + 1)
    assert(value.nonEmpty)
    assert(value.get === 1)
    
    value = ts(time3 + 2)
    assert(value.nonEmpty)
    assert(value.get === 3)
    
    ts = Curve(List(time1), List(1))
    value = ts(time1)    
    assert(value.nonEmpty)
    assert(value.get === 1)
    
    value = ts(time1 + 1)    
    assert(value.nonEmpty)
    assert(value.get === 1)
    
//    value = ts(time1 - 1)
//    assert(value.isEmpty)
    
    ts = Curve(List(time1, time2), List(1, 2))
    value = ts(time1)    
    assert(value.nonEmpty)
    assert(value.get === 1)
    
    value = ts(time2)    
    assert(value.nonEmpty)
    assert(value.get === 2)
    
    value = ts(time1 + 1)    
    assert(value.nonEmpty)
    assert(value.get === 1)
    
    value = ts(time2 + 1)
    assert(value.nonEmpty)
    assert(value.get === 2)
  }
}