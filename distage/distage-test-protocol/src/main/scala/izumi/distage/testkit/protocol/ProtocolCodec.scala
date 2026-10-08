package izumi.distage.testkit.protocol

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, Printer}
import io.circe.parser.parse

import scala.reflect.ClassTag

object ProtocolCodec {
  final val SchemaVersion = 4
  final val MaxFrameCharacters = 1024 * 1024
  final val MaxJsonDepth = 128
  final val MaxFailureDepth = 32

  def validate(message: ProtocolMessage): Either[ProtocolDecodeError, Unit] = {
    try messageCodec.decodeJson(messageCodec(message)).left.map(error => ProtocolDecodeError(error.message)).map(_ => ())
    catch { case error: IllegalArgumentException => Left(ProtocolDecodeError(error.getMessage)) }
  }

  def encode(message: ProtocolMessage): String = {
    val payload = messageCodec(message)
    val frame = Printer.noSpaces.copy(escapeNonAscii = true).print(Json.obj("schemaVersion" -> Json.fromInt(SchemaVersion), "message" -> payload))
    require(frame.length <= MaxFrameCharacters, "Protocol frame exceeds its character limit")
    messageCodec.decodeJson(payload).fold(error => throw new IllegalArgumentException(s"Invalid protocol message: ${error.message}"), _ => ())
    frame
  }

  def decode(frame: String): Either[ProtocolDecodeError, ProtocolMessage] = parseJson(frame).flatMap { json =>
    val cursor = json.hcursor
    cursor.get[Int]("schemaVersion").left.map(error => ProtocolDecodeError(error.message)).flatMap { version =>
      if (version != SchemaVersion) Left(ProtocolDecodeError(s"Unsupported protocol schema version: $version"))
      else cursor.get[ProtocolMessage]("message").left.map(error => ProtocolDecodeError(error.message))
    }
  }

  private[protocol] def encodeTestArgument(id: TestId): String = encodeArgument(testIdCodec, id)
  private[protocol] def decodeTestArgument(value: String): Either[ProtocolDecodeError, TestId] = decodeArgument(testIdCodec, value)
  private[protocol] def encodeAxisArgument(choice: AxisChoice): String = encodeArgument(axisChoiceCodec, choice)
  private[protocol] def decodeAxisArgument(value: String): Either[ProtocolDecodeError, AxisChoice] = decodeArgument(axisChoiceCodec, value)

  private def encodeArgument[A](codec: Codec[A], value: A): String = {
    val json = codec(value)
    codec.decodeJson(json).fold(error => throw new IllegalArgumentException(error.message), _ => ())
    val encoded = Printer.noSpaces.copy(escapeNonAscii = true).print(json)
    require(encoded.length <= MaxFrameCharacters, "Protocol argument exceeds its character limit")
    encoded
  }

  private def decodeArgument[A](codec: Codec[A], value: String): Either[ProtocolDecodeError, A] = {
    parseJson(value).flatMap(json => codec.decodeJson(json).left.map(error => ProtocolDecodeError(error.message)))
  }

  private def parseJson(frame: String): Either[ProtocolDecodeError, Json] = {
    if (frame.length > MaxFrameCharacters) Left(ProtocolDecodeError("Protocol frame exceeds its character limit"))
    else if (frame.indexOf('\n') >= 0 || frame.indexOf('\r') >= 0) Left(ProtocolDecodeError("Protocol frame must occupy one channel line"))
    else {
      validateJsonDepth(frame).flatMap(_ => parse(frame).left.map(error => ProtocolDecodeError(error.message)))
    }
  }

  private def validateJsonDepth(frame: String): Either[ProtocolDecodeError, Unit] = {
    var index = 0
    var depth = 0
    var inString = false
    var escaped = false
    while (index < frame.length) {
      val character = frame.charAt(index)
      if (inString) {
        if (escaped) escaped = false
        else if (character == '\\') escaped = true
        else if (character == '"') inString = false
      } else if (character == '"') inString = true
      else if (character == '{' || character == '[') {
        depth += 1
        if (depth > MaxJsonDepth) return Left(ProtocolDecodeError("JSON nesting exceeds its depth limit"))
      } else if (character == '}' || character == ']') {
        depth -= 1
        if (depth < 0) return Left(ProtocolDecodeError("Invalid JSON nesting"))
      }
      index += 1
    }
    Right(())
  }

