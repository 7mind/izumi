package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Enumeration
import com.github.pshirshov.izumi.idealingua.translator.csharp.types.CSharpField
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

final case class CSharpClass (
                              id: TypeId,
                              fields: Seq[CSharpField]
                             )(implicit im: CSharpImports, ts: Typespace) {

  def renderHeader(): String = {
    s"public class ${id.name}"
  }

  def render(withWrapper: Boolean): String = {
      val indent = if (withWrapper) 4 else 0

      s"""${if (withWrapper) s"${renderHeader()} {" else ""}
         |${fields.map(f => f.renderMember()).mkString("\n").shift(indent)}
         |${if (withWrapper) "}" else ""}
       """.stripMargin
  }
}

object CSharpClass {
  def apply(
             id: TypeId,
             fields: Seq[CSharpField]
           )(implicit im: CSharpImports, ts: Typespace): CSharpClass = new CSharpClass(id, fields)
}
