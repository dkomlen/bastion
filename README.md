### Bastion

Simple Twitter bot that does tweet searches and shares popular tweets under target account. 

### Configuration

Create `src/main/resources/application.conf`:
```
bastion {
  user = "<twitter-user>"
  searches = [
    "<twitter-search-1>",
    "<twitter-search-2>"
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
