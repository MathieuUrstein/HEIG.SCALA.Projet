package bootstrap

import com.google.inject.AbstractModule

class DBInitialization extends AbstractModule {
  protected def configure(): Unit = {
    bind(classOf[InitialData]).asEagerSingleton()
  }
}
