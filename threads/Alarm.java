package nachos.threads;

import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        
        waitQueue=new PriorityQueue<ThreadWithTime>();
                
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	 
        long time = Machine.timer().getTime();
        
        boolean inStatus=Machine.interrupt().disable();
        
        while(!waitQueue.isEmpty() && waitQueue.peek().wakeTime<=time){
            ThreadWithTime threadWithTime = waitQueue.poll();
            KThread t = threadWithTime.thread;
            t.ready();
        }
        KThread.yield();
        
        Machine.interrupt().restore(inStatus);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	
        long wakeTime = Machine.timer().getTime() + x;
        
        KThread t = KThread.currentThread();
        ThreadWithTime waitThread=new ThreadWithTime(t,wakeTime);
        
        boolean inStatus=Machine.interrupt().disable();
        
        waitQueue.add(waitThread);
        KThread.sleep();
        
        Machine.interrupt().restore(inStatus);
        
    }
    
    private class ThreadWithTime implements Comparable<ThreadWithTime>{
        KThread thread;
        long wakeTime;

        public ThreadWithTime(KThread thread, long wakeTime){
            this.thread=thread;
            this.wakeTime=wakeTime;
        }

        public int compareTo(ThreadWithTime thread){
            if(this.wakeTime<thread.wakeTime)return -1;
            else if(this.wakeTime>thread.wakeTime)return 1;
            else return 0;
        }

    }
    private PriorityQueue<ThreadWithTime> waitQueue;
       
}
