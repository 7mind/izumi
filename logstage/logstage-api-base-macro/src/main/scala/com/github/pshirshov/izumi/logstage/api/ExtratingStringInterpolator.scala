package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.Message

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

// TODO: this class is used by tests only
object ExtratingStringInterpolator {

  implicit class LogSC(val sc: StringContext) {
    def m(args: Any*): Message = macro fetchArgsNames
  }

  def fetchArgsNames(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Message] = {
    import c.universe._
    val expressionsList = ArgumentNameExtractionMacro.recoverArgNames(c)(args)

    reify {
      Message(
        c.prefix.splice.asInstanceOf[LogSC].sc
        , expressionsList.splice
      )
    }

  }
}
