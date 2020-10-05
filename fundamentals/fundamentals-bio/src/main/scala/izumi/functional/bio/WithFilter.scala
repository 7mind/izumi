package izumi.functional.bio

import izumi.fundamentals.platform.language.SourceFilePosition

trait WithFilter[+E] {
  def error(matchedValue: Any, pos: SourceFilePosition): E
}

object WithFilter extends WithFilterInstances1 {
  @inline def apply[E: WithFilter]: WithFilter[E] = implicitly

  implicit def WithFilterNoSuchElementException: WithFilter[NoSuchElementException] = {
    (matchedValue, pos) =>
      new NoSuchElementException(s"Pattern match failed at $pos for value=$matchedValue")
  }
}

sealed trait WithFilterInstances1 extends WithFilterInstances2 {
  implicit def WithFilterUnit: WithFilter[Unit] = (_, _) => ()
}

sealed trait WithFilterInstances2 extends WithFilterInstances3 {
  implicit def WithFilterString: WithFilter[String] = {
    (matchedValue, pos) =>
      s"Pattern match failed at $pos for value=$matchedValue"
  }
}

sealed trait WithFilterInstances3 {
  implicit def WithFilterOption[E](implicit filter: WithFilter[E]): WithFilter[Some[E]] = {
    (matchedValue, pos) => Some(filter.error(matchedValue, pos))
  }
}
