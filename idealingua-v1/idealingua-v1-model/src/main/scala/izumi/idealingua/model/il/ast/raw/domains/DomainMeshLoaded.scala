package izumi.idealingua.model.il.ast.raw.domains

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.il.ast.raw.defns._
import izumi.idealingua.model.il.ast.raw.models.Inclusion
import izumi.idealingua.model.loader.FSPath

final case class DomainMeshLoaded(
  id: DomainId,
  origin: FSPath,
  directInclusions: Seq[Inclusion],
  originalImports: Seq[Import],
  meta: RawNodeMeta,
  types: Seq[RawTypeDef],
  services: Seq[RawService],
  buzzers: Seq[RawBuzzer],
  streams: Seq[RawStreams],
  consts: Seq[RawConstBlock],
  imports: Seq[SingleImport],
  defn: DomainMeshResolved,
)
