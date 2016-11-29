package eu.unicredit.swagger

import com.google.common.base.CaseFormat

object ScalaUtils {

  private val identifier = """^[_\p{L}][_\p{L}\p{Nd}]*$""".r

  private def asId(conv: String => String)(raw: String): String =
    identifier.findFirstIn(raw).getOrElse(conv(raw))

  def asVarId: String => String =
    asId(raw => s"`$raw`")

  def asPlainId: String => String =
    asId(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, _)) _ andThen (_.capitalize)

}
