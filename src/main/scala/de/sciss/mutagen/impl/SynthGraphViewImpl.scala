package de.sciss.mutagen
package impl

import java.awt.geom.{Line2D, Point2D, Rectangle2D}
import java.awt.{BasicStroke, Color, Font, FontMetrics, Graphics2D, GraphicsEnvironment, Rectangle, RenderingHints, Shape}

import de.sciss.lucre.swing.requireEDT
import de.sciss.lucre.synth.Sys
import de.sciss.synth.UGenSpec
import prefuse.controls.{PanControl, ZoomControl}
import prefuse.data.{Graph, Node}
import prefuse.render.{AbstractShapeRenderer, DefaultRendererFactory, Renderer}
import prefuse.util.ColorLib
import prefuse.visual.{EdgeItem, VisualItem}
import prefuse.{Display, Visualization}

import scala.swing.Component

object SynthGraphViewImpl {
  def apply(top: Top): Unit = {

  }

  object PaneImpl {
    def apply(): PaneImpl = {
      val res = new Impl
      res
    }

    private case class PutMetaData(point: Point2D, edit: Boolean)

    private final val GROUP_GRAPH   = "graph"
    private final val COL_DATA      = "data"
    // private final val COL_PORTS     = "ports"

    private final class Impl extends PaneImpl {

      val visualization = new Visualization
      private val rf = new DefaultRendererFactory(new BoxRenderer(this), new CableRenderer(this))
      visualization.setRendererFactory(rf)

      //      private var editingNode     = Option.empty[VisualItem]
      //      private var editingOldText  = ""

      // val dragControl   = new DragControl(this)
      val display  = {
        val res   = new Display(visualization)
        res
      }
      private val g   = new Graph
      g .addColumn(COL_DATA , classOf[AnyRef /* VisualBox */])
      visualization.addGraph(GROUP_GRAPH, g)

      // display.addControlListener(dragControl)
      display.addControlListener(new PanControl())
      display.addControlListener(new ZoomControl())

      // private def lastMousePoint(): Point2D = dragControl.mousePoint

      //      def elemAdded(elem: Obj[S])(implicit tx: S#Tx): Unit = elem match {
      //        case IntElem.Obj(a)           => addVertex(elem, new VisualInt              [S](tx.newHandle(a), a.elem.peer.value))
      //        case BooleanElem.Obj(a)       => addVertex(elem, new VisualBoolean          [S](tx.newHandle(a), a.elem.peer.value))
      //        case UGenSource.Obj(a)        => addVertex(elem, new VisualUGenSource       [S](tx.newHandle(a), a.elem.spec      ))
      //        case IncompleteElement.Obj(a) => addVertex(elem, new VisualIncompleteElement[S](tx.newHandle(a), a.elem.peer.value))
      //        case Connection.Obj(a)        => addEdge(a)
      //        case _                        => // ignore
      //      }

      //      private def addEdge(cObj: Connection.Obj[S])(implicit tx: S#Tx): Unit = {
      //        val c = cObj.elem
      //        // println(s"addEdge($c")
      //        for {
      //          sourceData <- viewMap.get(c.source._1.id)
      //          sinkData   <- viewMap.get(c.sink  ._1.id)
      //        } deferTx {
      //          for {
      //            sourceNode <- sourceData.node
      //            sinkNode   <- sinkData  .node
      //          } {
      //            val e     = g.addEdge(sourceNode, sinkNode)
      //            // println(s"edge = $e")
      //            val data  = VisualEdge(c.source._2, c.sink._2)
      //            e.set(COL_DATA, data)
      //          }
      //        }
      //      }

      //      private def addVertex(elem: Obj[S], data: VisualBox)(implicit tx: S#Tx): Unit = {
      //        viewMap.put(elem.id, data)
      //        val cueOpt = cueMap.get(elem.id)
      //        if (cueOpt.nonEmpty) cueMap.remove(elem.id)
      //        // println(s"Get cue $cueOpt")
      //        deferTx {
      //          val n       = g.addNode()
      //          n.set(COL_DATA, data)
      //          data.init(n)
      //
      //          val vi      = visualization.getVisualItem(GROUP_GRAPH, n)
      //          val mp      = cueOpt.map(_.point).getOrElse(lastMousePoint())
      //          vi.setX(mp.getX)
      //          vi.setY(mp.getY)
      //          // val ports = new VisualPorts(numIns = 0, numOuts = 1)
      //          data.ports.update(vi.getBounds)
      //          // vi.set(COL_PORTS, ports)
      //          if (cueOpt.exists(_.edit)) editObject(vi)
      //          visualization.repaint()
      //        }
      //      }

