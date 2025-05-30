package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.AbstractLogger

import scala.reflect.macros.blackbox

object LoggerMacroMethods {
  object NonStrict extends LoggerMacroMethods(EncodingMode.NonStrict, defaultPrintTypes = false, defaultPrintImplicits = false)
  object Strict extends LoggerMacroMethods(EncodingMode.Strict, defaultPrintTypes = false, defaultPrintImplicits = false)
  object Raw extends LoggerMacroMethods(EncodingMode.Raw, defaultPrintTypes = false, defaultPrintImplicits = false)
}

open class LoggerMacroMethods(
  val mode: EncodingMode,
  val defaultPrintTypes: Boolean,
  val defaultPrintImplicits: Boolean,
) {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Trace, mode)
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Debug, mode)
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Info, mode)
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Warn, mode)
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Error, mode)
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Crit, mode)
  }

  def scLogValues(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[Unit] = {
    doLogValues(c)(level, values, mode)
  }

  // format: off
  def scLogMethod[A](c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(function: c.Expr[A]): c.Expr[A] = {
    scLogMethodImpl[A](c)(
      printTypes = defaultPrintTypes,
      printImplicits = defaultPrintImplicits,
    )(level, function)
  }

  def scLogMethodPrintTypes[A](c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level], printTypes: c.Expr[Boolean])(function: c.Expr[A]): c.Expr[A] = {
    scLogMethodImpl[A](c)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = defaultPrintImplicits,
    )(level, function)
  }

  def scLogMethodPrintTypesImplicits[A](c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level], printTypes: c.Expr[Boolean], printImplicits: c.Expr[Boolean])(function: c.Expr[A]): c.Expr[A] = {
    scLogMethodImpl[A](c)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = ReflectionUtil.getBooleanLiteral(c)(printImplicits.tree),
    )(level, function)
  }
  // format: on

  protected def scLogMethodImpl[A](
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(printTypes: Boolean,
    printImplicits: Boolean,
  )(level: c.Expr[Level],
    function: c.Expr[A],
  ): c.Expr[A] = {
    new LogMethodMacro[c.type](c).logMethod[A](level, function, printTypes, printImplicits)
  }

  protected def doLog(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String], level: Level, mode: EncodingMode): c.Expr[Unit] = {
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    val l = LogMessageMacro.reifyLevel(c)(level)
    doLogImpl(c)(m, l)
  }

  protected def doLogValues(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(level: c.Expr[Level],
    values: Seq[c.Expr[Any]],
    mode: EncodingMode,
  ): c.Expr[Unit] = {
    val message = LogValuesMacro.createMessageString(c)(values)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    doLogImpl(c)(m, level)
  }

  protected def doLogImpl(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(message: c.Expr[Message],
    level: c.Expr[Level],
  ): c.Expr[Unit] = {
    c.universe.reify {
      val self = c.prefix.splice
      val position = getEnclosingPosition(c).splice
      if (self.acceptable(position.get, level.splice)) {
        self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position))
      }
    }
  }

}
