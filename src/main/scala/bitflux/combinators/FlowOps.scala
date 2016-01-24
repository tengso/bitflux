package bitflux.combinators

import bitflux.core.Flow

class FlowOps[+T](self: Flow[T]) {
  def +[S >: T](that: Flow[S])(implicit num: Numeric[S]) = plus(self, that)(num)

  def -[S >: T](that: Flow[S])(implicit num: Numeric[S]) = minus(self, that)(num)

  def /[S >: T](that: Flow[S])(implicit num: Fractional[S]) = div(self, that)(num)

  def *[S >: T](that: Flow[S])(implicit num: Numeric[S]) = times(self, that)(num)
  
  def +[S >: T](that: S)(implicit num: Numeric[S]) = self.map(num.plus(_, that))

  def -[S >: T](that: S)(implicit num: Numeric[S]) = self.map(num.minus(_, that))

  def /[S >: T](that: S)(implicit num: Fractional[S]) = self.map(num.div(_, that))

  def *[S >: T](that: S)(implicit num: Numeric[S]) = self.map(num.times(_, that))

  def sum[S >: T](implicit num: Numeric[S]) = bitflux.combinators.sum[S](self)

  def count = bitflux.combinators.count[T](self)

  def mean[S >: T](implicit num: Numeric[S]) = bitflux.combinators.mean[S](self)

  def max[S >: T](implicit num: Numeric[S]) = bitflux.combinators.max[S](self)

  def min[S >: T](implicit num: Numeric[S]) = bitflux.combinators.min[S](self)

  def toDoubles[S >: T](implicit num: Numeric[S]) = toDouble[S](self)

  def toInts[S >: T](implicit num: Numeric[S]) = toInt[S](self)

  def >[S >: T](that: Flow[S])(implicit ord: Ordering[S]) = gt(self, that)

  def >=[S >: T](that: Flow[S])(implicit ord: Ordering[S]) = gteq(self, that)

  def <=[S >: T](that: Flow[S])(implicit ord: Ordering[S]) = lteq(self, that)
  
  def <[S >: T](that: Flow[S])(implicit ord: Ordering[S]) = lt(self, that)
  
  def ===[S >: T](that: Flow[S])(implicit ord: Ordering[S]) = equiv(self, that)
  
  def >[S >: T](that: S)(implicit ord: Ordering[S]) = self.map(ord.gt(_, that))

  def >=[S >: T](that: S)(implicit ord: Ordering[S]) = self.map(ord.gteq(_, that))

  def <=[S >: T](that: S)(implicit ord: Ordering[S]) = self.map(ord.lteq(_, that))
  
  def <[S >: T](that: S)(implicit ord: Ordering[S]) = self.map(ord.lt(_, that))
  
  def ===[S >: T](that: S)(implicit ord: Ordering[S]) = self.map(ord.equiv(_, that))

  def last[E](implicit ev: Flow[T] <:< Flow[Seq[E]]) = bitflux.combinators.last(self)

  def logger[S >: T]() = bitflux.combinators.logger[S](self)
  
  def toSeqFlow = toSeq(self)
  
  def filter(f: T => Boolean) = bitflux.combinators.filter[T](self, f)

  def filterSeq[E](f: E => Boolean)(implicit env: Flow[T] <:< Flow[Seq[E]]) =
    bitflux.combinators.filterSeq(env(self), f)

  def group[E](n: Int)(implicit env: Flow[T] <:< Flow[Seq[E]]) = bitflux.combinators.group(env(self), n)
}