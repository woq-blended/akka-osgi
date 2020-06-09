import mill._
import mill.scalalib.PublishModule
import mill.scalalib.publish._
import mill.modules.Jvm
import os.Path

/**
 * A publish module that also allows publishing via scp to a pseudo repository.
 */
trait BlendedPublishModule extends PublishModule {
  /** Force the user to provide a description for each published module */
  def description: String

  def scpHost : String = "u233308.your-storagebox.de"

  def scpTargetDir : String

  def githubOwner : String = "woq-blended"
  def githubRepo : String
  def organization : String = "de.wayofquality.blended"

  def developers : Seq[Developer] = Seq(
    Developer("atooni", "Andreas Gies", "https://github.com/atooni"),
    Developer("lefou", "Tobias Roeser", "https://github.com/lefou")
  )

  def scpUser = T.input {
    T.env.get("WOQ_SCP_USER") match {
      case Some(u) => u
      case _ =>
        T.log.error(s"The environment variable [WOQ_SCP_USER] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  def scpKey = T.input {
    T.env.get("WOQ_SCP_KEY") match {
      case Some(k) => k
      case None =>
        T.log.error(s"The environment variable [WOQ_SCP_KEY] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  def scpHostKey = T.input {
    T.env.get("WOQ_HOST_KEY") match {
      case Some(k) => k
      case None =>
        T.log.error(s"The environment variable [WOQ_HOST_KEY] must be set correctly to perform a scp upload.")
        sys.exit(1)
    }
  }

  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = description,
      organization = organization,
      url = s"https://github.com/$githubOwner/$githubRepo",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github(githubOwner, githubRepo),
      developers = developers
    )
  }

  def publishScp() : define.Command[Path] = T.command {

    val path = T.dest / publishVersion()

    val keyFile = T.dest / "scpKey"
    val knownHosts = T.dest / "known_hosts"

    try {

      val files : Seq[Path] = new LocalM2Publisher(path)
        .publish(
          jar = jar().path,
          sourcesJar = sourceJar().path,
          docJar = docJar().path,
          pom = pom().path,
          artifact = artifactMetadata(),
          extras = extraPublish()
        )

      // Todo: Sign all files and digest
      files.foreach(_ => ())

      os.write(keyFile, scpKey().replaceAll("\\$", "\n"), perms = "rw-------")
      os.write(knownHosts, s"$scpHost ssh-rsa ${scpHostKey()}")

      val process = Jvm.spawnSubprocess(
        commandArgs = Seq("scp",
          "-i", keyFile.toIO.getAbsolutePath() ,
          "-r",
          "-o", "CheckHostIP=no",
          "-o", s"UserKnownHostsFile=${knownHosts.toIO.getAbsoluteFile()}",
          path.toIO.getAbsolutePath(),s"${scpUser()}@${scpHost}:/${scpTargetDir}"
        ),
        envArgs = Map.empty,
        workingDir = os.pwd
      )

      process.join()
      T.log.info(s"Uploaded ${path.toIO.getAbsolutePath()} to Blended Snapshot repo at ${scpHost}")
    } finally {
      os.remove(keyFile)
      os.remove(knownHosts)
    }
    path
  }
}
