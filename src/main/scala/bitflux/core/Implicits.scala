package bitflux.core

object Implicits {
  
  implicit def convertFromCurve[T](input: Curve[T])(implicit context: Context) = CurveSource[T](input)
  
  implicit def lift[S, T](f: S => T): Flow[S] => Flow[T] = {
    (input: Flow[S]) =>
      {
        new Flow[T] {
          react(input) {
            f(input())
          }
        }
      }
  }
  
  implicit def lift[S1, S2, T](f: (S1, S2) => T): (Flow[S1], Flow[S2]) => Flow[T] = {
    (input1: Flow[S1], input2: Flow[S2]) =>
      {
        new Flow[T] {
          react(input1, input2) {
            if (input1.nonEmpty && input2.nonEmpty) {
              f(input1(), input2())
            }
          }
        }
      }
  }
  
  implicit def lift[S1, S2, S3, T](f: (S1, S2, S3) => T): (Flow[S1], Flow[S2], Flow[S3]) => Flow[T] = {
    (input1: Flow[S1], input2: Flow[S2], input3: Flow[S3]) =>
      {
        new Flow[T] {
          react(input1, input2, input3) {
            if (input1.nonEmpty && input2.nonEmpty && input3.nonEmpty) {
              f(input1(), input2(), input3())
            }
          }
        }
      }
  }
  
  implicit def lift[S1, S2, S3, S4, T](f: (S1, S2, S3, S4) => T): (Flow[S1], Flow[S2], Flow[S3], Flow[S4]) => Flow[T] = {
    (input1: Flow[S1], input2: Flow[S2], input3: Flow[S3], input4: Flow[S4]) =>
      {
        new Flow[T] {
          react(input1, input2, input3, input4) {
            if (input1.nonEmpty && input2.nonEmpty && input3.nonEmpty && input4.nonEmpty) {
              f(input1(), input2(), input3(), input4())
            }
          }
        }
      }
  }
  
  implicit def lift[S1, S2, S3, S4, S5, T](f: (S1, S2, S3, S4, S5) => T): (Flow[S1], Flow[S2], Flow[S3], Flow[S4], Flow[S5]) => Flow[T] = {
    (input1: Flow[S1], input2: Flow[S2], input3: Flow[S3], input4: Flow[S4], input5: Flow[S5]) =>
      {
        new Flow[T] {
          react(input1, input2, input3, input4, input5) {
            if (input1.nonEmpty && input2.nonEmpty && input3.nonEmpty && input4.nonEmpty && input5.nonEmpty) {
              f(input1(), input2(), input3(), input4(), input5())
            }
          }
        }
      }
  }
}