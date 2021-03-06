/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.atlas.chart

import java.awt.Color
import java.awt.Rectangle
import java.awt.font.LineBreakMeasurer
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.AttributedString
import javax.imageio.IIOImage
import javax.imageio.ImageIO

import com.sun.imageio.plugins.png.PNGMetadata


object PngImage {

  def apply(bytes: Array[Byte]): PngImage = {
    val input = new ByteArrayInputStream(bytes)
    apply(input)
  }

  def apply(input: InputStream): PngImage = {
    import scala.collection.JavaConversions._

    try {
      val iterator = ImageIO.getImageReadersBySuffix("png")
      require(iterator.hasNext, "no image readers for png")

      val reader = iterator.next()
      reader.setInput(ImageIO.createImageInputStream(input), true)

      val index = 0
      val metadata = reader.getImageMetadata(index).asInstanceOf[PNGMetadata]
      val keys = metadata.tEXt_keyword.toList
      val values = metadata.tEXt_text.toList

      val image = reader.read(index)

      PngImage(image, keys.zip(values).toMap)
    } finally {
      input.close()
    }
  }

  def error(msg: String, width: Int, height: Int): PngImage = {
    val fullMsg = "ERROR: " + msg

    val image = newBufferedImage(width, height)
    val g = image.createGraphics

    g.setPaint(Color.BLACK)
    g.fill(new Rectangle(0, 0, width, height))

    g.setPaint(Color.WHITE)
    val attrStr = new AttributedString(fullMsg)
    val iterator = attrStr.getIterator
    val measurer = new LineBreakMeasurer(iterator, g.getFontRenderContext)

    val wrap = width - 8.0f
    var y = 0.0f
    while (measurer.getPosition < fullMsg.length) {
      val layout = measurer.nextLayout(wrap)
      y += layout.getAscent
      layout.draw(g, 4.0f, y)
      y += layout.getDescent + layout.getLeading
    }

    PngImage(image, Map.empty)
  }

  def diff(img1: RenderedImage, img2: RenderedImage): PngImage = {
    val bi1 = newBufferedImage(img1)
    val bi2 = newBufferedImage(img2)

    val dw = List(img1.getWidth, img2.getWidth).max
    val dh = List(img1.getHeight, img2.getHeight).max

    val diffImg = newBufferedImage(dw, dh)
    val g = diffImg.createGraphics
    g.setPaint(Color.BLACK)
    g.fill(new Rectangle(0, 0, dw, dh))

    val red = Color.RED.getRGB

    var count = 0
    var x = 0
    while (x < dw) {
      var y = 0
      while (y < dh) {
        if (contains(bi1, x, y) && contains(bi2, x, y)) {
          val c1 = bi1.getRGB(x, y)
          val c2 = bi2.getRGB(x, y)
          if (c1 != c2) {
            diffImg.setRGB(x, y, red)
            count += 1
          }
        } else {
          diffImg.setRGB(x, y, red)
          count += 1
        }
        y += 1
      }
      x += 1
    }

    val identical = (count == 0).toString
    val diffCount = count.toString
    val meta = Map("identical" -> identical, "diff-pixel-count" -> diffCount)
    PngImage(diffImg, meta)
  }

  private def contains(img: RenderedImage, x: Int, y: Int): Boolean = {
    x < img.getWidth && y < img.getHeight
  }

  private def newBufferedImage(w: Int, h: Int): BufferedImage = {
    new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  }

  private def newBufferedImage(img: RenderedImage): BufferedImage = {
    img match {
      case bi: BufferedImage => bi
      case _ =>
        val w = img.getWidth
        val h = img.getHeight
        val bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = bi.createGraphics
        g.drawRenderedImage(img, new AffineTransform)
        bi
    }
  }
}

case class PngImage(data: RenderedImage, metadata: Map[String, String]) {

  type JList = java.util.List[String]

  def toByteArray: Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    write(buffer)
    buffer.toByteArray
  }

  def write(output: OutputStream) {
    try {
      val iterator = ImageIO.getImageWritersBySuffix("png")
      require(iterator.hasNext, "no image writers for png")

      val writer = iterator.next()
      writer.setOutput(ImageIO.createImageOutputStream(output))

      val pngMeta = new PNGMetadata
      metadata.foreach { t =>
        pngMeta.tEXt_keyword.asInstanceOf[JList].add(t._1)
        pngMeta.tEXt_text.asInstanceOf[JList].add(t._2)
      }

      val iioImage = new IIOImage(data, null, null)
      iioImage.setMetadata(pngMeta)
      writer.write(null, iioImage, null)
    } finally {
      output.close()
    }
  }
}
