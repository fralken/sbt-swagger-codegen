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
package eu.unicredit.swagger.generators

import eu.unicredit.swagger.SwaggerConverters
import io.swagger.parser.SwaggerParser

import scala.collection.JavaConverters._
import scala.meta._

class DefaultModelGenerator extends ModelGenerator with SwaggerConverters {

  def generateStatement(name: String, parameters: Option[List[Term.Param]], comments: Option[String]): Stat = {
    parameters match {
      case None => q"case object ${Term.Name(name)}"
      case Some(params) =>  q"case class ${Type.Name(name)} (..$params)"
    }
  }

  def generateImports(): List[Import] = {
    List.empty
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode] = {
    val swagger = new SwaggerParser().read(fileName)

    val models = swagger.getDefinitions.asScala

    val pkg = getPackageTerm(destPackage)
    val imports = generateImports()

    for {
      (name, model) <- models
    } yield
      SyntaxCode(name + ".scala",
        pkg,
        imports,
        List(generateStatement(name.capitalize, propertiesToParams(model.getProperties), Option(model.getDescription))))
  }
}
