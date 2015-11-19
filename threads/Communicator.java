package nachos.threads;

import java.util.Random;
import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
       
        communicatorLock.acquire();
        
        while (listening == 0 || message != null) {
                speakerCondition.sleep();
        }
        
        message = new Integer(word);
        listenerCondition.wakeAll();
        communicatorLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */
    public int listen() {
	
        communicatorLock.acquire();
	listening++;
	
        while (message == null) {
	    speakerCondition.wakeAll();
	    listenerCondition.sleep();
	}
	
        int receivedMessage = message.intValue();
	message = null;
	listening--;
	communicatorLock.release();
	return receivedMessage;
    }

    private static class Speaker implements Runnable {
	Speaker(Communicator com, String name) {
	    this.com = com;
	    this.name = name;
	}

	public void run() {
	    //two things to say
	    for (int j=0;j<=2;j++) {
                int i= new Random().nextInt(10)+1;
		com.speak(i);
		System.out.println(name + " says " + i);
	    }
	    
	}

	private Communicator com;
	private String name;
    }

    private static class Listener implements Runnable {
	Listener(Communicator com, String name) {
	    this.com = com;
	    this.name = name;
	}

	public void run() {
	    //two things to hear
	    for(int j=0;j<=2;j++){
                int heard = com.listen();
                System.out.println(name + " hears " + heard);
            }
        }

	private Communicator com;
	private String name;
    }

    public static void selfTest() {
	Communicator com1 = new Communicator();

        KThread thread1 = new KThread(new Speaker(com1, "Speaker 1"));
        KThread thread2 = new KThread(new Listener(com1, "Listner 1"));
        KThread thread3 = new KThread(new Speaker(com1, "Speaker 2"));
        KThread thread4 = new KThread(new Listener(com1, "Listner 2"));
        KThread thread5 = new KThread(new Listener(com1, "Listner 3"));
        
        thread1.fork();
        thread2.fork();
        thread3.fork();
        thread4.fork();
        thread5.fork();
        
        
    }

    private Integer message = null;
    private int listening = 0;
    private Lock communicatorLock = new Lock();
    private Condition2 listenerCondition = new Condition2(communicatorLock);
    private Condition2 speakerCondition = new Condition2(communicatorLock);
}