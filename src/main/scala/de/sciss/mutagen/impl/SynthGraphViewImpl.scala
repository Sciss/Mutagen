package de.sciss.mutagen
package impl

import java.awt.geom.{Rectangle2D, Line2D, Point2D}
import java.awt.{BasicStroke, Color, Font, FontMetrics, Graphics2D, GraphicsEnvironment, RenderingHints, Shape}

import de.sciss.lucre.swing.requireEDT
import de.sciss.lucre.synth.Sys
import prefuse.action.layout.Layout
import prefuse.action.{RepaintAction, ActionList}
import prefuse.action.layout.graph.{ForceDirectedLayout, NodeLinkTreeLayout}
import prefuse.activity.Activity
import prefuse.controls.{DragControl, PanControl, ZoomControl}
import prefuse.data.{Graph, Node}
import prefuse.render.{AbstractShapeRenderer, DefaultRendererFactory, Renderer}
import prefuse.util.ColorLib
import prefuse.visual.{EdgeItem, VisualItem}
import prefuse.{Display, Visualization}

import scala.collection.JavaConversions
import scala.swing.Component

object SynthGraphViewImpl {
  def apply(top: Top): Pane = PaneImpl(top)

  object PaneImpl {
    def apply(top: Top): PaneImpl = {
      requireEDT()
      val res = new Impl(top)
      top.vertices.foreach {
        case v: Vertex.Constant if !top.edges.exists(_.targetVertex == v) =>  // ignore loose constants
        case v =>
          res.addVertex(v)
      }
      top.edges   .foreach(res.addEdge  )
      res.visualization.run(ACTION_LAYOUT1)
      // res.visualization.run(ACTION_LAYOUT2)
      res
    }

    private case class PutMetaData(point: Point2D, edit: Boolean)

    private final val GROUP_GRAPH   = "graph"
    private final val COL_DATA      = "data"
    // private final val COL_PORTS     = "ports"
    private val ACTION_LAYOUT1 = "layout1"
    private val ACTION_LAYOUT2 = "layout2"
    private final val LAYOUT_TIME   = 50

    private final class Impl(top: Top) extends PaneImpl {

      val visualization = new Visualization
      private val rf = new DefaultRendererFactory(new BoxRenderer(this), new CableRenderer(this))
      visualization.setRendererFactory(rf)

      //      private var editingNode     = Option.empty[VisualItem]
      //      private var editingOldText  = ""

      private val lay1 = new NodeLinkTreeLayout(GROUP_GRAPH, prefuse.Constants.ORIENT_BOTTOM_TOP /* ORIENT_TOP_BOTTOM */, 50, 5, 25)
      private val actionLayout1 = new ActionList()
      actionLayout1.add(lay1)
      visualization.putAction(ACTION_LAYOUT1, actionLayout1)

      //      private val lay2 = new ForceDirectedLayout(GROUP_GRAPH)
      //      private val actionLayout2 = new ActionList(Activity.INFINITY, LAYOUT_TIME)
      //      actionLayout2.add(lay2)
      //      actionLayout2.add(new RepaintAction())
      //      visualization.putAction(ACTION_LAYOUT2, actionLayout2)
      //      visualization.alwaysRunAfter(ACTION_LAYOUT1, ACTION_LAYOUT2)

      private var viewMap = Map.empty[Vertex, VisualBox]

      private object MyLayout extends Layout {
        private final val V_SPACE   = 32
        private final val H_SPACE   = 16
        private final val LAY_STEP  = 16

        private val rect = new Rectangle2D.Double

