/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package eu.unicredit.swagger

import java.io.File

object FolderCreator {

  def genPackage(base: String, packageName: String): File = {
    val packagePath = base + File.separator + packageName.replace(".", File.separator)
    val packageDir = new File(packagePath)
    packageDir.mkdirs()

    packageDir
  }

}

object FileWriter {
  import java.nio.file.Files
  import java.nio.file.Paths

  def writeToFile(f: File, s: String) =
    Files.write(Paths.get(f.getAbsolutePath), s getBytes "UTF-8")

}
