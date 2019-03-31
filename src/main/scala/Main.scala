import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.dkomlen.bastion._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object Main extends Main {
  def main(args: Array[String]): Unit = {
    handleRequest(null, null)
  }
}

class Main extends RequestHandler[ScheduledEvent, Unit] with LazyLogging {

  val config = ConfigFactory.load().as[BastionConfig]("bastion")
  val processor = new WorkflowProcessor(config)

  def handleRequest(event: ScheduledEvent, context: Context) = {

    try {
      processor.processWorkflows()
    }
    catch {
      case e: Throwable => {
        logger.error("Handle request failed: {}", e.getMessage)
        // Avoid retries
      }
    }
  }
}