        def run(frac: Double): Unit = {
          val actions = viewMap.map { case (v, box) =>
            val vi = visualization.getVisualItem(GROUP_GRAPH, box.node)
            val b  = vi.getBounds
            val x0 = vi.getX
            val y0 = vi.getY

            var xTgt = x0
            var yTgt = y0

            top.edgeMap.get(v).fold {
              val r = top.edges.collect {
                case e if e.targetVertex == v =>
                  val box1  = viewMap(e.sourceVertex)
                  val vi1   = visualization.getVisualItem(GROUP_GRAPH, box1.node)
                  vi1.getBounds
              }
              if (r.nonEmpty) {
                val rMin = r.minBy(_.getMinY)
                yTgt = rMin.getMinY - V_SPACE - b.getHeight
                xTgt = rMin.getMinX + (b.getWidth - rMin.getWidth)/2
              }

            } { edgeMap =>
              val r = edgeMap.map { e =>
                val box1  = viewMap(e.targetVertex)
                val vi1   = visualization.getVisualItem(GROUP_GRAPH, box1.node)
                vi1.getBounds
              }
              val rMax  = r.maxBy(_.getMaxY)
              yTgt      = rMax.getMaxY + V_SPACE
              xTgt      = rMax.getMinX + (b.getWidth - rMax.getWidth)/2
            }

            // calculate average in order
            // to avoid infinite step oscillation between two interdependent objects
            val xTgt0 = (x0 + xTgt) / 2
            val yTgt0 = (y0 + yTgt) / 2
            val y     = if (yTgt0 < y0 - LAY_STEP) y0 - LAY_STEP else if (yTgt0 > y0 + LAY_STEP) y0 + LAY_STEP else yTgt0
            val isVertical = math.abs(yTgt0 - y) >= 0.1
            // once the y-coordinates are in place, start fixing horizontal overlaps
            val x = if (isVertical)
              if (xTgt0 < x0 - LAY_STEP) x0 - LAY_STEP else if (xTgt0 > x0 + LAY_STEP) x0 + LAY_STEP else xTgt0
            else {
              val deltas = viewMap.flatMap {
                case (v1, box1) if v1 != v =>
                  val vi1 = visualization.getVisualItem(GROUP_GRAPH, box1.node)
                  val b1  = vi1.getBounds
                  rect.setRect(x0, y, b.getWidth, b.getHeight)
                  if (b1.intersects(rect)) {
                    val c1 = b1  .getCenterX
                    val c2 = rect.getCenterX
                    val xTgt1 = if (c1 < c2) {  // move to the right
                      b1.getMaxX + H_SPACE
                    } else {        // move to the left
                      b1.getMinX - H_SPACE - rect.getWidth
                    }
                    val delta = xTgt1 - x0
                    Some(delta)

                  } else None

                case _ => None
              }

              if (deltas.isEmpty) x0 else {
                val delta   = deltas.maxBy(math.abs)
                val delta0  = (x0 + delta) / 2
                if (delta0 < x0 - LAY_STEP) x0 - LAY_STEP else if (delta0 > x0 + LAY_STEP) x0 + LAY_STEP else delta0
              }
            }

            (vi, x, y)
          }

          actions.foreach { case (vi, x, y) =>
            setX(vi, null, x)
            setY(vi, null, y)
          }
        }
      }

      private val lay2 = MyLayout
      private val actionLayout2 = new ActionList(8000) // // Activity.INFINITY) // (8000, LAYOUT_TIME)
      actionLayout2.add(lay2)
      actionLayout2.add(new RepaintAction())
      visualization.putAction(ACTION_LAYOUT2, actionLayout2)
      visualization.alwaysRunAfter(ACTION_LAYOUT1, ACTION_LAYOUT2)

      val dragControl   = new DragControl() // (this)
      val display  = {
        val res   = new Display(visualization)
        res
      }
      private val g   = new Graph
      g .addColumn(COL_DATA , classOf[AnyRef /* VisualBox */])
      visualization.addGraph(GROUP_GRAPH, g)

      display.addControlListener(dragControl)
      display.addControlListener(new PanControl())
      display.addControlListener(new ZoomControl())

      // private def lastMousePoint(): Point2D = dragControl.mousePoint

      def addVertex(elem: Vertex): Unit = elem match {
        case a: Vertex.UGen     => addVertex(elem, new VisualUGenSource(a))
        case a: Vertex.Constant => addVertex(elem, new VisualConstant  (a))
      }

