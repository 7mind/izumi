package izumi.functional.bio

import izumi.fundamentals.platform.language.SourceFilePosition

trait BIOWithFilter[+E] {
  def error(matchedValue: Any, pos: SourceFilePosition): E
}

object BIOWithFilter extends BIOWithFilterInstances1 {
  @inline def apply[E: BIOWithFilter]: BIOWithFilter[E] = implicitly

  implicit def BIOWithFilterNoSuchElementException: BIOWithFilter[NoSuchElementException] = {
    (matchedValue, pos) =>
      new NoSuchElementException(s"Pattern match failed at $pos for value=$matchedValue")
  }
}

sealed trait BIOWithFilterInstances1 extends BIOWithFilterInstances2 {
  implicit def BIOWithFilterUnit: BIOWithFilter[Unit] = (_, _) => ()
}

sealed trait BIOWithFilterInstances2 extends BIOWithFilterInstances3 {
  implicit def BIOWithFilterString: BIOWithFilter[String] = {
    (matchedValue, pos) =>
      s"Pattern match failed at $pos for value=$matchedValue"
  }
}

sealed trait BIOWithFilterInstances3 {
  implicit def BIOWithFilterOption[E](implicit filter: BIOWithFilter[E]): BIOWithFilter[Some[E]] = {
    (matchedValue, pos) => Some(filter.error(matchedValue, pos))
  }
}
