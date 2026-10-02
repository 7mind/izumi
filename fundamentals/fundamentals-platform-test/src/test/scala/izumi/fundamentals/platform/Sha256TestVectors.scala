package izumi.fundamentals.platform

object Sha256TestVectors {
  val textVectors: Seq[(String, String)] = Seq(
    "" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "abc" -> "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" -> "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
    "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu" -> "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
    "The quick brown fox jumps over the lazy dog" -> "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
    "The quick brown fox jumps over the lazy dog." -> "ef537f25c895bfa782526529a9b63d97aa631564d5d789c2b765448c8635fb6c",
    "a" * 1000000 -> "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
  )

  val binaryVectors: Seq[(Array[Byte], String)] = Seq(
    Array(0xBD) -> "68325720aabd7c82f30f554b313d0570c95accbb7dc4b5aae11204c08ffe732b",
    Array(0x5F, 0xD4) -> "7c4fbf484498d21b487b9d61de8914b2eadaf2698712936d47c3ada2558f6788",
    Array(0xB0, 0xBD, 0x69) -> "4096804221093ddccfbf46831490ea63e9e99414858f8d75ff7f642c7ca61803",
    Array(0xC9, 0x8C, 0x8E, 0x55) -> "7abc22c0ae5af26ce93dbb94433a0e0b2e119d014f8e7f65bd56c61ccccd9504",
  ).map { case (bytes, expected) => bytes.map(_.toByte) -> expected }
}