      //      def elemRemoved(elem: Obj[S])(implicit tx: S#Tx): Unit = {
      //        val dataOpt = viewMap.get(elem.id)
      //        cueMap.remove(elem.id)
      //
      //        dataOpt.foreach { data =>
      //          viewMap.remove(elem.id)
      //          deferTx {
      //            data.node.foreach { n =>
      //              g.removeNode(n)
      //              visualization.repaint()
      //            }
      //          }
      //        }
      //      }

      val component = Component.wrap(display)

      def getNodeData (vi: VisualItem): Option[VisualBox] = vi.get(COL_DATA) match {
        case b: VisualBox  => Some(b)
        case _                => None
      }

      def getEdgeData (vi: VisualItem): Option[VisualEdge  ] = vi.get(COL_DATA) match {
        case e: VisualEdge    => Some(e)
        case _                => None
      }

      private def updateEditingBounds(vi: VisualItem): Rectangle = {
        //      vi.validateBounds()
        vi.setValidated(false)  // this causes a subsequent call to getBounds to ask the renderer again, plus creates dirty screen region
        val b      = vi.getBounds
        val at     = display.getTransform
        val r      = at.createTransformedShape(b).getBounds
        r.x       += 3
        r.y       += 1
        r.width   -= 5
        r.height  -= 2
        getNodeData(vi).foreach(_.ports.update(b))
        r
      }

      //      def connect(source: VisualBox, out: Port.Out, sink: VisualBox, in: Port.In): Unit =
      //        cursor.step { implicit tx =>
      //          val sourceArt = source.source()
      //          val sinkArt   = sink  .source()
      //          val sourceLet = out.idx
      //          val sinkLet   = in .idx
      //          val conn      = Obj(Connection.apply(source = (sourceArt, sourceLet), sink = (sinkArt, sinkLet)))
      //          val p         = patcher()
      //          p.addNode(conn)
      //        }
    }
  }
  sealed trait PaneImpl /* extends Pane[S] */ {
    def visualization: Visualization
    def display      : Display
    def getNodeData (vi: VisualItem): Option[VisualBox]
    def getEdgeData (vi: VisualItem): Option[VisualEdge]
  }

  ////////////////////////////////////

  object BoxRenderer {
    final val MinBoxWidth         = 24
    final val DefaultBoxHeight    = 18

    def defaultFontMetrics: FontMetrics = Renderer.DEFAULT_GRAPHICS.getFontMetrics(Style.font)

