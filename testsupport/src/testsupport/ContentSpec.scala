package testsupport

import java.security.{DigestInputStream, MessageDigest}
import java.util.jar.{JarEntry, JarInputStream}

import org.scalatest.FreeSpec

class ContentSpec extends FreeSpec {

  val orig : Seq[os.Path] = System.getenv("origJars").split(":").map(s => os.Path(s)).toSeq
  val osgi : os.Path = os.Path(System.getenv("osgiJar"))

  s"The wrapped jar [${osgi.baseName}] should" - {

    "exist as does the original jar" in {
      assert(orig.forall(os.isFile))
      assert(os.isFile(osgi))
    }

    "contain the same entries (name, size, digest) as the original jar" in {
      val origEntries : Map[os.Path, Seq[JEntry]] = 
        orig.map(p => p -> jarEntryNameGenerator(p)).toMap
      val osgiEntries = jarEntryNameGenerator(osgi)

      val allOrigEntries : Seq[JEntry] = origEntries.values.flatten.iterator.toSeq.distinct
      assert(
        allOrigEntries.size == origEntries.values.foldLeft(0)( (c, v) => c + v.size),
        "We will not combine non-disjunct jar files into a combined bundle"
      )

      // The list of entries in the original jar file(s) that have not made it into the bundle 
      val missingEntries = allOrigEntries.filterNot(e => osgiEntries.exists(a => a.name == e.name))
      assert(missingEntries.isEmpty, s"\nMissing entries:\n  ${missingEntries.map(_.name).mkString(",\n  ")}")

      // The list of entries that were somehow added to the combined jar file contents
      val addedEntries = osgiEntries.filterNot(e => allOrigEntries.exists(a => a.name == e.name))
      assert(addedEntries.isEmpty, s"\nAdded entries:\n  ${addedEntries.map(_.name).mkString(",\n  ")}")

      val e1 = allOrigEntries.sortBy(_.name)
      val e2 = osgiEntries.sortBy(_.name)
      e1.zip(e2).foreach { case (a, b) =>
        assert(a.name === b.name )
        assert(a.size === b.size, s"Size does not compare for ${a.name}")
        assert(a.checksum === b.checksum, s"Checksum does not compare for ${a.name}")
      }

    }
  }

  case class JEntry(name: String, size: Long, checksum: String = "")


  private def jarEntryNameGenerator(file: os.Path): Seq[JEntry] = {
    val ois = new JarInputStream(file.getInputStream)
    val dis = new DigestInputStream(ois, MessageDigest.getInstance("SHA-1"))
    try {
      var entry: JarEntry = ois.getNextJarEntry()
      var fullEntries: Seq[JEntry] = Seq()
      while (entry != null) {
        if (!entry.isDirectory()) {
          val name = entry.getName()
          val (size, checksum) = {
            val buf = new Array[Byte](1024)
            var fullLen = 0L
            var len = 0
            while ( {
              len = dis.read(buf)
              len > 0
            }) {
              fullLen += len
            }
            fullLen -> dis.getMessageDigest().digest().map(b => "%02x".format(b)).mkString
          }
          fullEntries = fullEntries ++ Seq(JEntry(name, size, checksum))
        }
        entry = ois.getNextJarEntry()
      }
      fullEntries
    } finally {
      dis.close()
      ois.close()
    }
  }
}
