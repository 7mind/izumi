package izumi.fundamentals.platform.strings

import izumi.fundamentals.platform.IzPlatformPureUtil
import izumi.fundamentals.platform.strings.impl.*

import scala.annotation.nowarn
import scala.collection.compat.*
import scala.language.implicitConversions

@nowarn("msg=Unused import")
trait IzString extends IzPlatformPureUtil {
  implicit def toRichString(s: String): String_Syntax = new String_Syntax(s)

  implicit def toRichIterable[A](s: IterableOnce[A]): String_Iterable_Syntax[A] = new String_Iterable_Syntax(s)

  implicit def toRichStringIterable[A](s: Iterable[String]): String_StringIterable_Syntax[A] = new String_StringIterable_Syntax(s)

  implicit def toRichStringBytes[A](s: Iterable[Byte]): String_IterableBytes_Syntax = new String_IterableBytes_Syntax(s)
}

object IzString extends IzString {}
