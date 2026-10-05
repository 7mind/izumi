# Agent notes

## Running sbt in a git worktree

sbt-git's JGit cannot read a linked git worktree and the build fails to load with
`NoWorkTreeException: Bare Repository has neither a working tree, nor an index`.
`IzumiGitWorktreePlugin` (from sbt-izumi) switches sbt-git to console git, but only outside CI mode.

- In a worktree, do not export `CI=true`: it sets `insideCI` and the plugin keeps JGit.
- Setting `ThisBuild / insideCI := true` after load fails the same way, because the plugin re-reads it.
- For CI-strict checks (warnings as errors) in a worktree, set both keys in one command:
  `set Seq(ThisBuild / insideCI := true, ThisBuild / com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit := true)`.
