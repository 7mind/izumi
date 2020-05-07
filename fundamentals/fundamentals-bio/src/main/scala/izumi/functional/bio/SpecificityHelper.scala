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
  private[bio] trait S11 extends Any
  private[bio] trait S12 extends Any
  private[bio] trait S13 extends Any
  private[bio] trait S14 extends Any
  private[bio] trait S15 extends Any
  @inline private[bio] def S1[A, A1 >: A](a: A): A1 with S1 = a.asInstanceOf[A1 with S1]
  @inline private[bio] def S2[A, A1 >: A](a: A): A1 with S2 = a.asInstanceOf[A1 with S2]
  @inline private[bio] def S3[A, A1 >: A](a: A): A1 with S3 = a.asInstanceOf[A1 with S3]
  @inline private[bio] def S4[A, A1 >: A](a: A): A1 with S4 = a.asInstanceOf[A1 with S4]
  @inline private[bio] def S5[A, A1 >: A](a: A): A1 with S5 = a.asInstanceOf[A1 with S5]
  @inline private[bio] def S6[A, A1 >: A](a: A): A1 with S6 = a.asInstanceOf[A1 with S6]
  @inline private[bio] def S7[A, A1 >: A](a: A): A1 with S7 = a.asInstanceOf[A1 with S7]
  @inline private[bio] def S8[A, A1 >: A](a: A): A1 with S8 = a.asInstanceOf[A1 with S8]
  @inline private[bio] def S9[A, A1 >: A](a: A): A1 with S9 = a.asInstanceOf[A1 with S9]
  @inline private[bio] def S10[A, A1 >: A](a: A): A1 with S10 = a.asInstanceOf[A1 with S10]
  @inline private[bio] def S11[A, A1 >: A](a: A): A1 with S11 = a.asInstanceOf[A1 with S11]
  @inline private[bio] def S12[A, A1 >: A](a: A): A1 with S12 = a.asInstanceOf[A1 with S12]
  @inline private[bio] def S13[A, A1 >: A](a: A): A1 with S13 = a.asInstanceOf[A1 with S13]
  @inline private[bio] def S14[A, A1 >: A](a: A): A1 with S14 = a.asInstanceOf[A1 with S14]
  @inline private[bio] def S15[A, A1 >: A](a: A): A1 with S15 = a.asInstanceOf[A1 with S15]
}
