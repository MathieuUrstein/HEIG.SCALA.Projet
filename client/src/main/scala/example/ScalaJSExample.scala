package example

import org.scalajs.dom.ext.Ajax

import scala.scalajs.js

object ScalaJSExample extends js.JSApp {
  def main(): Unit = {
    Ajax.get("")
  }
}
