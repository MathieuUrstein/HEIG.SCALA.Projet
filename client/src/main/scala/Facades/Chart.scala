package Facades

import scala.scalajs.js
import org.scalajs.dom.CanvasRenderingContext2D

@js.native
trait Tooltips extends js.Object {}
object Tooltips {
  def apply(
             mode: String = "nearest",
             intersect: Boolean = true
           ): Tooltips = {
    js.Dynamic.literal(
      mode = mode,
      intersect = intersect
    )
  }.asInstanceOf[Tooltips]
}

@js.native
trait Legend extends js.Object {}
object Legend {
  def apply(
             display: Boolean = true
           ): Legend = {
    js.Dynamic.literal(
      display = display
    )
  }.asInstanceOf[Legend]
}

@js.native
trait Dataset extends js.Object {}
object Dataset {
  def apply(
             label: String = "",
             data: js.Array[Double] = js.Array(),
             backgroundColor: js.Array[String] = js.Array(),
             borderColor: String = "transparent",
             fill: Boolean = true,
             borderWidth: Int = 1,
             `type`: String = ""
           ): Dataset = {
    js.Dynamic.literal(
      label = label,
      data = data,
      backgroundColor = backgroundColor,
      borderColor = borderColor,
      fill = fill,
      borderWidth = borderWidth,
      `type` = `type`
    )
  }.asInstanceOf[Dataset]
}

@js.native
trait Data extends js.Object {}
object Data {
  def apply(
             datasets: js.Array[Dataset] = js.Array(),
             labels: js.Array[String] = js.Array()
           ): Data = {
    js.Dynamic.literal(
      datasets = datasets,
      labels = labels
    )
  }.asInstanceOf[Data]
}

@js.native
trait Stack extends js.Object {}
object Stack {
  def apply(
             stacked: Boolean = false
           ): Stack = {
    js.Dynamic.literal(
      stacked = stacked
    )
  }.asInstanceOf[Stack]
}

@js.native
trait Scales extends js.Object {}
object Scales {
  def apply(
             xAxes: js.Array[Stack] = js.Array(),
             yAxes: js.Array[Stack] = js.Array()
           ): Scales = {
    js.Dynamic.literal(
      xAxes = xAxes,
      yAxes = yAxes
    )
  }.asInstanceOf[Scales]
}

@js.native
trait Options extends js.Object {}
object Options {
  def apply(
             legend: Legend = Legend(),
             responsive: Boolean = true,
             tooltips: Tooltips = Tooltips(),
             scales: Scales = Scales()
           ): Options = {
    js.Dynamic.literal(
      legend = legend,
      responsive = responsive,
      tooltips = tooltips,
      scales = scales
    ).asInstanceOf[Options]
  }
}

@js.native
trait Config extends js.Object {}
object Config {
  def apply(
             `type`: String = "",
             data: Data = Data(),
             options: Options = Options()
           ): Config = {
    js.Dynamic.literal(
      `type` = `type`,
      data = data,
      options = options
    )
  }.asInstanceOf[Config]
}

@js.native
class Chart protected() extends js.Object {
  def this(context: CanvasRenderingContext2D, conf: Config) = this()
  def destroy() = js.native
}

@js.native
object Chart extends js.Object {
  var defaults: js.Any = js.native
}