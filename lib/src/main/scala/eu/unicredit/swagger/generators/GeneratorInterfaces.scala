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

import scala.meta._

case class SyntaxCode(name: String, pkg: Term.Ref, imports: List[Import], statements: List[Stat]) {
  val code: String = q"package $pkg { ..${imports ++ statements} }".syntax
}

trait Generator {}

trait ModelGenerator extends Generator {
  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode]
}

trait JsonGenerator extends Generator {
  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode]
}

trait ServerGenerator extends Generator {
  def generateRoutes(fileName: String, destPackage: String): Option[String] = None

  def generate(fileName: String, destPackage: String, codeProvidedPackage: String): Iterable[SyntaxCode]
}

trait ClientGenerator extends Generator {
  def generate(fileName: String, destPackage: String): Iterable[SyntaxCode]
}