  private def product[A](decoder: Decoder[A], encoder: Encoder[A]): Codec[A] = Codec.from(decoder, encoder)
  private def named[A](construct: String => A, value: A => String): Codec[A] = product(
    Decoder.decodeString.emap(text => if (text.nonEmpty) Right(construct(text)) else Left("Identity must not be empty")),
    Encoder.encodeString.contramap(value),
  )
  private def enumeration[A](entries: Vector[(String, A)]): Codec[A] = product(
    Decoder.decodeString.emap(name => entries.find(_._1 == name).map(_._2).toRight(s"Unknown protocol value: $name")),
    Encoder.encodeString.contramap(value => entries.find(_._2 == value).get._1),
  )
  private def tagged(kind: String, fields: (String, Json)*): Json = Json.obj((Vector("kind" -> Json.fromString(kind)) ++ fields)*)
  private def unknown[A](cursor: HCursor, kind: String): Decoder.Result[A] = Left(DecodingFailure(s"Unknown protocol kind: $kind", cursor.history))

  private final case class Variant[A](kind: String, decoder: Decoder[A], encoder: PartialFunction[A, Json])
  private def constant[A](kind: String, value: A): Variant[A] = Variant(
    kind,
    Decoder.const(value),
    {
      case candidate if candidate == value => tagged(kind)
    },
  )
  private def variant[A, B <: A: ClassTag](kind: String, codec: Codec.AsObject[B]): Variant[A] = Variant(
    kind,
    codec.map[A](identity),
    {
      case value: B => Json.fromFields(("kind" -> Json.fromString(kind)) +: codec.encodeObject(value).toVector)
    },
  )
  private def checked[A](codec: Codec.AsObject[A])(check: A => Either[String, A]): Codec.AsObject[A] = Codec.AsObject.from(codec.emap(check), codec)
  private def union[A](variants: Variant[A]*): Codec[A] = product(
    Decoder.instance {
      cursor =>
        cursor.get[String]("kind").flatMap {
          kind =>
            variants.find(_.kind == kind).fold(unknown[A](cursor, kind))(_.decoder(cursor))
        }
    },
    Encoder.instance(value => variants.collectFirst { case entry if entry.encoder.isDefinedAt(value) => entry.encoder(value) }.getOrElse(throw new MatchError(value))),
  )

