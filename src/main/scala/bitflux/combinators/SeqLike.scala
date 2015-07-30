package bitflux.combinators

trait SeqLike[T, E] {
  def lastOption(t: T): Option[E]
}

object SeqLike {
  implicit val IntSeqLike = new SeqLike[Seq[Int], Int] {
    override def lastOption(s: Seq[Int]) = s.lastOption
  }
    
  implicit val DoubleSeqLike = new SeqLike[Seq[Double], Double] {
    override def lastOption(s: Seq[Double]) = s.lastOption
  }
}