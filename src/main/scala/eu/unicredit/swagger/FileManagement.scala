package eu.unicredit.swagger

import java.io.File

object FolderCreator {
 
  def genPackage(base: String, packageName: String): File = {
    packageName.split('.').foldLeft(new File(base))((act, item) => {
     val file = new File(act, item)
     if (!file.exists()) file.mkdir()
     file
    })
  }

}

object FileWriter {
  import java.nio.file.Files
  import java.nio.file.Paths
  
  def writeToFile(f: File, s: String) =
    Files.write(Paths.get(f.getAbsolutePath), s.getBytes)
  
}