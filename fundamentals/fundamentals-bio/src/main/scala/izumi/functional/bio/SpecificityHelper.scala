package izumi.functional.bio

/**
  * Just packaging conversions into implicit priority traits is not enough,
  * scalac also has a rule that the most specific return type wins, for some reason. /_\
  * 2.13 seems to overweigh this rule compared to 2.12
  * So we "disjoin" the types from each other such that they're not "related", by *checks notes*... mixing some empty traits in
  */
private[bio] object SpecificityHelper {
  private[bio] trait S1 extends Any
  private[bio] trait S2 extends Any
  private[bio] trait S3 extends Any
  private[bio] trait S4 extends Any
  private[bio] trait S5 extends Any
  private[bio] trait S6 extends Any
  private[bio] trait S7 extends Any
  private[bio] trait S8 extends Any
  private[bio] trait S9 extends Any
  private[bio] trait S10 extends Any
  @inline private[bio] def S1[A, A1 >: A](a: A): A1 with S1 = a.asInstanceOf[A1 with S1]
  @inline private[bio] def S2[A, A1 >: A](a: A): A1 with S2 = a.asInstanceOf[A1 with S2]
  @inline private[bio] def S3[A, A1 >: A](a: A): A1 with S3 = a.asInstanceOf[A1 with S3]
}