    private final val colrSel     = Style.selectionColor
    private final val strkColrOk  = ColorLib.getColor(192, 192, 192)
    private final val strkColrEdit= colrSel
    private final val strkColrErr = ColorLib.getColor(240,   0,   0)
    private final val fillColr    = Style.boxColor
    private final val textColrEdit= strkColrEdit
    private final val textColr    = Color.black
    private final val strkShpOk   = new BasicStroke(1f)
    private final val strkShpPend = new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, Array[Float](6, 4), 0f)
    private final val portColr    = Style.portColor
  }
  final class BoxRenderer(d: PaneImpl) extends AbstractShapeRenderer {
    import de.sciss.mutagen.impl.SynthGraphViewImpl.BoxRenderer._

    private val r   = new Rectangle2D.Float()
    // private val r2  = new Rectangle2D.Float()

    protected def getRawShape(vi: VisualItem): Shape = {
      var x    = vi.getX
      if (x.isNaN || x.isInfinity) x = 0.0
      var y    = vi.getY
      if (y.isNaN || y.isInfinity) y = 0.0

      d.getNodeData(vi).fold[Shape] {
        r.setRect(x, y, MinBoxWidth, DefaultBoxHeight)
        r
      } { data =>
        data.renderer.getShape(x, y, data)
      }
    }

    override def render(g: Graphics2D, vi: VisualItem): Unit = {
      val shp = getShape(vi)
      val b   = shp.getBounds2D
      g.setColor(fillColr)
      g.fill(shp)

      d.getNodeData(vi).foreach { data =>
        g.setColor (strkColrOk)
        g.setStroke(strkShpOk )
        g.draw(shp)
        g.setColor(textColr)
        g.setFont(Style.font)
        // val fm  = Renderer.DEFAULT_GRAPHICS.getFontMetrics(Style.font)

        data.renderer.paint(g, b, data)

        val ports   = data.ports
        if (ports.nonEmpty) {
          val atOrig  = g.getTransform
          val x       = b.getX.toFloat
          val y       = b.getY.toFloat
          g.translate(x, y)
          g.setColor(portColr)
          ports.inlets .foreach(g.fill)
          ports.outlets.foreach(g.fill)
          ports.active.foreach { p =>
            val r0 = p.visualRect(ports)
            g.setColor(colrSel)
            r.setRect(r0.getX - 1, r0.getY - 1, r0.getWidth + 2, r0.getHeight + 2)
            g.fill(r0)
          }
          g.setTransform(atOrig)
        }
      }
    }
  }

  //////////////////////////

  sealed trait VisualBoxLike {
    def renderer: ElementRenderer

    def ports: VisualPorts

    def value: Any

    private var _node = Option.empty[Node]

    def init(node: Node): Unit = {
      requireEDT()
      require(_node.isEmpty, "Already initialized")
      _node = Some(node)
    }

    def node: Option[Node] = {
      requireEDT()
      _node
    }
  }

  sealed trait VisualBox extends VisualBoxLike

  class VisualUGenSource(spec: UGenSpec)
    extends VisualBox {

    def value = spec

    val ports = VisualPorts(numIns = spec.args.size, numOuts = spec.outputs.size)

    def renderer: ElementRenderer = UGenSourceRenderer
  }

  class VisualInt(var value: Int)
    extends VisualBox {

    val ports = VisualPorts(numIns = 0, numOuts = 1)

    def renderer: ElementRenderer = ToStringRenderer
  }

  class VisualBoolean(var value: Boolean)
    extends VisualBox {

    val ports = VisualPorts(numIns = 0, numOuts = 1)

    def renderer: ElementRenderer = BooleanRenderer
  }

  case class VisualEdge(outlet: Int, inlet: Int)

  ///////////////////////////

  trait ElementRenderer {
    def getShape(x: Double, y: Double           , data: VisualBoxLike): Shape

    def paint(g: Graphics2D, bounds: Rectangle2D, data: VisualBoxLike): Unit
  }

  trait StringRendererLike extends ElementRenderer {
    private val r = new Rectangle2D.Float()

    protected def dataToString(data: VisualBoxLike): String

    def getShape(x: Double, y: Double, data: VisualBoxLike): Shape = {
      val fm    = BoxRenderer.defaultFontMetrics
      val w1    = fm.stringWidth(dataToString(data))
      val w2    = math.max(BoxRenderer.MinBoxWidth, w1 + 6)
      val ports = data.ports
      val w3    = math.max(ports.numIns, ports.numOuts) * VisualPorts.MinSpacing
      val w     = math.max(w2, w3)
      r.setRect(x, y, w, BoxRenderer.DefaultBoxHeight)
      r
    }

    def paint(g: Graphics2D, bounds: Rectangle2D, data: VisualBoxLike): Unit = {
      val x   = bounds.getX.toFloat
      val y   = bounds.getY.toFloat
      // g.setFont(Style.font)
      val fm  = g.getFontMetrics
      g.drawString(dataToString(data), x + 3, y + 2 + fm.getAscent)
    }
  }

  object ToStringRenderer extends StringRendererLike {
    protected def dataToString(data: VisualBoxLike) = data.value.toString
  }

  object BooleanRenderer extends ElementRenderer {
    final val DefaultWidth  = 16
    final val DefaultHeight = 16

    private val ln  = new Line2D.Float()
    private val r   = new Rectangle2D.Float()

    def getShape(x: Double, y: Double, data: VisualBoxLike): Shape = {
      r.setRect(x, y, DefaultWidth, DefaultHeight)
      r
    }

    def paint(g: Graphics2D, bounds: Rectangle2D, data: VisualBoxLike): Unit =
      data.value match {
        case true =>
          g.setColor(Color.black)
          ln.setLine(bounds.getMinX + 2, bounds.getMinY + 2, bounds.getMaxX - 2, bounds.getMaxY - 2)
          g.draw(ln)
          ln.setLine(bounds.getMinX + 2, bounds.getMaxY - 2, bounds.getMaxX - 2, bounds.getMinY + 2)
          g.draw(ln)
        case _ =>
      }
  }
  object UGenSourceRenderer extends StringRendererLike {
    protected def dataToString(data: VisualBoxLike) = data.value match {
      case spec: UGenSpec => spec.name
      case _ => "???"
    }
  }
  
  //////////////////////////////

  object VisualPorts {
    final val MinSpacing = 10

    def apply(numIns: Int, numOuts: Int): VisualPorts = new VisualPorts(numIns, numOuts)
  }
  final class VisualPorts private(val numIns: Int, val numOuts: Int) {
    val inlets  = Vec.fill(numIns )(new Rectangle2D.Float)
    val outlets = Vec.fill(numOuts)(new Rectangle2D.Float)
    var active  = Option.empty[Port]

    def isEmpty   = numIns == 0 && numOuts == 0
    def nonEmpty  = !isEmpty

    def update(bounds: Rectangle2D): Unit = {
      //      val x       = bounds.getX.toFloat
      //      val y       = bounds.getY.toFloat
      val x       = 0f
      val y       = 0f
      val w       = bounds.getWidth.toFloat
      val h       = bounds.getHeight.toFloat
      val wm      = w - 7
      if (numIns > 0) {
        val xf = if (numIns > 1) wm / (numIns - 1) else 0f
        var i = 0; while (i < numIns) {
          inlets(i).setRect(x + i * xf, y, 8, 3)
          i += 1 }
      }
      if (numOuts > 0) {
        val xf = if (numOuts > 1) wm / (numOuts - 1) else 0f
        val y2 = y + h - 2 /* 3 */
        var i = 0; while (i < numOuts) {
          outlets(i).setRect(x + i * xf, y2, 8, 3)
          i += 1 }
      }
    }
  }
  
  ///////////////////////

  object Port {
    final case class In(idx: Int) extends Port {
      def visualRect(ports: VisualPorts)  = ports.inlets(idx)
    }
    final case class Out(idx: Int) extends Port {
      def visualRect(ports: VisualPorts)  = ports.outlets(idx)
    }
  }
  sealed trait Port {
    def visualRect  (ports: VisualPorts): Rectangle2D
  }

  ///////////////////////

  object Style {
    final val font: Font = {
      val fntNames  = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
      val fntMenlo  = "Menlo"
      val name      = if (fntNames.contains(fntMenlo)) {
        fntMenlo
      } else {
        Font.MONOSPACED
      }
      new Font(name, Font.PLAIN, 11)
    }

    final val selectionColor  = ColorLib.getColor(  0,   0, 240)
    final val boxColor        = ColorLib.getColor(246, 248, 248)
    final val portColor       = ColorLib.getColor( 80,  80, 128)
  }

  ///////////////////////

  object CableRenderer {
    private final val portColr  = Style.portColor
    private final val strk      = new BasicStroke(1.5f)
  }
  final class CableRenderer[S <: Sys[S]](d: PaneImpl) extends AbstractShapeRenderer {
    import de.sciss.mutagen.impl.SynthGraphViewImpl.CableRenderer._

    private val line = new Line2D.Double()

    protected def getRawShape(vi: VisualItem): Shape = {
      val edge      = vi.asInstanceOf[EdgeItem]
      val sourceVi  = edge.getSourceItem
      val sinkVi    = edge.getTargetItem

      for {
        edgeData   <- d.getEdgeData(edge    )
        sourceData <- d.getNodeData(sourceVi)
        sinkData   <- d.getNodeData(sinkVi  )
      } {
        val sourcePorts = sourceData.ports
        val sourceR     = sourcePorts.outlets(edgeData.outlet)
        val sinkPorts   = sinkData  .ports
        val sinkR       = sinkPorts  .inlets (edgeData.inlet )
        val x1          = sourceR.getCenterX + sourceVi.getX
        val y1          = sourceR.getCenterY + sourceVi.getY
        val x2          = sinkR  .getCenterX + sinkVi  .getX
        val y2          = sinkR  .getCenterY + sinkVi  .getY

        line.setLine(x1, y1, x2, y2)
      }

      strk.createStrokedShape(line)
    }

    override def render(g: Graphics2D, vi: VisualItem): Unit = {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setColor(portColr)
      val shp = getShape(vi)
      g.fill(shp)
    }
  }
}