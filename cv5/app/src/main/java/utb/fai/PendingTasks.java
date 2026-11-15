package utb.fai;

public class PendingTasks {

    private int running;

    public synchronized void started() {
        running++;
    }

    public synchronized void finished() {
        running--;
        if (running == 0) {
            notifyAll();
        }
    }

    public synchronized void awaitAllFinished() throws InterruptedException {
        while (running > 0) {
            wait();
        }
    }
}
