import mill._

import scala.util.control.NonFatal

trait GitModule extends Module {

  /**
   * The current git revision.
   */
  def gitHead: T[String] = T.input {
    sys.env.getOrElse("TRAVIS_COMMIT",
      os.proc("git", "rev-parse", "HEAD").call(cwd = millSourcePath).out.text().trim()
    ).toString()
  }

  /**
   * Calc a publishable version based on git tags and dirty state.
   *
   * @return A tuple of (the latest tag, the calculated version string)
   */
  def calculateVersion: T[(String, String)] = T.input {
    val tag =
      try Option(
        os.proc("git", "describe", "--tags", "--always", gitHead()).call(cwd = millSourcePath).out.trim
      )
      catch {
        case NonFatal(e) => None
      }

    val dirtySuffix = os.proc("git", "diff").call().out.text.trim() match {
      case "" => ""
      case s => "-DIRTY" + Integer.toHexString(s.hashCode)
    }

    tag match {
      case Some(t) => (t, t)
      case None =>
        val latestTaggedVersion = os.proc("git", "describe", "--abbrev=0", "--always", "--tags").call().out.trim

        val commitsSinceLastTag =
          os.proc("git", "rev-list", gitHead(), "--not", latestTaggedVersion, "--count").call().out.trim.toInt

        (latestTaggedVersion, s"$latestTaggedVersion-$commitsSinceLastTag-${gitHead().take(6)}$dirtySuffix")
    }
  }

  def publishVersion: T[String] = T.input {
    val v = T.env.get("CI") match {
      case Some(ci @ ("1" | "true")) =>
        val version = calculateVersion()._2
        T.log.info(s"Using git-based version: ${version} (CI=${ci})")
        version
      case _ => os.read(millSourcePath / "version.txt").trim()
    }
    val path = T.dest / "version.txt"
    os.write(path, v)
    v
  }
}
