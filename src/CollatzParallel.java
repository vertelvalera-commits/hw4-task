import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class CollatzParallel {

    static final int TOTAL        = 10_000_000;
    static final int THREAD_COUNT = resolveThreadCount();
    static final int CHUNK_SIZE   = 50_000;

    static int resolveThreadCount() {
        String prop = System.getProperty("threadCount");
        String val  = (prop != null && !prop.isBlank()) ? prop : System.getenv("THREAD_COUNT");
        if (val != null && !val.isBlank()) {
            try {
                int n = Integer.parseInt(val.trim());
                if (n > 0) return n;
            } catch (NumberFormatException ignore) {}
        }
        return Runtime.getRuntime().availableProcessors();
    }

    static int collatzSteps(long n) {
        int steps = 0;
        while (n != 1) {
            n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
            steps++;
        }
        return steps;
    }

    static Result runSequential() {
        long[] steps = new long[TOTAL];
        long t0 = System.currentTimeMillis();

        for (int i = 1; i <= TOTAL; i++) {
            steps[i - 1] = collatzSteps(i);
        }

        return new Result("Sequential (1 thread)", System.currentTimeMillis() - t0, average(steps));
    }
    static Result runManualThreads() throws InterruptedException {
        long[] steps  = new long[TOTAL];
        int chunk      = TOTAL / THREAD_COUNT;
        Thread[] threads = new Thread[THREAD_COUNT];

        long t0 = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int from = t * chunk + 1;
            final int to   = (t == THREAD_COUNT - 1) ? TOTAL : (t + 1) * chunk;

            threads[t] = new Thread(() -> {
                for (int i = from; i <= to; i++) {
                    steps[i - 1] = collatzSteps(i);
                }
            });
            threads[t].start();
        }

        for (Thread th : threads) th.join();

        return new Result(
            "Manual Threads (" + THREAD_COUNT + " th, static split)",
            System.currentTimeMillis() - t0, average(steps));
    }

    static Result runThreadPoolQueue() throws InterruptedException {
        long[] steps      = new long[TOTAL];
        AtomicInteger cursor = new AtomicInteger(1);
        CountDownLatch latch  = new CountDownLatch(THREAD_COUNT);

        long t0 = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            new Thread(() -> {
                while (true) {
                    int from = cursor.getAndAdd(CHUNK_SIZE);
                    if (from > TOTAL) break;
                    int to = Math.min(from + CHUNK_SIZE - 1, TOTAL);
                    for (int i = from; i <= to; i++) {
                        steps[i - 1] = collatzSteps(i);
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        return new Result(
            "ThreadPool + dynamic queue (" + THREAD_COUNT + " th, chunk=" + CHUNK_SIZE + ")",
            System.currentTimeMillis() - t0, average(steps));
    }

    static Result runForkJoin() throws Exception {
        long[] steps = new long[TOTAL];
        ForkJoinPool pool = new ForkJoinPool(THREAD_COUNT);

        long t0 = System.currentTimeMillis();

        pool.submit(() ->
            IntStream.rangeClosed(1, TOTAL).parallel().forEach(i ->
                steps[i - 1] = collatzSteps(i)
            )
        ).get();

        pool.shutdown();

        return new Result(
            "ForkJoinPool / work-stealing (" + THREAD_COUNT + " th)",
            System.currentTimeMillis() - t0, average(steps));
    }

    static double average(long[] arr) {
        long sum = 0;
        for (long v : arr) sum += v;
        return (double) sum / arr.length;
    }

    record Result(String label, long ms, double avg) {
        void print(long seqMs) {
            double speedup = seqMs > 0 ? (double) seqMs / ms : 1.0;
            System.out.printf("  %-55s  %6d ms   avg=%.2f steps   speedup x%.2f%n",
                label, ms, avg, speedup);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        Collatz Conjecture — Parallel Computing               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.printf("  Numbers      : 1 .. %,d%n", TOTAL);
        System.out.printf("  CPU cores    : %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("  THREAD_COUNT : %d%n", THREAD_COUNT);
        System.out.printf("  CHUNK_SIZE   : %,d%n%n", CHUNK_SIZE);

        List<Result> results = new ArrayList<>();

        System.out.println("[1/4] Sequential...");
        results.add(runSequential());

        System.out.println("[2/4] Manual Threads (static distribution)...");
        results.add(runManualThreads());

        System.out.println("[3/4] ThreadPool + dynamic queue (no idle threads)...");
        results.add(runThreadPoolQueue());

        System.out.println("[4/4] ForkJoinPool (work-stealing)...");
        results.add(runForkJoin());

        long seqMs = results.get(0).ms();

        System.out.println();
        System.out.println("┌─────────────────────────────────────────────────────────────────────────────┐");
        System.out.printf("│  %-55s  %8s   %-20s  %-10s│%n", "Approach", "Time", "Avg steps", "Speedup");
        System.out.println("├─────────────────────────────────────────────────────────────────────────────┤");
        for (Result r : results) r.print(seqMs);
        System.out.println("└─────────────────────────────────────────────────────────────────────────────┘");
    }
}
