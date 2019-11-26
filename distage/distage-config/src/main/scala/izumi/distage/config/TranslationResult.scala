package izumi.distage.config

import izumi.distage.model.plan.{ExecutableOp, OperationOrigin}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.typesafe.config.{ConfigOrigin, ConfigValue}
import izumi.fundamentals.platform.exceptions.IzThrowable._

sealed trait TranslationResult

object TranslationResult {

  sealed trait TranslationFailure extends TranslationResult {
    def op: ExecutableOp

    def target: RuntimeDIUniverse.DIKey = op.target

    protected def origin: String = {
      op.origin match {
        case d: OperationOrigin.Defined =>
          s"${d.binding.origin.toString} ($target)"

        case OperationOrigin.Unknown =>
          target.toString
      }

    }
  }

  final case class Success(op: ExecutableOp, path: ConfigPath) extends TranslationResult

  final case class Passthrough(op: ExecutableOp) extends TranslationResult

  final case class MissingConfigValue(op: ExecutableOp, paths: Seq[(ConfigPath, Throwable)], configOrigin: ConfigOrigin) extends TranslationFailure {
    override def toString: String = {
      import izumi.fundamentals.platform.strings.IzString._
      val tried = paths.map {
        case (path, f) =>
          s"${path.toPath}: ${f.getMessage}"
      }.niceList()

      val details = s"""origin: $origin
         |tried: $tried""".stripMargin.shift(4)

      s"Missing config value:\n$details\nWhen reading from ConfigOrigin: $configOrigin"
    }
  }

  final case class ExtractionFailure(op: ExecutableOp, tpe: SafeType, path: String, config: ConfigValue, configOrigin: ConfigOrigin, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$origin: cannot read $tpe out of $path ==> $config: ${f.stackTrace}\nWhen reading from ConfigOrigin: $configOrigin"
  }

  final case class Failure(op: ExecutableOp, configOrigin: ConfigOrigin, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$origin: unexpected exception: ${f.stackTrace}\nWhen reading from ConfigOrigin: $configOrigin"
  }

}
