package com.dkomlen.bastion

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.enums.ResultType
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

case class Workflow(
                     searches: List[String],
                     result_type: String,
                     max_age: Option[Int] = None,
                     actions: List[Action],
                   )

case class BastionConfig(
                          user: String,
                          workflows: List[Workflow]
                        )


class WorkflowProcessor(config: BastionConfig) extends LazyLogging {
  val restClient = TwitterRestClient()
  val timeoutMinutes = 1

  val searchProcessor = new SearchProcessor(restClient)
  val userStatus = getStatus(config.user)
  val actionProcessor = new ActionProcessor(restClient, userStatus)

  def processWorkflows(): Unit = {
    logger.info("Starting")
    config.workflows.foreach(processWorkflow)
    logger.info("Shutting down")
    restClient.shutdown()
    logger.info("Exiting")
  }

  def getStatus(user: String): UserStatus = {
    val userTweetsFuture = searchProcessor.userTweets(user)
    val userFollowersFuture = searchProcessor.followers(user)
    val userTweets = getTweets(Seq(userTweetsFuture))
    val retweeted = userTweets.map(_.retweeted_status).flatten.map(_.id).toSet
    val followers = Await.result(userFollowersFuture, timeoutMinutes minutes)
    val likes = getTweets(Seq(searchProcessor.likes(user))).map(_.id).toSet

    UserStatus(
      followers = followers,
      retweetIds = retweeted,
      likes = likes,
      tweets = userTweets
    )
  }

  def processWorkflow(workflow: Workflow) = {
    try {
      workflow.searches.foreach(processSearch(_, workflow))
      logger.info("Process workflow completed")
    } catch {
      case e: Throwable => {
        logger.error("Process workflow failed: {} {}", e.getMessage, workflow)
      }
    }
  }

  def processSearch(query: String, workflow: Workflow) {

    val resultType = workflow.result_type match {
      case "popular" => ResultType.Popular
      case "mixed" => ResultType.Mixed
      case "recent" => ResultType.Recent
      case _ => ResultType.Mixed
    }

    val searchFuture = searchProcessor.search(query, resultType, workflow.max_age)
    val searchTweets = getTweets(Seq(searchFuture))

    logger.info(s"Total: ${searchTweets.length} tweets for search: ${query}")
    val newTweets = getTweets(actionProcessor.process(searchTweets, workflow.actions))

    newTweets.foreach(tweet => {
      logger.info(s"Tweet processed: ${tweet.text}")
    })
    logger.info("Process search completed")
  }

  private def getTweets(futures: Seq[Future[Seq[Tweet]]]): Seq[Tweet] = {
    futures.flatMap(f => {
      try {
        Await.result(f, timeoutMinutes minutes)
      } catch {
        case e: Throwable => {
          logger.error("Process future failed {}", e.getMessage)
          Seq()
        }
      }
    })
  }
}
