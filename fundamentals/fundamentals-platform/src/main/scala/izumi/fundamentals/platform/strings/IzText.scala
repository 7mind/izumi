package izumi.fundamentals.platform.strings

object IzText {

  final case class Row(parts: Seq[String], splitter: String)

  def tableFormat(rows: Seq[Seq[String]], header: List[String]): String = {
    val splitChar = " | "

    import izumi.fundamentals.collections.IzCollections._
    val columnsCount = math.max(rows.map(_.size).maxOr(0), header.length)

    val bheader = header ++ List.fill(columnsCount - header.size)("")
    val bparts = rows.map(p => p ++ List.fill(columnsCount - p.size)(""))
    val withHeader = bheader +: bparts

    val maxes = (0 until columnsCount).map(c => c -> withHeader.map(_.apply(c).length).max)

    val maxesM = maxes.toMap

    val splitter = Row(
      maxes.map {
        case (_, len) =>
          "-" * len
      },
      "-+-",
    )

    val boundary = Row(
      maxes.map {
        case (_, len) =>
          "-" * len
      },
      "---",
    )

    val mainRows = bparts.map(p => Row(p, splitChar))

    (List(boundary) ++ List(Row(bheader, splitChar)) ++ List(splitter) ++ mainRows ++ List(boundary))
      .map {
        row =>
          row
            .parts.zipWithIndex.map {
              case (v, cnum) =>
                v.padTo(maxesM(cnum), ' ')
            }.mkString(row.splitter)
      }.mkString("\n")
  }
}
