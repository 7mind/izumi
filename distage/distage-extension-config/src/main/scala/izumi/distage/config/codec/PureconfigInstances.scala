package izumi.distage.config.codec

import com.typesafe.config.ConfigMemorySize
import pureconfig.ConfigReader.Result
import pureconfig.error.CannotConvert
import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigCursor, ConfigFieldMapping, Exported, error}

import scala.reflect.classTag
import scala.util.control.NonFatal

object PureconfigInstances extends PureconfigInstances
trait PureconfigInstances {
  /** Override pureconfig's default `snake-case` fields – force CamelCase product-hint */
  implicit def forceCamelCaseProductHint[T]: ProductHint[T] = productHintAny.asInstanceOf[ProductHint[T]]
  private[this] val productHintAny: ProductHint[Any] = ProductHint(fieldMapping = ConfigFieldMapping(CamelCase, CamelCase))

  // use `Exported` so that if user imports their own instances, user instances will have higher priority
  implicit final val memorySizeDecoder: Exported[pureconfig.ConfigReader[ConfigMemorySize]] = Exported((cur: ConfigCursor) => {
    try Right(cur.value.atKey("m").getMemorySize("m")) catch {
      case NonFatal(ex) =>
        Result.fail(error.ConvertFailure(CannotConvert(cur.value.toString, classTag[ConfigMemorySize].toString, ex.toString), cur))
    }
  })
}
