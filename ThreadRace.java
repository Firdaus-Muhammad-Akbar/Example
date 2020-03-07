import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.*;

public class ThreadRace {
    private static final Long UPDATE_RATE = 100l;
    private static final Long DISPLAY_RATE = 1000l;
    private static final Long KM_TO_MS = 3600000l;

    class ThreadRacer implements Callable<ThreadRacer>, Comparable<ThreadRacer> {
        private final Integer id;
        private final Double speed;
        private final Double trackLength;
        private volatile Double pos = 0d;
        private Long start;
        private LocalTime end;

        ThreadRacer(Integer id, Double speed, Double trackLength) {
            this.id = id;
            this.speed = speed / KM_TO_MS * UPDATE_RATE;
            this.trackLength = trackLength;
        }

        @Override
        public ThreadRacer call() {
            start = System.currentTimeMillis();
            while (pos < trackLength) {
                try {
                    Thread.sleep(UPDATE_RATE);
                    pos += speed;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            end = getTime();
            return this;
        }

        private LocalTime getTime() {
            Long end = System.currentTimeMillis();
            Long nanos = TimeUnit.MILLISECONDS.toNanos(end - start);
            return LocalTime.ofNanoOfDay(nanos);
        }

        public void timestamp() {
            System.out.printf("[%s] Thread %d - %.2f km%n", getTime(), id, pos);
        }

        public Long getDurationInNano() {
            return TimeUnit.NANOSECONDS.toMillis(end.toNanoOfDay()) - start;
        }

        @Override
        public int compareTo(ThreadRacer oRacer) {
            return (int) (getDurationInNano() - oRacer.getDurationInNano());
        }
    }

    public static void main(String[] args) {
        args = new String[] { "0.1", "60.3", "60.2", "40.3", "44.2", "52.3", "43", "62", "57.21" };
        if (args.length < 3) {
            throw new IllegalArgumentException("At least 3 arguments is required");
        }

        Double trackLength;
        List<Double> speeds;
        try {
            trackLength = Double.parseDouble(args[0]);
            speeds = Arrays.asList(Arrays.copyOfRange(args, 1, args.length)).stream()
                    .mapToDouble(val -> Double.parseDouble(val)).boxed().collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Speeds must be a number");
        }

        ThreadRace app = new ThreadRace();
        app.race(trackLength, speeds);
    }

    private void race(Double trackLength, List<Double> speeds) {
        ExecutorService service = Executors.newFixedThreadPool(speeds.size());
        AtomicInteger i = new AtomicInteger();
        List<ThreadRacer> threads = speeds.stream().map(speed -> {
            return new ThreadRacer(i.incrementAndGet(), speed, trackLength);
        }).collect(Collectors.toList());

        try {
            List<Future<ThreadRacer>> futures = new ArrayList<>();
            threads.forEach(thread -> {
                futures.add(service.submit(thread));
            });
            int second = 0;
            while (!allServiceDone(futures)) {
                try {
                    Thread.sleep(DISPLAY_RATE);
                    System.out.printf("#%d SECOND(S)#%n", ++second);
                    threads.forEach(ThreadRacer::timestamp);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            service.shutdown();
            service.awaitTermination(1, TimeUnit.DAYS);

            System.out.println("#FINISH#");
            i.set(0);
            futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } catch (ExecutionException e1) {
                    e1.printStackTrace();
                }
                return null;
            }).sorted().forEach(future -> {
                ThreadRacer racer = future;
                System.out.printf("%d - Thread %d - %s%n", i.incrementAndGet(), racer.id, racer.end);
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean allServiceDone(List<Future<ThreadRacer>> futures) {
        return futures.stream().map(future -> future.isDone()).reduce(true, (allDone, isDone) -> allDone && isDone);
    }
}
