package izumi.flow.dsl

import izumi.flow.schema.FValue

sealed trait StreamState

object StreamState {
  sealed trait Final extends StreamState
  case class ChunkReady(chunk: List[FValue]) extends StreamState
  case object ChunkNotReady extends StreamState
  case object StreamFinished extends Final
  case class StreamErrored() extends Final
}
