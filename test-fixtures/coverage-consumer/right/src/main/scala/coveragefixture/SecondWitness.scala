package coveragefixture

object SecondWitness {
  def choose(selected: Boolean): Int = {
    if (selected) {
      val executedWitness = 41
      executedWitness + 1
    } else {
      val unexecutedWitness = -41
      unexecutedWitness - 1
    }
  }
}
