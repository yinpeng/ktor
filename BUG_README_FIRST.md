# JDK-8198531: C2: Wrong type of return value of Unsafe.getAndSetObject() call

This write up details our findings on HotSpot C2 compiler bug 
while trouble-shooting a failure of `io.ktor.client.engine.cio.CIOPostTest.hugePost` test.

## Short description

In the following snippet of code `ThreadCont` is `AtomicReferenceFieldUpdater` for a `cont` field of
`IOThread` class and `threads` is an `ArrayList<IOThread>`: 

```kotlin
   val cont = ThreadCont.getAndSet(threads[i], null)
   if (cont != null) { /* do something */ }
```

> Line 76 of [IOCoroutineDispatcher.kt](ktor-network/src/io/ktor/network/util/IOCoroutineDispatcher.kt#L76). 

This code gets compiled under certain circumstances by 
Java version "1.8.0_161" HotSpot(TM) 64-Bit Server VM (build 25.161-b12, mixed mode) to:

```asm
  0x00000001110d42af: xor    r8d,r8d
  0x00000001110d42b2: xchg   QWORD PTR [rcx+0x1b8],r8  ;*invokevirtual getAndSetObject
       ; - java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl::getAndSet@19 (line 468)
       ; - io.ktor.network.util.IOCoroutineDispatcher::resumeAnyThread@38 (line 76)
  0x00000001110d42b9: mov    r8,QWORD PTR [rsp+0x28]
```

> Line 444114 of [run_test.txt](run_test.txt#L444114)

The result of `getAndSet` (`xchg`) gets immediately lost and is not used. The whole `if (cont != null) { ... }` block is 
eliminated from the compiled code as if HotSpot C2 assumes that this particular `getAndSet` always returns `null`.
This hypothesis is substantiated by the fact that we can also reproduce the problem while the value of `cont` is 
logged in between `getAndSet` and the subsequent `if (cont != null) { ... }` 
(uncomment line 102 in [IOCoroutineDispatcher.kt](ktor-network/src/io/ktor/network/util/IOCoroutineDispatcher.kt#L102)), 
and the logs do indeed confirm that non-null value was successfully written by the `await` method 
[at line 190](ktor-network/src/io/ktor/network/util/IOCoroutineDispatcher.kt#190),
yet subsequent results of `getAndSet` are showing as `null` in the log.

The bug is reported as [JDK-8198531](https://bugs.openjdk.java.net/browse/JDK-8198531). 

## Steps to reproduce

Build the code using Java 8 (this version cannot be built on Java 9+):

```bash
./gradlew :ktor-client:ktor-client-cio:compileTestKotlin
```

Run the test using [run_test.sh](run_test.sh) script:

```bash
./run_test.sh
```

Wait for approximately 1 minute (indicates that test hangs), then analyze the resulting [run_test.txt](run_test.txt) and
find the miscompiled version of `io.ktor.network.util.IOCoroutineDispatcher::resumeAnyThread`.

## Workarounds

The simplest workaround is to change line 76 of [IOCoroutineDispatcher.kt](ktor-network/src/io/ktor/network/util/IOCoroutineDispatcher.kt).
Replace the following line of code:

```kotlin
    val cont = ThreadCont.getAndSet(threads[i], null) // BAD
```

with this line, where `thread[i]` is extracted into a separate variable before using `getAndSet`:

```kotlin
   val t = threads[i]; val cont = ThreadCont.getAndSet(t, null) // GOOD
```

With this change running the test with the script terminates in ~45 secs. In fact, the test is faster (takes ~5 sec) and
it is all the infrastructure and logging of assembly code that makes it slow. 

Extracting `ThreadCont.getAndSet(t, null)` into a separate method (either static or instance) has the same effect of
working around this problem.

Also note that `threads` in this code in an `ArrayList`. Replacing it with a plain JVM array makes the problem go away, too.

The problem does not immediately reproduce under Java 9 (build 9+181). It is hard to tell why it is exactly so, because
in Java 9 runtime `AtomicReferenceFieldUpdater` implementation is considerably different.

## Technical notes

We use the following JVM settings (among others) when running the test to make analysis of the resulting assembly code easier:

`-XX:-UseCompressedOops` forces 64 bit pointers (makes reference manipulation easier to read).

`-XX:CompileCommand=dontinline,*.resumeAnyThread` makes sure that the miscomiled method is not inlined itself anywhere.

However, _just_ turning off inlining for `resumeAnyThread` makes the problem go away. It is only miscompiled when 
either something else inlineable is inside the method or when it is inlined into a larger body of code up-the-stack.
In our case, we have `logContResume(node)` invocation at the beginning of the method that gets inlined into the 
`resumeAnyThread`, which is enough to reproduce the bug. 
 
`-XX:CompileCommand=dontinline,*AtomicLog.invoke` trims further inlining inside of `logContResume`.
 
 Note, that using `dontinline` on `logContResume` makes the problem go away.
 
 Also note, that in the [run_test.txt](run_test.txt) we can see that `resumeAnyThread` gets compiled three times
 and only the second version is miscompiled. Incidentally, it is only miscompiled in the version where
 `typeCheck` and `accessCheck` [optimization by Aleksey Shipilёv](https://shipilev.net/blog/2015/faster-atomic-fu/) kicks in.
