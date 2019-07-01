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

import io.swagger.models.Response
import io.swagger.models.properties._
import io.swagger.models.parameters._

import scala.collection.JavaConverters._
import scala.meta._

trait SwaggerConverters {

  private def parseUrl(url: String) = {
    val regex = "([^{]*)(?:\\{([^}]+)\\})?".r
    val (p, a) = regex.findAllMatchIn(url).foldLeft((Vector[String](), Vector[String]())){(l, m) =>
      val (parts, args) = l
      (parts :+ m.group(1), Option(m.group(2)).map(args :+ _).getOrElse(args))
    }
    (p.take(a.size + 1).toList, a.toList)
  }

  def patternInterpolateUrl(prefix: String, url: String): Pat.Interpolate = {
    val (parts, args) = parseUrl(url)
    Pat.Interpolate(Term.Name(prefix), parts.map(p => Lit.String(p)), args.map(a => Pat.Var(Term.Name(a))))
  }

  def termInterpolateUrl(prefix: String, url: String): Term.Interpolate = {
    val (parts, args) = parseUrl(url)
    Term.Interpolate(Term.Name(prefix), parts.map(p => Lit.String(p)), args.map(a => Term.Name(a)))
  }

  def isType(what: String, thisType: Type): Boolean = {
    thisType match {
      case Type.Apply(name, _) => name.syntax == what
      case _ => false
    }
  }

  def getTypeIn(what: String, thisType: Type): Type = {
    thisType match {
      case Type.Apply(name, args) if name.syntax == what => args.head
      case _ => thisType
    }
  }

  def isOption(thisType: Type): Boolean = isType("Option", thisType)

  def getTypeInOption(thisType: Type): Type = getTypeIn("Option", thisType)

  def isSeq(thisType: Type): Boolean = isType("Seq", thisType)

  def getTypeInSeq(thisType: Type): Type = getTypeIn("Seq", thisType)

  private def propertyToType(p: Property): Type = {
    if (!p.getRequired)
      t"Option[${propertyToTypeNotOptional(p)}]"
    else
      propertyToTypeNotOptional(p)
  }

  private def propertyToTypeNotOptional(p: Property): Type = {
    p match {
      case _: StringProperty => t"String"
      case _: BooleanProperty => t"Boolean"
      case _: DoubleProperty => t"Double"
      case _: FloatProperty => t"Float"
      case _: IntegerProperty => t"Int"
      case _: LongProperty => t"Long"
      case _: BaseIntegerProperty => t"Int"
      case m: MapProperty => t"Map[String, ${propertyToTypeNotOptional(m.getAdditionalProperties)}]"
      case a: ArrayProperty => t"Seq[${propertyToTypeNotOptional(a.getItems)}]"
      case _: DecimalProperty => t"BigDecimal"
      case r: RefProperty => Type.Name(r.getSimpleRef.capitalize)
      case _: DateProperty => t"java.time.LocalDate"
      case _: DateTimeProperty => t"java.time.OffsetDateTime"
      case _: UUIDProperty => t"java.util.UUID"

      //case _: ByteArrayProperty => throw new Exception(s"ByteArrayProperty $p is not supported yet")
      case _: BinaryProperty => throw new Exception(s"BinaryProperty $p is not supported yet")
      // supported as a subclass of StringProperty
      //case _: EmailProperty => throw new Exception(s"EmailProperty $p is not supported yet")
      case _: FileProperty => throw new Exception(s"FileProperty $p is not supported yet")
      case _: ObjectProperty => throw new Exception(s"ObjectProperty $p is not supported yet")
      case _: PasswordProperty => throw new Exception(s"PasswordProperty $p is not supported yet")

      case null => throw new Exception("Trying to resolve null property")
      // should not happen as all existing types have been checked before
      case x => throw new Exception(s"unexpected property type $x")
    }
  }

  private def parameterToType(p: Parameter): Type = {
    if (!p.getRequired)
      t"Option[${parameterToTypeNotOptional(p)}]"
    else
      parameterToTypeNotOptional(p)
  }

  private def parameterToTypeNotOptional(p: Parameter): Type = {
    p match {
      case asp: AbstractSerializableParameter[_] =>
        if (asp.getType == "array")
          t"Seq[${propertyToTypeNotOptional(asp.getItems)}]"
        else
          propertyToTypeNotOptional(PropertyBuilder.build(asp.getType, asp.getFormat, null))
      case bp: BodyParameter =>
        propertyToTypeNotOptional(new RefProperty(bp.getSchema.getReference))
      case rp: RefParameter =>
        Type.Name(rp.getSimpleRef.capitalize)
    }
  }

  def parametersToBodyParams(params: List[Parameter]): List[Term.Param] =
    params.collect {
      case p: BodyParameter =>
        Term.Param(List.empty, Name(p.getName), Some(parameterToType(p)), None)
    }

  def parametersToHeaderParams(params: List[Parameter]): List[Term.Param] =
    params.collect {
      case p: HeaderParameter =>
        Term.Param(List.empty, Name(p.getName), Some(parameterToType(p)), None)
    }

  def parametersToPathParams(params: List[Parameter]): List[Term.Param] =
    params.collect {
      case p: PathParameter =>
        Term.Param(List.empty, Name(p.getName), Some(parameterToType(p)), None)
    }

  def parametersToQueryParams(params: List[Parameter]): List[Term.Param] =
    params.collect {
      case p: QueryParameter =>
        val tpe = parameterToType(p)
        val realTpe = if (isSeq(getTypeInOption(tpe))) getTypeInOption(tpe) else tpe
        Term.Param(List.empty, Name(p.getName), Some(realTpe), None)
    }

  def propertiesToParams(properties: java.util.Map[String, Property]): Option[List[Term.Param]] = {
    if (properties == null)
      None
    else
      Some(properties.asScala.map { case (pname, prop) =>
        val default = if (prop.getRequired) None else Some(Term.Name("None"))
        Term.Param(List.empty, Name(pname), Some(propertyToType(prop)), default)
      }.toList)
  }

  def getParamsNames(params: List[Term.Param]): List[Term.Name] = params.map(p => Term.Name(p.name.value))

  def getResponseType(response: Response): Option[Type] = {
    Option(response.getSchema).map(propertyToTypeNotOptional)
  }

  def getImporters(imports: (String, String)*): List[Importer] = {
    imports.map { case (importer, importee) =>
      Importer(getPackageTerm(importer), List(Importee.Name(Name(importee))))
    }.toList
  }

  def getPackageTerm(destPackage: String): Term.Ref = {
    destPackage.split("\\.").map(Term.Name(_)).toList match {
      case a :: b :: tail => tail.foldLeft(Term.Select(a, b))(Term.Select(_, _))
      case a  => a.head
    }
  }
}