      def addEdge(c: Edge): Unit = {
        for {
          sourceData <- viewMap.get(c.targetVertex)
          sinkData   <- viewMap.get(c.sourceVertex)
        } /* deferTx */ {
          val sourceNode = sourceData.node
          val sinkNode   = sinkData  .node
          val e     = g.addEdge(sourceNode, sinkNode)
          // val e     = g.addEdge(sinkNode, sourceNode)
          // println(s"edge = $e")
          val inletName = c.inlet
          val inlet = sinkData match {
            case u: VisualUGenSource => u.source.info.args.indexWhere(_.name == inletName)
            case _ => 0 // shouldn't happen!
          }

          val data  = VisualEdge(outlet = 0, inlet = inlet)
          e.set(COL_DATA, data)

          // sourceData.edges += data
          // sinkData  .edges += data
        }
      }

      private def addVertex(elem: Vertex, data: VisualBox): Unit = {
        val id = elem
        viewMap += id -> data
        // val cueOpt = cueMap.get(id)
        // if (cueOpt.nonEmpty) cueMap.remove(elem.id)

        // deferTx {
          val n       = g.addNode()
          n.set(COL_DATA, data)
          data.init(n)

          val vi      = visualization.getVisualItem(GROUP_GRAPH, n)
          // val mp      = cueOpt.map(_.point).getOrElse(lastMousePoint())
          // vi.setX(mp.getX)
          // vi.setY(mp.getY)
          // val ports = new VisualPorts(numIns = 0, numOuts = 1)
          data.ports.update(vi.getBounds)
          // vi.set(COL_PORTS, ports)
          // if (cueOpt.exists(_.edit)) editObject(vi)
          // visualization.repaint()
        // }
      }

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

      //      private def updateEditingBounds(vi: VisualItem): Rectangle = {
      //        //      vi.validateBounds()
      //        vi.setValidated(false)  // this causes a subsequent call to getBounds to ask the renderer again, plus creates dirty screen region
      //        val b      = vi.getBounds
      //        val at     = display.getTransform
      //        val r      = at.createTransformedShape(b).getBounds
      //        r.x       += 3
      //        r.y       += 1
      //        r.width   -= 5
      //        r.height  -= 2
      //        getNodeData(vi).foreach(_.ports.update(b))
      //        r
      //      }

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
  sealed trait PaneImpl extends Pane {
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

    private var _node: Node = _

    def init(node: Node): Unit = {
      requireEDT()
      if (_node != null) throw new IllegalStateException(s"Already initialized: $this")
      _node = node
    }

    def node: Node = {
      // requireEDT()
      if (_node == null) throw new IllegalStateException(s"Not yet initialized: $this")
      _node
    }
  }

  sealed trait VisualBox extends VisualBoxLike {
    // var edges: Set[VisualEdge] = Set.empty
  }

  class VisualUGenSource(val source: Vertex.UGen)
    extends VisualBox {

    def value = source.info

    val ports = VisualPorts(numIns = value.args.size, numOuts = value.outputs.size)

    def renderer: ElementRenderer = UGenSourceRenderer
  }

  class VisualConstant(val source: Vertex.Constant)
    extends VisualBox {

    def value = source.f

    val ports = VisualPorts(numIns = 0, numOuts = 1)

    def renderer: ElementRenderer = ToStringRenderer
  }

  //  class VisualInt(var value: Int)
  //    extends VisualBox {
  //
  //    val ports = VisualPorts(numIns = 0, numOuts = 1)
  //
  //    def renderer: ElementRenderer = ToStringRenderer
  //  }

  //  class VisualBoolean(var value: Boolean)
  //    extends VisualBox {
  //
  //    val ports = VisualPorts(numIns = 0, numOuts = 1)
  //
  //    def renderer: ElementRenderer = BooleanRenderer
  //  }

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
    protected def dataToString(data: VisualBoxLike) = data match {
      case u: VisualUGenSource => u.source.boxName
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
      val fntPref   = "DejaVu Sans Mono"
      val name      = if (fntNames.contains(fntPref)) fntPref else Font.MONOSPACED
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
      //      val sourceVi  = edge.getTargetItem
      //      val sinkVi    = edge.getSourceItem

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

  /////

  trait Pane {
    def component: Component
  }
}