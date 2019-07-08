package com.dkomlen.bastion

import com.danielasfregola.twitter4s.entities.Tweet
import com.typesafe.scalalogging.LazyLogging

trait Filter extends LazyLogging {
  def run(tweets: Seq[Tweet]): Seq[Tweet]
}
