package izumi.idealingua.translator.tocsharp.types

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.il.ast.typed.Field
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.CSharpImports

final case class CSharpField(
                              name: String,
                              tp: CSharpType,
                              structName: String,
                              by: Seq[String]
                            ) {
  def renderMemberName(capitalize: Boolean = true, uncapitalize: Boolean = false): String = {
    CSharpField.safeName(name, capitalize, uncapitalize, structName)
  }

  private def renderMemberImpl(forInterface: Boolean, by: String): String = {
    if (tp.isNative) {
      s"${if (forInterface) "" else "public "}${tp.renderType(true)} ${if (by.isEmpty) "" else s"$by."}${renderMemberName()} { get; set; }"
    } else {
      "Not Implemented renderMember()"
    }
  }

  def renderMember(forInterface: Boolean): String = {
    if (forInterface) {
      if (by.isEmpty) {
        renderMemberImpl(forInterface, "")
      } else {
        s"new ${renderMemberImpl(forInterface, "")}"
      }
    } else {
      if (by.isEmpty) {
        renderMemberImpl(forInterface, "")
      } else {
        by.map(b => renderMemberImpl(forInterface, b)).mkString("\n")
      }
    }
  }
}

object CSharpField {
  def apply(
            field: Field,
            structName: String,
            by: Seq[String] = Seq.empty
          ) (implicit im: CSharpImports, ts: Typespace): CSharpField = new CSharpField(field.name, CSharpType(field.typeId), structName, by)
  def safeVarName(name: String): String = CSharpField.safeName(name, capitalize = false, uncapitalize = false, "")
  def safeName(name: String, capitalize: Boolean, uncapitalize: Boolean, structName: String): String = {
    val systemReserved = Seq("Type", "Environment")

    val reserved = Seq("abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
      "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event",
      "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in",
      "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out",
      "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed",
      "short", "sizeof", "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try",
      "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using", "using", "static", "virtual", "void",
      "volatile", "while")

    val all = systemReserved ++ reserved

    //      Contextual words in C#, not reserved:
    //      add	alias	ascending
    //      async	await	descending
    //      dynamic	from	get
    //      global	group	into
    //      join	let	nameof
    //      orderby	partial (type)	partial (method)
    //      remove	select	set
    //      value	var	when (filter condition)
    //      where (generic type constraint)	where (query clause)	yield

    val finalName = if (capitalize) name.capitalize else if (uncapitalize) name.uncapitalize else name
    if (finalName == structName) {
      s"@${finalName}_"
    } else {
      if (all.contains(finalName)) s"@$finalName" else finalName
    }
  }
}
