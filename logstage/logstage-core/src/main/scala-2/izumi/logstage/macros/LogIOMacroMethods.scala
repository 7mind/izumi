package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.AbstractLogIO

import scala.reflect.macros.blackbox

object LogIOMacroMethods {
  object NonStrict extends LogIOMacroMethods(EncodingMode.NonStrict, defaultPrintTypes = false, defaultPrintImplicits = false)
  object Strict extends LogIOMacroMethods(EncodingMode.Strict, defaultPrintTypes = false, defaultPrintImplicits = false)
  object Raw extends LogIOMacroMethods(EncodingMode.Raw, defaultPrintTypes = false, defaultPrintImplicits = false)
}

open class LogIOMacroMethods(
  val mode: EncodingMode,
  val defaultPrintTypes: Boolean,
  val defaultPrintImplicits: Boolean,
) {

  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Trace, mode)
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Debug, mode)
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Info, mode)
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Warn, mode)
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Error, mode)
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Crit, mode)
  }

  // format: off
  def scLogValues[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[F[Unit]] = {
    doLogValues(c)(level, values, mode)
  }

  def scLogMethod[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level])(function: c.Expr[A])(qp: c.Expr[QuasiIO[F]]): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(qp, level, function)(
      printTypes = defaultPrintTypes,
      printImplicits = defaultPrintImplicits,
    )
  }

  def scLogMethodPrintTypes[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level], printTypes: c.Expr[Boolean])(function: c.Expr[A])(qp: c.Expr[QuasiIO[F]]): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(qp, level, function)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = defaultPrintImplicits,
    )
  }

  def scLogMethodPrintTypesImplicits[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level], printTypes: c.Expr[Boolean], printImplicits: c.Expr[Boolean])(function: c.Expr[A])(qp: c.Expr[QuasiIO[F]]): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(qp, level, function)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = ReflectionUtil.getBooleanLiteral(c)(printImplicits.tree),
    )
  }

  def scLogMethodF[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level])(function: c.Expr[F[A]])(qp: c.Expr[QuasiPrimitives[F]]): c.Expr[F[A]] = {
    scLogMethodImplF[F, A](c)(qp, level, function)(
      printTypes = defaultPrintTypes,
      printImplicits = defaultPrintImplicits,
    )
  }

  def scLogMethodPrintTypesF[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level], printTypes: c.Expr[Boolean])(function: c.Expr[F[A]])(qp: c.Expr[QuasiPrimitives[F]]): c.Expr[F[A]] = {
    scLogMethodImplF[F, A](c)(qp, level, function)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = defaultPrintImplicits,
    )
  }

  def scLogMethodPrintTypesImplicitsF[F[_], A](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level], printTypes: c.Expr[Boolean], printImplicits: c.Expr[Boolean])(function: c.Expr[F[A]])(qp: c.Expr[QuasiPrimitives[F]]): c.Expr[F[A]] = {
    scLogMethodImplF[F, A](c)(qp, level, function)(
      printTypes = ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      printImplicits = ReflectionUtil.getBooleanLiteral(c)(printImplicits.tree),
    )
  }

  protected def scLogMethodImpl[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(qp: c.Expr[QuasiIO[F]],
    level: c.Expr[Level],
    function: c.Expr[A],
  )(printTypes: Boolean,
    printImplicits: Boolean,
  ): c.Expr[F[A]] = {
    val lmm = new LogMethodMacro[c.type](c)
    lmm.logMethodIO[F, A](qp, level, printTypes, printImplicits, function.tree)(
      functionToUse = lmm.exprMaybeSuspend(qp, function)
    )
  }

  protected def scLogMethodImplF[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(qp: c.Expr[QuasiPrimitives[F]],
    level: c.Expr[Level],
    function: c.Expr[F[A]],
  )(printTypes: Boolean,
    printImplicits: Boolean,
  ): c.Expr[F[A]] = {
    val lmm = new LogMethodMacro[c.type](c)
    lmm.logMethodIO[F, A](qp, level, printTypes, printImplicits, function.tree)(
      functionToUse = function
    )
  }

  protected def doLog[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(message: c.Expr[String],
    level: Level,
    mode: EncodingMode,
  ): c.Expr[F[Unit]] = {
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    val l = LogMessageMacro.reifyLevel(c)(level)
    doLogImpl[F](c)(m, l)
  }

  protected def doLogValues[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    values: Seq[c.Expr[Any]],
    mode: EncodingMode,
  ): c.Expr[F[Unit]] = {
    val message = LogValuesMacro.createMessageString(c)(values)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    doLogImpl(c)(m, level)
  }

  protected def doLogImpl[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(message: c.Expr[Message],
    level: c.Expr[Level],
  ): c.Expr[F[Unit]] = {
    c.universe.reify {
      c.prefix.splice.log(level.splice)(message.splice)(getEnclosingPosition(c).splice)
    }
  }

}
