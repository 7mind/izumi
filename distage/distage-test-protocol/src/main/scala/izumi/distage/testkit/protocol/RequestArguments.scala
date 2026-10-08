package izumi.distage.testkit.protocol

object RequestArguments {
  private val Options = Set("--build-id", "--target-id", "--catalogue-id", "--suite-id", "--test-id", "--axis", "--axis-filter", "--memoization")
  private final case class Argument(option: String, value: String)

  def parse(arguments: Vector[String]): Either[ProtocolDecodeError, RunRequest] = {
    if (arguments.size % 2 != 0) Left(ProtocolDecodeError("Each request option requires one value"))
    else {
      val pairs = arguments.grouped(2).map(pair => Argument(pair.head, pair(1))).toVector
      if (pairs.exists(pair => !Options.contains(pair.option))) Left(ProtocolDecodeError("Unknown request option"))
      else if (pairs.exists(_.value.isEmpty)) Left(ProtocolDecodeError("Request option values must not be empty"))
      else {
        def values(option: String): Vector[String] = pairs.filter(_.option == option).map(_.value)
        def required(option: String): Either[ProtocolDecodeError, String] = values(option) match {
          case Vector(value) => Right(value)
          case _ => Left(ProtocolDecodeError(s"Request requires exactly one $option"))
        }
        def repeated[A](option: String, decode: String => Either[ProtocolDecodeError, A]): Either[ProtocolDecodeError, Vector[A]] = {
          values(option).foldLeft[Either[ProtocolDecodeError, Vector[A]]](Right(Vector.empty)) { (previous, value) =>
            previous.flatMap(decoded => decode(value).map(decoded :+ _))
          }
        }
        val memoization: Either[ProtocolDecodeError, MemoizationOverride] = values("--memoization") match {
          case Vector() | Vector("inherit") => Right(MemoizationOverride.Inherit)
          case Vector("enabled") => Right(MemoizationOverride.Enabled)
          case Vector("disabled") => Right(MemoizationOverride.Disabled)
          case _ => Left(ProtocolDecodeError("Memoization requires at most one of inherit, enabled or disabled"))
        }
        for {
          build <- required("--build-id")
          target <- required("--target-id")
          catalogue <- required("--catalogue-id")
          tests <- repeated("--test-id", ProtocolCodec.decodeTestArgument)
          axes <- repeated("--axis", ProtocolCodec.decodeAxisArgument)
          filters <- repeated("--axis-filter", ProtocolCodec.decodeAxisArgument)
          memo <- memoization
          suites = values("--suite-id").map(SuiteId.apply)
          selection = if (suites.isEmpty && tests.isEmpty) Selection.All else Selection.Only(suites, tests)
          request <- normalize(RunRequest(CatalogueIdentity(BuildId(build), BuildTargetId(target), CatalogueId(catalogue)), selection, RunOverrides(axes, filters, memo)))
        } yield request
      }
    }
  }

  def normalize(request: RunRequest): Either[ProtocolDecodeError, RunRequest] = ProtocolCodec.validate(ProtocolMessage.Request(RequestOperation.Resolve, RunId("arguments"), request)).flatMap { _ =>
    try normalizeValid(request)
    catch { case error: IllegalArgumentException => Left(ProtocolDecodeError(error.getMessage)) }
  }

  private def normalizeValid(request: RunRequest): Either[ProtocolDecodeError, RunRequest] = {
    val selection: Either[ProtocolDecodeError, Selection] = request.selection match {
      case Selection.All => Right(Selection.All)
      case Selection.Only(suites, tests) =>
        if (suites.distinct.size != suites.size || tests.distinct.size != tests.size) Left(ProtocolDecodeError("Duplicate selected identities"))
        else if (tests.exists(_.target != request.identity.target)) Left(ProtocolDecodeError("Selected test belongs to another target"))
        else {
          val encoded = tests.map(test => test -> ProtocolCodec.encodeTestArgument(test))
          Right(Selection.Only(suites.sortBy(_.value), encoded.sortBy(_._2).map(_._1)))
        }
    }
    val overrides = request.overrides
    if (overrides.axes.map(_.axis).distinct.size != overrides.axes.size || overrides.axisFilters.map(_.axis).distinct.size != overrides.axisFilters.size) {
      Left(ProtocolDecodeError("An axis must have exactly one requested value"))
    } else {
      selection.map(value => request.copy(selection = value, overrides = overrides.copy(axes = overrides.axes.sortBy(_.axis.value), axisFilters = overrides.axisFilters.sortBy(_.axis.value))))
    }
  }

  def render(request: RunRequest): Vector[String] = {
    val normalized = normalize(request).fold(error => throw new IllegalArgumentException(error.message), value => value)
    def repeated[A](option: String, values: Vector[A], encode: A => String): Vector[String] = values.flatMap(value => Vector(option, encode(value)))
    val selection = normalized.selection match {
      case Selection.All => Vector.empty
      case Selection.Only(suites, tests) => repeated[SuiteId]("--suite-id", suites, _.value) ++ repeated("--test-id", tests, ProtocolCodec.encodeTestArgument)
    }
    val memoization = normalized.overrides.memoization match {
      case MemoizationOverride.Inherit => "inherit"
      case MemoizationOverride.Enabled => "enabled"
      case MemoizationOverride.Disabled => "disabled"
    }
    Vector("--build-id", normalized.identity.build.value, "--target-id", normalized.identity.target.value, "--catalogue-id", normalized.identity.catalogue.value) ++ selection ++
    repeated("--axis", normalized.overrides.axes, ProtocolCodec.encodeAxisArgument) ++ repeated("--axis-filter", normalized.overrides.axisFilters, ProtocolCodec.encodeAxisArgument) ++
    Vector("--memoization", memoization)
  }
}
