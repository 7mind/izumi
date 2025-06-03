package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogIO, AbstractMacroLogIO}
import izumi.logstage.macros.EncodingModeExtractors.{getModeFromPrefixesEncModeTypeMember, getModeFromType}

import scala.reflect.macros.blackbox

object LogIOMacroMethods {

  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Trace)
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Debug)
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Info)
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Warn)
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Error)
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Crit)
  }

  def scLogValues[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[F[Unit]] = {
    doLogValues(c)(level, values)
  }

  def scLogMethod[XF[_], F[x] >: XF[x], A, EncMode: c.WeakTypeTag](
    c: blackbox.Context { type PrefixType = AbstractMacroLogIO.LogMethod[XF, F, EncMode] }
  )(function: c.Expr[A]
  )(F: c.Expr[QuasiIO[F]]
  ): c.Expr[F[A]] = {
    import c.universe.*
    val mode = getModeFromType[EncMode](c)
    val prefixName = c.freshName(TermName("prefix"))
    val self = c.Expr[AbstractLogIO[XF]](q"$prefixName.__getSelf")
    val level = c.Expr[Level](q"$prefixName.__getSelfLevel")
    val printTypes = c.Expr[Boolean](q"$prefixName.__printTypes")
    val printImplicits = c.Expr[Boolean](q"$prefixName.__printImplicits")

    val lmm = new LogMethodMacro[c.type](c)
    lmm.logMethodIO[XF, F, QuasiIO[F], A](mode, prefixName, F, self, level, printTypes, printImplicits, function.tree)(
      functionToUse = lmm.exprMaybeSuspend[F, A](_, function)
    )
  }

  def scLogMethodF[F[_], A, EncMode: c.WeakTypeTag](
    c: blackbox.Context { type PrefixType = AbstractMacroLogIO.LogMethodF[F, EncMode] }
  )(function: c.Expr[F[A]]
  )(F: c.Expr[QuasiPrimitives[F]]
  ): c.Expr[F[A]] = {
    import c.universe.*
    val mode = getModeFromType[EncMode](c)
    val prefixName = c.freshName(TermName("prefix"))
    val self = c.Expr[AbstractLogIO[F]](q"$prefixName.__getSelf")
    val level = c.Expr[Level](q"$prefixName.__getSelfLevel")
    val printTypes = c.Expr[Boolean](q"$prefixName.__printTypes")
    val printImplicits = c.Expr[Boolean](q"$prefixName.__printImplicits")

    val lmm = new LogMethodMacro[c.type](c)
    lmm.logMethodIO[F, F, QuasiPrimitives[F], A](mode, prefixName, F, self, level, printTypes, printImplicits, function.tree)(
      functionToUse = _ => function
    )
  }

  private def doLog[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(message: c.Expr[String],
    level: Level,
  ): c.Expr[F[Unit]] = {
    val mode = getModeFromPrefixesEncModeTypeMember(c)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    val l = LogMessageMacro.reifyLevel(c)(level)
    doLogImpl[F](c)(m, l)
  }

  private def doLogValues[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    values: Seq[c.Expr[Any]],
  ): c.Expr[F[Unit]] = {
    val mode = getModeFromPrefixesEncModeTypeMember(c)
    val message = LogValuesMacro.createMessageString(c)(values)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    doLogImpl(c)(m, level)
  }

  private def doLogImpl[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(message: c.Expr[Message],
    level: c.Expr[Level],
  ): c.Expr[F[Unit]] = {
    c.universe.reify {
      c.prefix.splice.log(level.splice)(message.splice)(CodePositionMaterializerMacro.getEnclosingPosition(c).splice)
    }
  }

}
