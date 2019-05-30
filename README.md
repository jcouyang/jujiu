# <ruby><rb>雎鳩</rb><rt>ju jiu</rt></ruby>

Functional Scala Caching

[![CircleCI](https://circleci.com/gh/jcouyang/jujiu.svg?style=svg)](https://circleci.com/gh/jcouyang/jujiu)
[![Javadocs](https://www.javadoc.io/badge/us.oyanglul/jujiu_2.12.svg?label=document)](https://www.javadoc.io/doc/us.oyanglul/jujiu_2.12)
[![](https://jitpack.io/v/jcouyang/jujiu.svg)](https://jitpack.io/#jcouyang/jujiu)
[![codecov](https://codecov.io/gh/jcouyang/jujiu/branch/master/graph/badge.svg)](https://codecov.io/gh/jcouyang/jujiu)

[<img src=https://upload.wikimedia.org/wikipedia/commons/7/7e/Imperial_Encyclopaedia_-_Animal_Kingdom_-_pic009_-_%E9%9B%8E%E9%B3%A9%E5%9C%96.svg width=40%/>](https://en.wikisource.org/wiki/zh:%E5%8F%A4%E4%BB%8A%E5%9C%96%E6%9B%B8%E9%9B%86%E6%88%90/%E5%8D%9A%E7%89%A9%E5%BD%99%E7%B7%A8/%E7%A6%BD%E8%9F%B2%E5%85%B8/%E7%AC%AC011%E5%8D%B7)
> 关关雎鸠 在河之洲

# Making [Caffeine](https://github.com/ben-manes/caffeine) ![Cats Friendly Badge](https://typelevel.org/cats/img/cats-badge-tiny.png) 

```scala
object cache extends CaffeineCache[IO, String, String]
val program = for {
  r1 <- cache.fetch("not exist yet")
  r2 <- cache.fetch("not exist yet", _ => IO("default"))
  _ <- cache.put("not exist yet", "overrided")
  r3 <- cache.fetch("not exist yet")
} yield (r1, r2, r3)
program(Caffeine().sync).unsafeRunSync() must_== ((None, "default", Some("overrided")))
```
you can find the code in [test](https://github.com/jcouyang/jujiu/blob/master/src/test/scala/us/oyanglul/JujiuSpec.scala) as well, I can walk you through line by line though

- `object cache extends CaffeineCache[IO, String, String]` \
  it creates an instance of `CaffeineCache` which has side effect `IO`, key is `String` and value is `String` as well
- `val program = for {` \
let us give the following process a name `program`
- `r1 <- cache.fetch("not exist yet")` \
 `cache.fetch` won't acutally trigger any effect, it just returns a DSL, represent as type `Klesili[IO, Cache, String]`
 which in English you can read as, "give me a `Cache` and I can provide you an `IO[String]`"

- `r2 <- cache.fetch("not exist yet", _ => IO("default"))` \
  this is new `fetch` DSL, the second parameter is a function `K => IO[V]`, if cache not exist, it will run the function can put the result into the cache, and return the value

- `_ <- cache.put("not exist yet", "overrided")` \
  `put` will update the value of key "not exist yet" to "overrided"

- `program(Caffeine().sync).unsafeRunSync() must_== ((None, "default", Some("overrided")))` \
  `Caffeine().sync` is the Scala idiomatic syntax to build synchronize Caffeine Cache \
  if you still recall that the `program` is actually `Klesili[IO, Cache, String]` so now \
  I provide it a `Cache` by `program(Caffeine().sync)` \
  it shall return me a `IO[String]`
  `.unsafeRunSync()` the IO and all effects you described before in `program` will be triggered \
  and you will get the actual result
  
## and Cats IO
Dealing with Java DSL and Java Future is too verbose and painful in Scala project

Let's see how Jiujiu makes Caffeine friendly to Cats IO as well

A good example is the Async Loading Cache
```scala
import scala.concurrent.ExecutionContext.Implicits.global
object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
  implicit val executionContext = global  // provide excution context for exec Future
}
implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global) // context shift for parallel fetchAll

val program = for {
  r1 <- cache.fetch(1)
  r2 <- cache.fetch(2)
  r3 <- cache.fetchAll(List[Integer](1, 2, 3))
} yield (r1, r2, r3)

val caffeine = Caffeine()
  .executionContext(global) //
  .expire(
    (_: Integer, _: String) => { 1.second }, // after create
    (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration, // after write
    (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration // after read
  )
  .async((key: Integer) => IO("async string" + key))

program(caffeine).unsafeRunSync() must_== (
  (
    "async string1",
    "async string2",
    List("async string1", "async string2", "async string3")
  ) 
)
```

- Async Loading Cache need an Execution Context to execute the Java Future things

```scala
object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
  implicit val executionContext = global  // provide excution context for exec Future
}
```


- `ContextShift` is for runing `IO` in parallel, will explain in later `fetchAll`
```scala
implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
```

- `program` is pretty much the same, it is just DSL \
but here `fetchAll` is async loading all values of keys and so it should run in **parallel** \
in Cats IO we need to tell how to **shift** thread \
usually your `IO` should `run` in `IOApp`(which provided you context already) so you won't need to worry too much about that

- `.executionContext(global)` will make sure the cache using Scala execution context otherwise its default java folk join pool.
```scala
val caffeine = Caffeine()
  .executionContext(global)
```

-  `expire` default the expiring policy, here it's more Scala way using lambda and `Duration`
```scala
  .expire(
    (_: Integer, _: String) => { 1.second }, // after create
    (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration, // after write
    (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration // after read
  )
```
- `.async((key: Integer) => IO("async string" + key))` will create an async loading cache \
the async loading function that it will use is `K => IO[V]` so you don't need to deal with awful Java Future.
