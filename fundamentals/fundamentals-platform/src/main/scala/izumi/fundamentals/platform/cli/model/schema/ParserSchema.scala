package izumi.fundamentals.platform.cli.model.schema

/** TODOs:
  * - default values
  * - varargs
  * - required parameters
  * - automated decoder: ParserSchema[CaseClass](args: RoleAppArguments): CaseClass
  * - decoding MUST fail on
  *    * unknown parameters
  *    * unallowed free args
  *    * unary args used multiple times
  *    * missing required parameters
  */
case class ParserSchema(
                         globalArgsSchema: GlobalArgsSchema,
                         descriptors: Seq[RoleParserSchema],
                       )


case class GlobalArgsSchema(parserDef: ParserDef, doc: Option[String], notes: Option[String])

case class RoleParserSchema(id: String, parser: ParserDef, doc: Option[String], notes: Option[String], freeArgsAllowed: Boolean)


