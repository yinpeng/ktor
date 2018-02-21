Short description
=================

The following code where `ThreadCont` is `AtomicReferenceFieldUpdater` and we use its `getAndSet` method: 

```kotlin
   val cont = ThreadCont.getAndSet(threads[i], null)
   if (cont != null) { /* do something */ }
```

> Line 76 of [IOCoroutineDispatcher.kt](ktor-network/src/io/ktor/network/util/IOCoroutineDispatcher.kt#L76). 

Gets compiled under certain circumstances by 
Java version "1.8.0_161" HotSpot(TM) 64-Bit Server VM (build 25.161-b12, mixed mode) to:

```asm
  0x00000001110d42af: xor    r8d,r8d
  0x00000001110d42b2: xchg   QWORD PTR [rcx+0x1b8],r8  ;*invokevirtual getAndSetObject
       ; - java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl::getAndSet@19 (line 468)
       ; - io.ktor.network.util.IOCoroutineDispatcher::resumeAnyThread@38 (line 76)
  0x00000001110d42b9: mov    r8,QWORD PTR [rsp+0x28]
```

> Line 444114 of [run_test.txt](run_test.txt#L444114)

The result of getAndSet (xchg) gets immediately lost (overwritten).

Steps to reproduce
==================

Build test source:

```bash
/gradlew :ktor-client:ktor-client-cio:compileTestKotlin
```

Run the test using [run_test.sh](run_test.sh) script:

```bash
./run_test.sh
```

Wait for approximately 1 minute (indicates that test hangs), then analyze the resulting [run_test.txt](run_test.txt) and
find the miscompiled version of `io.ktor.network.util.IOCoroutineDispatcher::resumeAnyThread`.

Workarounds
===========

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

Also note that `threads` in this code in an `ArrayList`. Replacing it with a plain JVM array makes the problem go away.

The problem does not immediately reproduce under Java 9 (build 9+181).

Technical notes
===============

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
 