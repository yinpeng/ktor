package io.ktor.network.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.internal.*
import kotlin.coroutines.experimental.intrinsics.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*

private val DI = AtomicInteger()

class IOCoroutineDispatcher(private val nThreads: Int) : CoroutineDispatcher(), Closeable {
    private val dispatcherThreadGroup = ThreadGroup(ioThreadGroup, "io-pool-group-sub")
    private val tasks = LockFreeLinkedListHead()

    init {
        require(nThreads > 0) { "nThreads should be positive but $nThreads specified"}
    }

    private val di = DI.incrementAndGet()
    private val log = AtomicLog()

    private fun dump() {
        synchronized(DI) {
            System.err.println("===== $di =====")
            for (t in threads) {
                System.err.println(t.name)
                for (traceElement in t.stackTrace)
                    System.err.println("\tat $traceElement")
                System.err.println()
            }
            System.err.println("---------")
            log.dump(System.err)
        }
    }

    private val dumper = thread(name = "dumper-$di") {
        while (true) {
            Thread.sleep(10_000)
            dump()
        }
    }

    private val threads = (0 until nThreads).map { i ->
        IOThread(i).apply { start() }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
//        log("dispatch $block")
        val node: LockFreeLinkedListNode = if (block is LockFreeLinkedListNode && block.isFresh) {
            tasks.addLast(block)
            block
        } else {
            IODispatchedTask(block).also { tasks.addLast(it) }
        }
        resumeAnyThread(node)
    }

    override fun close() {
        if (tasks.prev is Poison) return
        tasks.addLastIfPrev(Poison()) { prev -> prev !is Poison }
        resumeAllThreads()
    }

    private fun resumeAnyThread(node: LockFreeLinkedListNode) {
    // -XX:CompileCommand=dontinline,*.logCont*
    // -XX:CompileCommand=dontinline,*AtomicLog.invoke
    // -XX:CompileCommand=dontinline,*.resumeAnyThread
    // -XX:CompileCommand=dontinline,*IOThread.await
        logContResume(node)
        val threads = threads
        @Suppress("LoopToCallChain")
        for (i in 0 until nThreads) {
//            val t = threads[i] ; val cont = ThreadCont.getAndSet(t, null) // GOOD
            val cont = ThreadCont.getAndSet(threads[i], null) // BAD

//            val cont = if (threads[i].cont == null) null else
//                ThreadCont.getAndSet(threads[i], null) // BAD

//            val cont = threads[i].fetchContInstance() // GOOD

//            val cont = threads[i].fetchContStatic() // GOOD

//            val cont: Continuation<Unit>?
//            loop@while (true) {
//                val t = threads[i]
//                val c = ThreadCont.get(t)
//                when (c) {
//                    null -> {
//                        cont = null
//                        break@loop
//                    }
//                    else -> if (ThreadCont.compareAndSet(t, c, null)) {
//                        cont = c
//                        break@loop
//                    }
//                }
//
//            }
//            log("resumeAnyThread cont[$i] = $cont")
//            logContResult(i, cont)
            if (cont != null) {
//                logContResume(i)
                cont.resume(Unit)
                return
            } else if (node.isRemoved) return
        }
    }

    private fun logContResume(node: LockFreeLinkedListNode) {
        log("resumeAnyThread $node")
    }

    private fun logContResult(i: Int, cont: Continuation<Unit>?) {
        log("resumeAnyThread cont[$i] == $cont")
    }

    private fun logContResume(i: Int) {
        log("resumeAnyThread resuming, resulting activeSuspends=${activeSuspends.decrementAndGet()}")
    }

    internal fun IOThread.fetchContStatic() = ThreadCont.getAndSet(this, null)

    private fun resumeAllThreads() {
        val threads = threads
        for (i in 0 until nThreads) {
            ThreadCont.getAndSet(threads[i], null)?.resume(Unit)
        }
    }

    private val activeRuns = AtomicInteger(0)
    private val activeSuspends = AtomicInteger(0)

    internal inner class IOThread(private val index: Int) : Thread(dispatcherThreadGroup, "io-$di-thread-$index") {
        @Volatile
        @JvmField
        var cont : Continuation<Unit>? = null

        init {
            isDaemon = true
        }

        fun fetchContInstance() = ThreadCont.getAndSet(this, null)

        override fun run() {
            runBlocking(CoroutineName("io-$di-dispatcher-executor-$index")) {
                try {
                    while (true) {
                        val task = receiveOrNull() ?: break
                        log("run: before $task, other activeRuns=${activeRuns.getAndIncrement()}")
                        run(task)
                        log("run: after $task, other activeRuns=${activeRuns.decrementAndGet()}")
                    }
                } catch (t: Throwable) {
                    println("thread died: $t")
                }
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveOrNull(): Runnable? {
            val r = tasks.removeFirstIfIsInstanceOf<Runnable>()
            if (r != null) {
                log("receiveOrNull -> $r")
                return r
            }
            log("receiveOrNull -> suspend")
            return receiveSuspend()
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveSuspend(): Runnable? {
            do {
                val t = tasks.removeFirstIfIsInstanceOf<Runnable>()
                if (t != null) {
                    log("receiveSuspend -> $t")
                    return t
                }
                if (tasks.next is Poison) return null
                log("receiveSuspend -> await")
                await()
            } while (true)
        }

        private val awaitSuspendBlock = { c: Continuation<Unit>? ->
            // nullable param is to avoid null check
            // we know that it is always non-null
            // and it will never crash if it is actually null
            if (!ThreadCont.compareAndSet(this, null, c)) throw IllegalStateException()
            log("awaitSuspendBlock cont[$index] := $c")
            if (tasks.next !== tasks) {
                log("awaitSuspendBlock: queue is not empty")
                if (ThreadCont.compareAndSet(this, c, null)) {
                    log("awaitSuspendBlock cont[$index] := null -> Unit")
                    Unit
                } else {
                    log("awaitSuspendBlock -> COROUTINE_SUSPENDED, resulting activeSuspends=${activeSuspends.incrementAndGet()}")
                    COROUTINE_SUSPENDED
                }
            } else {
                log("awaitSuspendBlock -> COROUTINE_SUSPENDED, resulting activeSuspends=${activeSuspends.incrementAndGet()}")
                COROUTINE_SUSPENDED
            }
        }

        private suspend fun await() {
            return suspendCoroutineOrReturn(awaitSuspendBlock)
        }

        private fun run(task: Runnable) {
            try {
                task.run()
            } catch (t: Throwable) {
                println("Crash: $t")
            }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val ThreadCont =
                AtomicReferenceFieldUpdater.newUpdater<IOThread, Continuation<*>>(IOThread::class.java, Continuation::class.java, IOThread::cont.name)
        as AtomicReferenceFieldUpdater<IOThread, Continuation<Unit>?>
    }

    private class Poison : LockFreeLinkedListNode()
    private class IODispatchedTask(val r: Runnable) : LockFreeLinkedListNode(), Runnable by r
}
