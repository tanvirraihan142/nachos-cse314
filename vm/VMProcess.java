package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        //System.out.println("Swap Count "+ SwapFile.swapCount);
        VMKernel.tlbScheduler.clearTLB(processID); //flush TLB + write te back dirty bit
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
            int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // address translation
        int vpn = vaddr / pageSize;
        int voffset = vaddr % pageSize;
        TranslationEntry entry = getPP(vpn, false);
        entry.used = true;
        int paddr = entry.ppn * pageSize + voffset;

        // if entry is not valid, then don't read
        if (paddr < 0 || paddr >= memory.length || !entry.valid || entry==null) {
            return 0;
        }

        int amount = Math.min(length, memory.length - paddr);

        // copies 'amount' bytes from byte array 'memory' starting at byte 'vaddr'
        // to byte array 'data' starting 'offset' bytes into 'data'
        System.arraycopy(memory, paddr, data, offset, amount);

        return amount;
    }
    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
            int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // address translation
        int vpn = vaddr / pageSize;
        int voffset = vaddr % pageSize;
        TranslationEntry entry = getPP(vpn, true);
     
        entry.used = true;
        entry.dirty = true;
        int paddr = entry.ppn * pageSize + voffset;

        // can't write it entry is not valid or read only
        if (paddr < 0 || paddr >= memory.length || !entry.valid || entry.readOnly || entry==null) {
            return 0;
        }

        entry.dirty = true;
        int amount = Math.min(length, memory.length - paddr);
        System.arraycopy(data, offset, memory, paddr, amount);

        return amount;
    }
    
    
    
    public static VMProcess newUserProcess() {
        return (VMProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    public TranslationEntry getPP(int vpn, boolean writeBit) {
        TranslationEntry entry = VMKernel.pageScheduler.getPageEntry(loader, processID, vpn);
        if (entry == null || entry.readOnly && writeBit) {
            return null;
        }
        return entry;
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {//use lazy loader
        loader = new LazyLoader(coff);
        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        VMKernel.tlbScheduler.clearTLB(processID);
        VMKernel.pageScheduler.clearPage(processID);
        coff.close();
    }

    /**
     * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
     * . The <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    
    public void handleExit(int exit) {
        System.out.println("Swap Count "+ SwapFile.swapCount);
        super.handleExit(exit);
    }
    
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionTLBMiss:
                VMKernel.tlbScheduler.handleTLBMiss(loader, processID, Processor.pageFromAddress(Machine.processor().readRegister(Processor.regBadVAddr)));
                break;

            case Processor.exceptionPageFault:
                VMKernel.pageScheduler.handlePageFault(loader, processID, Processor.pageFromAddress(Machine.processor().readRegister(Processor.regBadVAddr)));
                break;
            

            default:
                super.handleException(cause);
                break;
        }
    }

    private LazyLoader loader;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
