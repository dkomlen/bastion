### Bastion

Simple Twitter bot that does tweet searches and shares popular tweets under target account. 

### Configuration

Create `src/main/resources/application.conf`:
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

### Setup

```
> sbt assembly
> serverless deploy -v
```
