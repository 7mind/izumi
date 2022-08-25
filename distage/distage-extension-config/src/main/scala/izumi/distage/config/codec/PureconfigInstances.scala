package izumi.distage.config.codec

import com.typesafe.config.{ConfigMemorySize, ConfigValue}
import pureconfig.ConfigReader.Result
import pureconfig._
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ThrowableFailure}
import pureconfig.generic.error.{InvalidCoproductOption, NoValidCoproductOptionFound}
import pureconfig.generic.{CoproductHint, ProductHint}

import scala.reflect.classTag
import scala.util.control.NonFatal

trait PureconfigInstances {

  /** Override pureconfig's default `kebab-case` fields – force CamelCase product-hint */
  @inline implicit final def forceCamelCaseProductHint[T]: ProductHint[T] = PureconfigInstances.camelCaseProductHint.asInstanceOf[ProductHint[T]]

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
  @inline implicit final def forceCirceLikeCoproductHint[T]: CoproductHint[T] = PureconfigInstances.circeLikeCoproductHint.asInstanceOf[CoproductHint[T]]

  // use `Exported` so that if user imports their own instances, user instances will have higher priority
  @inline implicit final def memorySizeDecoder: Exported[ConfigReader[ConfigMemorySize]] = PureconfigInstances.configMemorySizeDecoder

}

object PureconfigInstances extends PureconfigInstances {

  private[config] final val camelCaseProductHint: ProductHint[Any] = ProductHint(ConfigFieldMapping(CamelCase, CamelCase))

  private[config] final val circeLikeCoproductHint: CoproductHint[Any] = new CoproductHint[Any] {
    override def from(cur: ConfigCursor, options: Seq[String]): Result[CoproductHint.Action] = {
      for {
        objCur <- cur.asObjectCursor
        _ <-
          if (objCur.keys.size == 1) {
            Right(())
          } else {
            val msg = s"""Invalid format for sealed trait, found multiple or no keys in object. Expected a single-key object like `{ "$$K": {} }`
                         |where `$$K` can be one of ${options.mkString}""".stripMargin
            Left(
              ConfigReaderFailures(
                objCur.failureFor(
                  NoValidCoproductOptionFound(objCur.objValue, Seq("_invalid_format_" -> ConfigReaderFailures(ThrowableFailure(new RuntimeException(msg), None))))
                )
              )
            )
          }
        res <- {
          val key = objCur.keys.head
          if (options.contains(key)) {
            objCur.atKey(key).map(CoproductHint.Use(_, key))
          } else {
            Left(ConfigReaderFailures(objCur.failureFor(InvalidCoproductOption(key))))
          }
        }
      } yield res
    }
    override def to(cv: ConfigValue, name: String): ConfigValue = {
      import pureconfig.syntax._
      Map(name -> cv).toConfig
    }
  }

  private[config] final lazy val configMemorySizeDecoder: Exported[ConfigReader[ConfigMemorySize]] = Exported {
    (cur: ConfigCursor) =>
      cur.asConfigValue.flatMap {
        cv =>
          try Right(cv.atKey("m").getMemorySize("m"))
          catch {
            case NonFatal(ex) =>
              Result.fail(error.ConvertFailure(CannotConvert(cv.toString, classTag[ConfigMemorySize].toString, ex.toString), cur))
          }
      }
  }

}
