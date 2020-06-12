package izumi.distage.config.codec

import com.typesafe.config.{ConfigMemorySize, ConfigValue}
import pureconfig.ConfigReader.Result
import pureconfig.error.CannotConvert
import pureconfig.generic.{CoproductHint, ProductHint}
import pureconfig._

import scala.reflect.classTag
import scala.util.control.NonFatal

object PureconfigInstances extends PureconfigInstances

trait PureconfigInstances {
  /** Override pureconfig's default `snake-case` fields – force CamelCase product-hint */
  @inline implicit final def forceCamelCaseProductHint[T]: ProductHint[T] = camelCaseProductHint.asInstanceOf[ProductHint[T]]

  /** Override pureconfig's default `type` field type discriminator for sealed traits.
    * Instead, use `circe`-like format with a single-key object. Example:
    *
    * {{{
    *   sealed trait AorB
    *   final case class A(a: Int) extends AorB
    *   final case class B(b: String) extends AorB
    *
    *   final case class Config(values: List[AorB])
    * }}}
    *
    * in config:
    *
    * {{{
    *   config {
    *     values = [{ A { a = 5 } }, { B { b = cba } }]
    *   }
    * }}}
    */
  @inline implicit final def forceCirceLikeCoproductHint[T]: CoproductHint[T] = circeLikeCoproductHint.asInstanceOf[CoproductHint[T]]

  private[this] final val camelCaseProductHint: ProductHint[Any] = ProductHint(fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))
  private[this] final val circeLikeCoproductHint: CoproductHint[Any] = new CoproductHint[Any] {
    override def from(cur: ConfigCursor, name: String): Result[Option[ConfigCursor]] = {
      for {
        objCur <- cur.asObjectCursor
      } yield objCur.atKey(name).toOption.filter(_ => objCur.keys.size == 1)
    }
    override def to(cv: ConfigValue, name: String): Result[ConfigValue] = {
      import pureconfig.syntax._
      Right(Map(name -> cv).toConfig)
    }
    override def tryNextOnFail(name: String): Boolean = false
  }

  // use `Exported` so that if user imports their own instances, user instances will have higher priority
  implicit final lazy val memorySizeDecoder: Exported[ConfigReader[ConfigMemorySize]] = Exported {
    cur: ConfigCursor =>
      try Right(cur.value.atKey("m").getMemorySize("m"))
      catch {
        case NonFatal(ex) =>
          Result.fail(error.ConvertFailure(CannotConvert(cur.value.toString, classTag[ConfigMemorySize].toString, ex.toString), cur))
      }
  }
}
