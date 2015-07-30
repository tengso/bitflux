package bitflux.core

object CurveSource {
  
  def apply[T](inputs: Curve[T])(implicit context: Context): Flow[T] = context match {
      case _: RealtimeRunner => new RealtimeCurveSource(inputs)
      case _: SimulationRunner => new SimulationCurveSource(inputs)
    }  
}