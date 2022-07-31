package dev.faustin0.importer.domain

import dev.faustin0.domain.TransformError

import scala.xml.NodeSeq

trait XmlDecoder[T] {
  def decode(xml: NodeSeq): Either[TransformError, T]
}

object XmlDecoder {

  object syntax {

    implicit final class XmlOps(val xml: NodeSeq) extends AnyVal {

      def decodeTo[T: XmlDecoder]: Either[TransformError, T] =
        implicitly[XmlDecoder[T]].decode(xml)

    }

  }

  implicit val busStopDecoderInstance: BusStopDecoder.type = BusStopDecoder

}
