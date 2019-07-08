### Bastion

Framework for running Twitter workflows based on tweet searches, written
in Scala.  

### Usage

```scala
  val config = ConfigFactory.load().as[BastionConfig]("bastion")
  val processor = new WorkflowProcessor(config)
  processor.processWorkflows()
```
 - Runs workflows based on `application.conf` configuration file.
 - Workflow is basd on set of tweet searches and a set of actions.
 - Each action defines which tweets to pick from the search results and
   what to do with every chosen tweet. For example like, retweet etc. 

### Configuration

```
bastion {
    user = "<twitter-user>"
    workflows = [
        {
            searches = [
                "<twitter-search>"
            ],
            result_type = "mixed",
            max_age = 120,
            actions = [
                {
                    filter = ["not-following"]
                    order = ["like"]
                    take = 2
                    act = ["like", "retweet", "follow"]
                    comments = ["<some comment>"]
                }
            ]
        }
    ]
}
twitter {
    consumer {
        key = "<key>"
        secret = "<secret>"
    }
    access {
        key = "<key>"
        secret = "<secret>"
  }
}
```
Available options:
 - result_type: latest, top, mixed
 - filter: not-following, not-reply, not-liked, follows, custom
 - order: like-desc, friends-desc, age-asc
 - act: like, follow, comment, retweet, custom