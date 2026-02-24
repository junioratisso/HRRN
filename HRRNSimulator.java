import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * HRRNSimulator
 * 
 * Simulates the non-preemptive Highest Response Ratio Next (HRRN) scheduling policy.
 * 
 * Input:
 *  - Default generator (no args): generates 100 processes using the assignment's arrival/runtime/deadline scheme.
 *  - Or provide a CSV path as arg0: id,arrival,runtime,deadline (deadline is absolute).
 * 
 * Output:
 *  - One line per process: id, arrival, runtime, deadline, start, finish, turnaround, waiting, responseRatioAtDispatch, metDeadline
 */
public class HRRNSimulator {

    static class Proc {
        final int id;
        final int arrival;
        final int runtime;
        final int deadline; // absolute deadline

        int start = -1;
        int finish = -1;

        Proc(int id, int arrival, int runtime, int deadline) {
            this.id = id;
            this.arrival = arrival;
            this.runtime = runtime;
            this.deadline = deadline;
        }
    }

    /** Non-preemptive HRRN: whenever CPU is free, select ready process with max (waiting+service)/service. */
    static List<Proc> simulateHRRN(List<Proc> procs) {
        // Sort by arrival then id for deterministic behavior
        procs.sort(Comparator.comparingInt((Proc p) -> p.arrival).thenComparingInt(p -> p.id));

        int n = procs.size();
        int nextIdx = 0;
        int time = 0;

        List<Proc> ready = new ArrayList<>();
        List<Proc> finished = new ArrayList<>(n);

        // If first arrival is > 0, jump to it
        if (!procs.isEmpty()) {
            time = Math.min(time, procs.get(0).arrival);
            if (time < procs.get(0).arrival) time = procs.get(0).arrival;
        }

        while (finished.size() < n) {
            // Add all newly-arrived processes to ready queue
            while (nextIdx < n && procs.get(nextIdx).arrival <= time) {
                ready.add(procs.get(nextIdx));
                nextIdx++;
            }

            if (ready.isEmpty()) {
                // CPU idle: jump to next arrival
                if (nextIdx < n) {
                    time = Math.max(time, procs.get(nextIdx).arrival);
                    continue;
                } else {
                    break; // should not happen
                }
            }

            // Pick process with highest response ratio; tie-breakers: earlier arrival, then smaller id
            Proc best = null;
            double bestRR = -1.0;
            for (Proc p : ready) {
                int waiting = Math.max(0, time - p.arrival);
                double rr = ((double) waiting + p.runtime) / (double) p.runtime;
                if (rr > bestRR) {
                    bestRR = rr;
                    best = p;
                } else if (rr == bestRR && best != null) {
                    if (p.arrival < best.arrival || (p.arrival == best.arrival && p.id < best.id)) {
                        best = p;
                    }
                }
            }

            // Dispatch best
            ready.remove(best);
            best.start = time;
            time += best.runtime;
            best.finish = time;
            finished.add(best);
        }

        // Sort finished by id for reporting convenience
        finished.sort(Comparator.comparingInt(p -> p.id));
        return finished;
    }

    static List<Proc> generateDefault(int count) {
        List<Proc> list = new ArrayList<>(count);
        for (int id = 0; id < count; id++) {
            int arrival;
            if (id < 10) arrival = 0;
            else arrival = 5 * (id - 9); // matches example: id 10 -> 5, id 19 -> 50

            int runtime = ((id % 10) + 1) * 5; // 5,10,15,...,50 repeating
            int relDeadline = ((id % 10) + 1) * 10; // 10,20,...,100 repeating
            int deadline = arrival + relDeadline; // absolute

            list.add(new Proc(id, arrival, runtime, deadline));
        }
        return list;
    }

    static List<Proc> loadCsv(String path) throws IOException {
        List<Proc> list = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // allow header
            if (line.toLowerCase().startsWith("id")) continue;
            String[] parts = line.split(",");
            if (parts.length < 4) throw new IllegalArgumentException("Bad CSV line: " + line);
            int id = Integer.parseInt(parts[0].trim());
            int arrival = Integer.parseInt(parts[1].trim());
            int runtime = Integer.parseInt(parts[2].trim());
            int deadline = Integer.parseInt(parts[3].trim());
            list.add(new Proc(id, arrival, runtime, deadline));
        }
        return list;
    }

    static void printReport(List<Proc> finished) {
        System.out.println("HRRN (non-preemptive) simulation report");
        System.out.println("Format: id,arrival,runtime,deadline,start,finish,turnaround,waiting,RR_at_dispatch,met_deadline");

        int met = 0;
        int totalWait = 0;
        int totalTurn = 0;

        // To compute RR at dispatch for reporting, recompute based on recorded start time.
        for (Proc p : finished) {
            int waiting = p.start - p.arrival;
            int turnaround = p.finish - p.arrival;
            double rr = ((double) waiting + p.runtime) / (double) p.runtime;
            boolean metDeadline = p.finish <= p.deadline;
            if (metDeadline) met++;
            totalWait += waiting;
            totalTurn += turnaround;

            System.out.printf(Locale.US,
                    "%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%s%n",
                    p.id, p.arrival, p.runtime, p.deadline, p.start, p.finish,
                    turnaround, waiting, rr, metDeadline ? "YES" : "NO");
        }

        System.out.println();
        System.out.printf(Locale.US, "Summary: processes=%d, met_deadline=%d, missed_deadline=%d%n",
                finished.size(), met, finished.size() - met);
        System.out.printf(Locale.US, "Average waiting time: %.2f%n", (finished.isEmpty() ? 0.0 : (double) totalWait / finished.size()));
        System.out.printf(Locale.US, "Average turnaround time: %.2f%n", (finished.isEmpty() ? 0.0 : (double) totalTurn / finished.size()));
    }

    public static void main(String[] args) {
        try {
            List<Proc> procs;
            if (args.length >= 1) {
                procs = loadCsv(args[0]);
            } else {
                procs = generateDefault(100);
            }

            List<Proc> finished = simulateHRRN(procs);
            printReport(finished);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: java HRRNSimulator [optional_processes.csv]");
            System.err.println("CSV format: id,arrival,runtime,deadline (deadline absolute). Lines starting with # are ignored.");
            System.exit(1);
        }
    }
}
