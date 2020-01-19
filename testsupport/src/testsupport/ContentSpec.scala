package testsupport

import java.util.jar.{JarEntry, JarInputStream}

import org.scalatest.FreeSpec

class ContentSpec extends FreeSpec {

  val orig = os.Path(System.getenv("origJar"))
  val osgi = os.Path(System.getenv("osgiJar"))

  s"The wrapped jar [${osgi.baseName}] should" - {

    "contain the same entries as the original jar" in {
      // both exists
      assert(os.isFile(orig))
      assert(os.isFile(osgi))

      def jarEntryNameGenerator(file: os.Path): Seq[String] = {
        val ois = new JarInputStream(file.getInputStream)
        try {
          var entry: JarEntry = ois.getNextJarEntry()
          var entries: Seq[String] = Seq()
          while (entry != null) {
            if (!entry.isDirectory()) entries = entries ++ Seq(entry.getName())
            entry = ois.getNextJarEntry()
          }
          entries
        } finally {
          ois.close()
        }
      }

      val origEntries = jarEntryNameGenerator(orig)
      val osgiEntries = jarEntryNameGenerator(osgi)

      val missingEntries = origEntries.filterNot(e => osgiEntries.contains(e))
      assert(missingEntries.isEmpty, s"\nMissing entries:\n  ${missingEntries.mkString(",\n  ")}")

      val addedEntries = osgiEntries.filterNot(e => origEntries.contains(e))
      assert(addedEntries.isEmpty, s"\nAdded entries:\n  ${addedEntries.mkString(",\n  ")}")

      assert(origEntries.sorted === osgiEntries.sorted)
    }
  }
}
