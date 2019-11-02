package izumi.idealingua.runtime

trait IRTCast[From, To] {
  def convert(f: From): To
}

trait IRTExtend[From, To] {
  type INSTANTIATOR
  def next(f: From): INSTANTIATOR
}


trait IRTConversions[From] {
  protected def _value: From
  def to[T](implicit converter: IRTCast[From, T]): T = converter.convert(_value)
  def extend[T](implicit converter: IRTExtend[From, T]): converter.INSTANTIATOR = converter.next(_value)
}
