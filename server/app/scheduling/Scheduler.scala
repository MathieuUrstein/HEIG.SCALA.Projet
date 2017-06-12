package scheduling

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Configuration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Scheduler @Inject() (val system: ActorSystem, @Named("scheduler-actor") val schedulerActor: ActorRef,
                           configuration: Configuration)(implicit ec: ExecutionContext) {
  // task executed on time per day (the day is the smallest unit for update a budget) (every 24 hours)
  val frequency: Int = configuration.getInt("frequency").get
  var actor: Cancellable = system.scheduler.schedule(frequency.hours, frequency.hours, schedulerActor, "update")
}
