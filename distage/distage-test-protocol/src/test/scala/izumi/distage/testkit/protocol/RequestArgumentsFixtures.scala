package izumi.distage.testkit.protocol

object RequestArgumentsFixtures {
  def run(verify: (Boolean, String) => Unit): Unit = {
    val identity = CatalogueIdentity(BuildId("arguments-build"), BuildTargetId("arguments-target"), CatalogueId("arguments-catalogue"))
    val suite = SuiteId("example.ArgumentsSuite")
    val first = TestId(identity.target, suite, Vector("a b", "c"), None)
    val second = TestId(identity.target, suite, Vector("a", "b c", "\"\\\n😀"), Some("variant"))
    val axes = Vector(AxisChoice(AxisId("z"), AxisValue("real")), AxisChoice(AxisId("a"), AxisValue("dummy")))
    val base = Vector("--build-id", identity.build.value, "--target-id", identity.target.value, "--catalogue-id", identity.catalogue.value)
    val all = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    verify(RequestArguments.parse(base) == Right(all), "Identity-only host arguments must retain the all-tests inherited request")
    Vector(MemoizationOverride.Inherit, MemoizationOverride.Enabled, MemoizationOverride.Disabled).foreach { memoization =>
      val request = RunRequest(identity, Selection.Only(Vector(SuiteId("z"), suite), Vector(second, first)), RunOverrides(axes, axes.reverse, memoization))
      val expected = request.copy(selection = Selection.Only(Vector(suite, SuiteId("z")), Vector(first, second)), overrides = request.overrides.copy(axes = axes.reverse, axisFilters = axes.reverse))
      val rendered = RequestArguments.render(request)
      verify(RequestArguments.parse(rendered) == Right(expected), "CLI normalization must retain complete structured identities and effective settings")
      verify(RequestArguments.parse(rendered.grouped(2).toVector.reverse.flatten) == Right(expected), "Argument order must not change the normalized request")
      verify(RequestArguments.render(expected) == rendered, "Equivalent requests must have one canonical argument representation")
      val command = ProtocolMessage.Request(RequestOperation.Execute, RunId("arguments-wire"), expected)
      verify(ProtocolCodec.decode(ProtocolCodec.encode(command)) == Right(command), "Standalone protocol input must accept the same normalized request")
    }
    val json = """{"target":"arguments-target","suite":"example.ArgumentsSuite","path":["a b","c"],"variant":null}"""
    val axis = """{"axis":"repo","value":"real"}"""
    val malformed = Vector(
      base.dropRight(1), base.dropRight(2), base ++ Vector("--build-id", "duplicate"), base.updated(1, ""),
      base ++ Vector("--unknown", "value"), base ++ Vector("--memoization", "unknown"),
      base ++ Vector("--memoization", "inherit", "--memoization", "inherit"),
      base ++ Vector("--suite-id", suite.value, "--suite-id", suite.value),
      base ++ Vector("--test-id", json, "--test-id", json),
      base ++ Vector("--test-id", json.replace("arguments-target", "another-target")),
      base ++ Vector("--test-id", json.replace("[\"a b\",\"c\"]", "[]")),
      base ++ Vector("--test-id", json.replace("null", "\"\"")),
      base ++ Vector("--test-id", json + "\n"), base ++ Vector("--test-id", "{"),
      base ++ Vector("--test-id", "x" * (ProtocolCodec.MaxFrameCharacters + 1)),
      base ++ Vector("--axis", axis, "--axis", axis.replace("real", "dummy")),
      base ++ Vector("--axis-filter", axis, "--axis-filter", axis),
      base ++ Vector("--axis", axis.replace("repo", "")),
    )
    malformed.foreach(arguments => verify(RequestArguments.parse(arguments).isLeft, "Malformed, conflicting or cross-target CLI input must reject before discovery"))
    verify(RequestArguments.normalize(all.copy(selection = Selection.Only(Vector.empty, Vector(first.copy(path = Vector.empty))))).isLeft, "Typed request normalization must reject invalid paths as a domain error")
    verify(RequestArguments.normalize(all.copy(selection = Selection.Only(Vector.empty, Vector(first.copy(path = Vector("x" * (ProtocolCodec.MaxFrameCharacters + 1))))))).isLeft, "Oversized typed test IDs must reject through the normalization result")
    println("REQUEST_ARGUMENTS_FIXTURES_OK ids=structured variants=retained order=canonical overrides=typed malformed=rejected standalone=same")
  }
}
