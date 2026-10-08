package izumi.distage.testkit.protocol

object SdkProtocolFrameFixtures {
  def run(verify: (Boolean, String) => Unit): Unit = {
    val owner = "fixture.Suite"
    val large = "😀\u0000:" * SdkProtocolFrames.MaxPartCharacters
    val parts = SdkProtocolFrames.encode(large)
    val decoder = new SdkProtocolFrames.Decoder
    verify(parts.size > 1, "Large SDK frames require multiple selectors")
    verify(parts.forall(_.payload.length <= SdkProtocolFrames.MaxPartCharacters + 16), "Every selector must remain bounded including its decimal header")
    verify(parts.flatMap(decoder.accept(owner, _)) == Vector(large), "SDK frame reassembly preserves Unicode and NUL characters")
    decoder.requireComplete()
    val small = SdkProtocolFrames.encode("small").head
    verify(decoder.accept(owner, small).contains("small"), "Completed assembly must not contaminate a later frame")
    val maximum = "x" * ProtocolCodec.MaxFrameCharacters
    val maximumDecoder = new SdkProtocolFrames.Decoder
    verify(SdkProtocolFrames.encode(maximum).flatMap(maximumDecoder.accept(owner, _)) == Vector(maximum), "SDK transport supports the complete protocol frame limit")

    def reject(expected: String)(body: => Unit): Unit = {
      val message = try { body; None } catch { case failure: IllegalArgumentException => Option(failure.getMessage) }
      verify(message.exists(_.contains(expected)), s"SDK transport must reject $expected; observed $message")
    }
    reject("incomplete") {
      val incomplete = new SdkProtocolFrames.Decoder
      val _ = incomplete.accept(owner, parts.head)
      incomplete.requireComplete()
    }
    reject("first part") { val _ = new SdkProtocolFrames.Decoder().accept(owner, parts(1)) }
    reject("contiguous") {
      val duplicate = new SdkProtocolFrames.Decoder
      val _ = duplicate.accept(owner, parts.head)
      val _ = duplicate.accept(owner, parts.head)
      ()
    }
    reject("owner or length") {
      val changed = new SdkProtocolFrames.Decoder
      val _ = changed.accept(owner, parts.head)
      val _ = changed.accept("another.Suite", parts(1))
      ()
    }
    reject("incomplete") {
      val interleaved = new SdkProtocolFrames.Decoder
      val _ = interleaved.accept(owner, parts.head)
      val _ = interleaved.accept(owner, small)
      ()
    }
    reject("header") { val _ = new SdkProtocolFrames.Decoder().accept(owner, parts.head.copy(payload = "missing-header")) }
    reject("unexpected length") { val _ = new SdkProtocolFrames.Decoder().accept(owner, parts.head.copy(payload = parts.head.payload.dropRight(1))) }
    reject("Unknown SDK") { val _ = new SdkProtocolFrames.Decoder().accept(owner, small.copy(selectorId = "unknown")) }
    reject("character limit") { val _ = SdkProtocolFrames.encode(maximum + "x") }
    reject("channel line") { val _ = SdkProtocolFrames.encode("line\nline") }
    reject("character limit") { val _ = SdkProtocolFrames.encode("") }
  }
}
