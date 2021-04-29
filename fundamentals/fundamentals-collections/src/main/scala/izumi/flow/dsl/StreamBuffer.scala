package izumi.flow.dsl

trait StreamBuffer {
  def nextStates(outChunk: List[StreamState]): Unit
}