  private implicit val exactLongCodec: Codec[Long] = product(
    Decoder.decodeString.emap { text =>
      try Right(text.toLong)
      catch { case _: NumberFormatException => Left("Invalid decimal 64-bit integer") }
    },
    Encoder.encodeString.contramap(_.toString),
  )
  private implicit val buildIdCodec: Codec[BuildId] = named(BuildId.apply, _.value)
  private implicit val catalogueIdCodec: Codec[CatalogueId] = named(CatalogueId.apply, _.value)
  private implicit val targetIdCodec: Codec[BuildTargetId] = named(BuildTargetId.apply, _.value)
  private implicit val suiteIdCodec: Codec[SuiteId] = named(SuiteId.apply, _.value)
  private implicit val runIdCodec: Codec[RunId] = named(RunId.apply, _.value)
  private implicit val axisIdCodec: Codec[AxisId] = named(AxisId.apply, _.value)
  private implicit val axisValueCodec: Codec[AxisValue] = named(AxisValue.apply, _.value)
  private implicit val identityCodec: Codec[CatalogueIdentity] =
    Codec.forProduct3("build", "target", "catalogue")(CatalogueIdentity.apply)(value => (value.build, value.target, value.catalogue))
  private implicit val testIdCodec: Codec[TestId] = Codec
    .forProduct4("target", "suite", "path", "variant")(TestId.apply)(value => (value.target, value.suite, value.path, value.variant)).iemap {
      id =>
        if (id.path.isEmpty) Left("Test path must not be empty")
        else if (id.variant.contains("")) Left("Explicit variant must not be empty")
        else Right(id)
    }(identity)
  private implicit val axisChoiceCodec: Codec[AxisChoice] = Codec.forProduct2("axis", "value")(AxisChoice.apply)(value => (value.axis, value.value))
  private implicit val effectiveSettingsCodec: Codec[EffectiveSettings] =
    Codec.forProduct2("axes", "memoization")(EffectiveSettings.apply)(value => (value.axes, value.memoization))
  private implicit val memoizationCodec: Codec[MemoizationOverride] = enumeration(Vector(
    "inherit" -> MemoizationOverride.Inherit, "enabled" -> MemoizationOverride.Enabled, "disabled" -> MemoizationOverride.Disabled,
  ))
  private implicit val selectionCodec: Codec[Selection] = union(
    constant("all", Selection.All),
    variant[Selection, Selection.Only](
      "only",
      checked(Codec.forProduct2("suites", "tests")(Selection.Only.apply)(value => (value.suites, value.tests))) {
        value =>
          if (value.suites.nonEmpty || value.tests.nonEmpty) Right(value) else Left("Explicit selection must not be empty")
      },
    ),
  )
  private implicit val overridesCodec: Codec[RunOverrides] =
    Codec.forProduct3("axes", "axisFilters", "memoization")(RunOverrides.apply)(value => (value.axes, value.axisFilters, value.memoization))
  private implicit val requestCodec: Codec[RunRequest] =
    Codec.forProduct3("identity", "selection", "overrides")(RunRequest.apply)(value => (value.identity, value.selection, value.overrides))
  private implicit val locationCodec: Codec[SourceLocation] = union(
    constant("unavailable", SourceLocation.Unavailable),
    variant[SourceLocation, SourceLocation.Known](
      "known",
      checked(Codec.forProduct3("path", "line", "column")(SourceLocation.Known.apply)(value => (value.path, value.line, value.column))) {
        value =>
          if (value.path.nonEmpty && value.line >= 0 && value.column.forall(_ >= 0)) Right(value) else Left("Invalid source location")
      },
    ),
  )
  private implicit val suiteCodec: Codec[SuiteDescriptor] = Codec.forProduct2("id", "displayName")(SuiteDescriptor.apply)(value => (value.id, value.displayName))
  private implicit val testCodec: Codec[TestDescriptor] =
    Codec.forProduct4("id", "displayName", "location", "settings")(TestDescriptor.apply)(value => (value.id, value.displayName, value.location, value.settings))
  private implicit val catalogueCodec: Codec[Catalogue] =
    Codec.forProduct3("identity", "suites", "tests")(Catalogue.apply)(value => (value.identity, value.suites, value.tests))
  private implicit val resolvedSelectionCodec: Codec[ResolvedSelection] = Codec
    .forProduct2("request", "tests")(ResolvedSelection.apply)(value => (value.request, value.tests)).iemap {
      selection =>
        val tests = selection.tests
        if (tests.isEmpty || tests.map(_.id).distinct.size != tests.size) Left("Resolved selection must contain distinct tests")
        else if (tests.exists(_.id.target != selection.request.identity.target)) Left("Resolved test target differs from the request")
        else if (tests.exists(
            test =>
              selection.request.selection match {
                case Selection.All => false
                case Selection.Only(suites, ids) => !suites.contains(test.id.suite) && !ids.contains(test.id)
              }
          )) Left("Resolved selection contains an unselected test")
        else if (tests.exists(test => test.settings.axes.map(_.axis).distinct.size != test.settings.axes.size)) Left("Duplicate effective activation axes")
        else if (tests.exists(test => !(selection.request.overrides.axes ++ selection.request.overrides.axisFilters).forall(test.settings.axes.contains)))
          Left("Effective activation differs from the requested choices")
        else if (tests.exists(
            test =>
              selection.request.overrides.memoization match {
                case MemoizationOverride.Inherit => false
                case MemoizationOverride.Enabled => !test.settings.memoization
                case MemoizationOverride.Disabled => test.settings.memoization
              }
          )) Left("Effective memoization differs from the request")
        else Right(selection)
    }(identity)
  private implicit val dependencyKeyIdCodec: Codec[DependencyKeyId] = product(
    Decoder.decodeInt.emap(value => if (value >= 0) Right(DependencyKeyId(value)) else Left("Invalid plan key identity")),
    Encoder.encodeInt.contramap(_.value),
  )
  private implicit val dependencyKeyCodec: Codec[DependencyKey] = Codec.forProduct2("id", "displayName")(DependencyKey.apply)(value => (value.id, value.displayName))
  private implicit val planScopeIdCodec: Codec[PlanScopeId] = product(
    Decoder.decodeVector[Int].emap { path =>
      if (path.isEmpty || path.exists(_ < 0)) Left("Invalid plan scope path") else Right(PlanScopeId(path))
    },
    Encoder.encodeVector[Int].contramap(_.path),
  )
  private implicit val planOperationCodec: Codec[PlanOperation] = enumeration(Vector(
    "import" -> PlanOperation.Import, "locatorReference" -> PlanOperation.LocatorReference,
    "createSet" -> PlanOperation.CreateSet, "callProvider" -> PlanOperation.CallProvider,
    "useInstance" -> PlanOperation.UseInstance, "referenceKey" -> PlanOperation.ReferenceKey,
    "createSubcontext" -> PlanOperation.CreateSubcontext, "executeEffect" -> PlanOperation.ExecuteEffect,
    "allocateResource" -> PlanOperation.AllocateResource, "makeProxy" -> PlanOperation.MakeProxy,
    "initProxy" -> PlanOperation.InitProxy,
  ))
  private implicit val planScopeKindCodec: Codec[PlanScopeKind] = enumeration(Vector(
    "runtime" -> PlanScopeKind.Runtime, "memoization" -> PlanScopeKind.Memoization, "test" -> PlanScopeKind.Test,
  ))
  private implicit val planStepCodec: Codec[PlanStep] =
    Codec.forProduct3("key", "operation", "dependencies")(PlanStep.apply)(value => (value.key, value.operation, value.dependencies))
  private implicit val planScopeCodec: Codec[PlanScope] =
    Codec.forProduct4("id", "kind", "tests", "steps")(PlanScope.apply)(value => (value.id, value.kind, value.tests, value.steps))
  private implicit val phaseCodec: Codec[FailurePhase] = enumeration(Vector(
    "discovery" -> FailurePhase.Discovery, "selection" -> FailurePhase.Selection, "planning" -> FailurePhase.Planning,
    "setup" -> FailurePhase.Setup, "test" -> FailurePhase.Test, "finalization" -> FailurePhase.Finalization, "transport" -> FailurePhase.Transport,
  ))
  private implicit val diagnosticErrorMessageCodec: Codec[DiagnosticErrorMessage] = union(
    variant[DiagnosticErrorMessage, DiagnosticErrorMessage.Available]("available", Codec.forProduct1("value")(DiagnosticErrorMessage.Available.apply)(_.value)),
    constant("unavailable", DiagnosticErrorMessage.Unavailable),
    variant[DiagnosticErrorMessage, DiagnosticErrorMessage.AccessorFailed](
      "accessorFailed",
      Codec.forProduct1("exceptionClass")(DiagnosticErrorMessage.AccessorFailed.apply)(_.exceptionClass),
    ),
  )
  private implicit val valueCodec: Codec[ObservedValue] = union(
    variant[ObservedValue, ObservedValue.Evaluated]("evaluated", Codec.forProduct1("value")(ObservedValue.Evaluated.apply)(_.value)),
    constant("notEvaluated", ObservedValue.NotEvaluated),
    variant[ObservedValue, ObservedValue.RenderingFailed](
      "renderingFailed",
      Codec.forProduct2("exceptionClass", "message")(ObservedValue.RenderingFailed.apply)(value => (value.exceptionClass, value.message)),
    ),
  )
  private implicit val diagnosticIdentityCodec: Codec[DiagnosticSourceIdentity] = product(
    Decoder.instance { cursor => for {
      kind <- cursor.get[String]("kind")
      path <- cursor.get[String]("path")
      identity <- if (path.isEmpty) Left(DecodingFailure("Diagnostic source identity must not be empty", cursor.history)) else kind match {
        case "relative" => Right(DiagnosticSourceIdentity.Relative(path))
        case "absolute" => Right(DiagnosticSourceIdentity.Absolute(path))
        case "virtual" => Right(DiagnosticSourceIdentity.Virtual(path))
        case other => unknown(cursor, other)
      }
    } yield identity },
    Encoder.instance {
      case DiagnosticSourceIdentity.Relative(path) => tagged("relative", "path" -> Json.fromString(path))
      case DiagnosticSourceIdentity.Absolute(path) => tagged("absolute", "path" -> Json.fromString(path))
      case DiagnosticSourceIdentity.Virtual(path) => tagged("virtual", "path" -> Json.fromString(path))
    },
  )
  private implicit val diagnosticPointCodec: Codec[DiagnosticPoint] = Codec
    .forProduct3("offset", "line", "column")(DiagnosticPoint.apply)(point => (point.offset, point.line, point.column)).iemap {
      point =>
        if (point.offset >= 0 && point.line >= 0 && point.column >= 0) Right(point) else Left("Invalid diagnostic source point")
    }(identity)
  private implicit val diagnosticSpanCodec: Codec[DiagnosticSpan] = union(
    constant("unavailable", DiagnosticSpan.Unavailable),
    variant[DiagnosticSpan, DiagnosticSpan.Point]("point", Codec.forProduct1("point")(DiagnosticSpan.Point.apply)(_.point)),
    variant[DiagnosticSpan, DiagnosticSpan.Range](
      "range",
      checked(Codec.forProduct2("start", "end")(DiagnosticSpan.Range.apply)(value => (value.start, value.end))) {
        value =>
          if (value.end.offset >= value.start.offset && (value.end.line > value.start.line || (value.end.line == value.start.line && value.end.column >= value.start.column)))
            Right(value)
          else Left("Invalid diagnostic source range")
      },
    ),
  )
  private implicit val diagnosticSourceCodec: Codec[DiagnosticSource] =
    Codec.forProduct3("identity", "span", "expression")(DiagnosticSource.apply)(source => (source.identity, source.span, source.expression))
  private implicit val diagnosticValidationCodec: Codec[DiagnosticSourceValidation] = union(
    constant("matching", DiagnosticSourceValidation.Matching),
    constant("mismatch", DiagnosticSourceValidation.Mismatch),
    constant("unavailable", DiagnosticSourceValidation.Unavailable),
    constant("rangeUnavailable", DiagnosticSourceValidation.RangeUnavailable),
    constant("textUnavailable", DiagnosticSourceValidation.TextUnavailable),
    variant[DiagnosticSourceValidation, DiagnosticSourceValidation.ProviderFailed](
      "providerFailed",
      Codec.forProduct2("exceptionClass", "message")(DiagnosticSourceValidation.ProviderFailed.apply)(value => (value.exceptionClass, value.message)),
    ),
  )
  private implicit val diagnosticKindCodec: Codec[DiagnosticObservationKind] = enumeration(Vector(
    "booleanLeaf" -> DiagnosticObservationKind.BooleanLeaf, "booleanOperator" -> DiagnosticObservationKind.BooleanOperator,
    "comparison" -> DiagnosticObservationKind.Comparison, "operand" -> DiagnosticObservationKind.Operand, "opaque" -> DiagnosticObservationKind.Opaque,
  ))
  private implicit val observationCodec: Codec[DiagnosticObservation] =
    Codec.forProduct4("expression", "span", "kind", "value")(DiagnosticObservation.apply)(value => (value.expression, value.span, value.kind, value.value))
  private implicit val diagnosticCodec: Codec[AssertionDiagnostic] = Codec
    .forProduct4("source", "sourceValidation", "observations", "omittedObservations")(AssertionDiagnostic.apply)(
      value => (value.source, value.sourceValidation, value.observations, value.omittedObservations)
    ).iemap {
      diagnostic =>
        if (diagnostic.omittedObservations >= 0) Right(diagnostic) else Left("Omitted observation count must not be negative")
    }(identity)
  private implicit val captureFieldCodec: Codec[FailureCaptureField] = enumeration(Vector(
    "message" -> FailureCaptureField.Message, "cause" -> FailureCaptureField.Cause, "stack" -> FailureCaptureField.Stack,
  ))
  private implicit val captureErrorCodec: Codec[FailureCaptureError] = Codec
    .forProduct2("field", "exceptionClass")(FailureCaptureError.apply)(error => (error.field, error.exceptionClass)).iemap {
      error =>
        if (error.exceptionClass.nonEmpty) Right(error) else Left("Failure capture exception class must not be empty")
    }(identity)
  private def failureDecoder(depth: Int): Decoder[Failure] = Decoder.instance { cursor =>
    if (depth > MaxFailureDepth) Left(DecodingFailure("Failure graph depth exceeds its limit", cursor.history))
    else for {
      phase <- cursor.get[FailurePhase]("phase")
      exceptionClass <- cursor.get[String]("exceptionClass")
      message <- cursor.get[String]("message")
      stack <- cursor.get[Vector[String]]("stack")
      causes <- cursor.get[Vector[Failure]]("causes")(Decoder.decodeVector(failureDecoder(depth + 1)))
      assertion <- cursor.get[Option[AssertionDiagnostic]]("assertion")
      suppressed <- cursor.get[Vector[Failure]]("suppressed")(Decoder.decodeVector(failureDecoder(depth + 1)))
      captureErrors <- cursor.get[Vector[FailureCaptureError]]("captureErrors")
      _ <- if (captureErrors.map(_.field).distinct.size == captureErrors.size) Right(()) else Left(DecodingFailure("Duplicate failure capture fields", cursor.history))
      _ <- if (captureErrors.forall { error => error.field match {
        case FailureCaptureField.Message => message.isEmpty
        case FailureCaptureField.Cause => causes.isEmpty
        case FailureCaptureField.Stack => stack.isEmpty
      } }) Right(()) else Left(DecodingFailure("Failed capture fields must be unavailable", cursor.history))
    } yield Failure(phase, exceptionClass, message, stack, causes, assertion, suppressed, captureErrors)
  }
  private def failureEncoder(depth: Int): Encoder[Failure] = Encoder.instance { value =>
    require(depth <= MaxFailureDepth, "Failure graph depth exceeds its limit")
    Json.obj(
      "phase" -> phaseCodec(value.phase), "exceptionClass" -> Json.fromString(value.exceptionClass), "message" -> Json.fromString(value.message),
      "stack" -> Encoder.encodeVector[String].apply(value.stack), "causes" -> Json.fromValues(value.causes.map(failureEncoder(depth + 1).apply)),
      "assertion" -> Encoder.encodeOption[AssertionDiagnostic].apply(value.assertion),
      "suppressed" -> Json.fromValues(value.suppressed.map(failureEncoder(depth + 1).apply)),
      "captureErrors" -> Encoder.encodeVector[FailureCaptureError].apply(value.captureErrors),
    )
  }
  private implicit val failureCodec: Codec[Failure] = product(failureDecoder(1), failureEncoder(1))
  private implicit val statusCodec: Codec[TestStatus] = enumeration(Vector(
    "succeeded" -> TestStatus.Succeeded, "failed" -> TestStatus.Failed, "cancelled" -> TestStatus.Cancelled, "skipped" -> TestStatus.Skipped,
  ))
  private implicit val resultCodec: Codec[TestResult] = Codec
    .forProduct4("id", "status", "failure", "durationNanos")(TestResult.apply)(value => (value.id, value.status, value.failure, value.durationNanos)).iemap {
      result =>
        if (result.durationNanos < 0) Left("Test duration must not be negative")
        else if (result.status == TestStatus.Failed && result.failure.isEmpty) Left("Failed test must carry a failure")
        else if (result.status == TestStatus.Succeeded && result.failure.nonEmpty) Left("Successful test must not carry a failure")
        else if (result.status == TestStatus.Skipped && result.failure.nonEmpty) Left("Skipped test must not carry a failure")
        else Right(result)
    }(identity)
  private implicit val outcomeCodec: Codec[RunOutcome] =
    Codec.forProduct4("run", "results", "failures", "cancelled")(RunOutcome.apply)(value => (value.run, value.results, value.failures, value.cancelled))
  private implicit val eventCodec: Codec[RunEvent] = {
    val codec = union(
      variant[RunEvent, RunEvent.Started]("started", Codec.forProduct1("run")(RunEvent.Started.apply)(_.run)),
      variant[RunEvent, RunEvent.TestStarted]("testStarted", Codec.forProduct2("run", "test")(RunEvent.TestStarted.apply)(value => (value.run, value.test))),
      variant[RunEvent, RunEvent.TestCompleted]("testCompleted", Codec.forProduct2("run", "result")(RunEvent.TestCompleted.apply)(value => (value.run, value.result))),
      variant[RunEvent, RunEvent.PhaseFailed]("phaseFailed", Codec.forProduct2("run", "failure")(RunEvent.PhaseFailed.apply)(value => (value.run, value.failure))),
      variant[RunEvent, RunEvent.Finished](
        "finished",
        checked(Codec.forProduct2("run", "outcome")(RunEvent.Finished.apply)(value => (value.run, value.outcome))) {
          value => if (value.outcome.run == value.run) Right(value) else Left("Event and outcome run identities differ")
        },
      ),
    )
    product(Decoder.instance(cursor => cursor.get[String]("kind").flatMap(_ => cursor.get[RunId]("run")).flatMap(_ => codec(cursor))), codec)
  }
  private implicit val operationCodec: Codec[RequestOperation] = enumeration(Vector(
    "resolve" -> RequestOperation.Resolve, "plan" -> RequestOperation.Plan, "execute" -> RequestOperation.Execute,
  ))
  private implicit val planFailureCodec: Codec[PlanFailure] = Codec.forProduct2("tests", "failure")(PlanFailure.apply)(value => (value.tests, value.failure))
  private implicit val planInspectionCodec: Codec[PlanInspection] =
    Codec.forProduct3("keys", "scopes", "failures")(PlanInspection.apply)(value => (value.keys, value.scopes, value.failures))
  private implicit val plannedSelectionCodec: Codec[PlannedSelection] = Codec
    .forProduct2("selection", "inspection")(PlannedSelection.apply)(value => (value.selection, value.inspection)).iemap {
      plan =>
        plan.inspection.validate(plan.selection.tests.map(_.id)).map(_ => plan)
    }(identity)
  private implicit val messageCodec: Codec[ProtocolMessage] = union(
    variant[ProtocolMessage, ProtocolMessage.Discover](
      "discover",
      Codec.forProduct3("run", "build", "target")(ProtocolMessage.Discover.apply)(value => (value.run, value.build, value.target)),
    ),
    variant[ProtocolMessage, ProtocolMessage.Request](
      "request",
      Codec.forProduct3("operation", "run", "request")(ProtocolMessage.Request.apply)(value => (value.operation, value.run, value.request)),
    ),
    variant[ProtocolMessage, ProtocolMessage.Cancel]("cancel", Codec.forProduct1("run")(ProtocolMessage.Cancel.apply)(_.run)),
    variant[ProtocolMessage, ProtocolMessage.Discovered](
      "discovered",
      Codec.forProduct2("run", "catalogue")(ProtocolMessage.Discovered.apply)(value => (value.run, value.catalogue)),
    ),
    variant[ProtocolMessage, ProtocolMessage.Resolved](
      "resolved",
      Codec.forProduct2("run", "selection")(ProtocolMessage.Resolved.apply)(value => (value.run, value.selection)),
    ),
    variant[ProtocolMessage, ProtocolMessage.Planned]("planned", Codec.forProduct2("run", "plan")(ProtocolMessage.Planned.apply)(value => (value.run, value.plan))),
    variant[ProtocolMessage, ProtocolMessage.Event](
      "event",
      checked(Codec.forProduct2("sequence", "event")(ProtocolMessage.Event.apply)(value => (value.sequence, value.event))) {
        value =>
          if (value.sequence >= 0) Right(value) else Left("Event sequence must not be negative")
      },
    ),
    variant[ProtocolMessage, ProtocolMessage.Completed]("completed", Codec.forProduct1("outcome")(ProtocolMessage.Completed.apply)(_.outcome)),
    variant[ProtocolMessage, ProtocolMessage.Rejected](
      "rejected",
      Codec.forProduct2("run", "failure")(ProtocolMessage.Rejected.apply)(value => (value.run, value.failure)),
    ),
  )
}
