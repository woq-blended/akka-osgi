package testsupport

import java.util.jar.{JarEntry, JarInputStream}

import org.scalatest.FreeSpec

class ContentSpec extends FreeSpec {

  "The wrapped jar should contain the same entries as the original jar" in {

    val orig = os.Path(System.getenv("origJar"))
    val osgi = os.Path(System.getenv("osgiJar"))

    // both exists
    os.isFile(orig)
    os.isFile(osgi)

    def jarEntryNameGenerator(file: os.Path): Seq[String] = {
      val ois = new JarInputStream(file.getInputStream)
      var entry: JarEntry = ois.getNextJarEntry()
      var entries: Seq[String] = Seq()
      while(entry != null) {
        entries = entries ++ Seq(entry.getName())
        entry = ois.getNextJarEntry()
      }
      ois.close()
      entries
    }

    val origEntries = jarEntryNameGenerator(orig)
    val osgiEntries = jarEntryNameGenerator(osgi)

    assert(origEntries.sorted === osgiEntries.sorted)
  }

}
