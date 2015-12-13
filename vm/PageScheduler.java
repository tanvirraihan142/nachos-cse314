/*
 * Phase 3
 */
package nachos.vm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.Config;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class PageScheduler {

    public static int physicalSize;
    // least recently used
    private long[] lru;

    public PageScheduler() {
        swapFile = new SwapFile();
        pageTable = new InvertedPageTable();
    }

    public void init() {
        physicalSize = Machine.processor().getNumPhysPages();

        lru = new long[physicalSize];
        for (int i = 0; i < physicalSize; ++i) {
           lru[i] = Machine.timer().getTime();
        }
        swapFile.init();

        pageLock = new Lock();
    }

    private int getVictim() {

        long time = lru[0];
        int at = 0;
        for (int i = 1; i < lru.length; ++i) {
            if (lru[i] < time) {
                time = lru[i];
                at = i;
            }
        }
        return at;

    }

    // clear all pages when unloadSections() is invoked
    public void clearPage(int processID) {
        pageTable.removeProcessPage(processID);
        swapFile.clearPage(processID);
    }

    public void writePageEntry(int processID, TranslationEntry entry) {
        pageTable.put(processID, entry);
    }

    public TranslationEntry getPageEntry(LazyLoader loader, int processID, int vpn) {
        TranslationEntry entry = pageTable.getTranslationEntry(processID, vpn);

        if (entry == null) {
            handlePageFault(loader, processID, vpn);
            entry = pageTable.getTranslationEntry(processID, vpn);
        }
        if (entry != null){
            lru[entry.ppn] = Machine.timer().getTime();
        }
        return entry;
    }

    public boolean handlePageFault(LazyLoader loader, int processID, int vpn) {
        pageLock.acquire();

        int tmpppn = getVictim();
        int tmppid = pageTable.getProcessID(tmpppn);
        int tmpvpn = pageTable.getvpn(tmpppn);

        //swapToFile
        if (tmppid == processID) {
            VMKernel.tlbScheduler.clear(tmppid, tmpvpn);
        }
        TranslationEntry entry = pageTable.getTranslationEntry(tmpppn);
        
        if (entry != null) {
            swapFile.swapToFile(tmppid, tmpvpn, entry);
        }
        pageTable.removePage(tmppid, tmpvpn);

        //swapToMemory
        int ppn = tmpppn;
        entry = swapFile.swapToMemory(processID, vpn, ppn);
        boolean needToLoadSection = (entry == null);
        if (needToLoadSection) { 
            entry = new TranslationEntry(vpn, ppn, true, false, false, false);
            //loadPages
            entry.readOnly = loader.loadSection(vpn, ppn).readOnly;
        }
        pageTable.put(processID, entry);
        
        lru[ppn] = Machine.timer().getTime();

        pageLock.release();

        return true;
    }

    public InvertedPageTable pageTable;

    //inverted page table
    private class InvertedPageTable {

        public InvertedPageTable() {
            int length = Machine.processor().getNumPhysPages();
            coreMapPID = new int[length];
            coreMapVPN = new int[length];
            
            Arrays.fill(coreMapPID, -1);
            Arrays.fill(coreMapVPN, -1);
            
            coreMapEntry = new TranslationEntry[length];
            mapping = new HashMap<Pair, Integer>();
        }

        public TranslationEntry getTranslationEntry(Integer ppn) {
            if (ppn == null || ppn < 0 || ppn >= coreMapEntry.length) {
                return null;
            }
            return coreMapEntry[ppn];
        }

        public TranslationEntry getTranslationEntry(int processID, int vpn) {
            return getTranslationEntry(mapping.get(new Pair(processID, vpn)));
        }

        public void put(int processID, TranslationEntry entry) {
            coreMapPID[entry.ppn] = processID;
            coreMapVPN[entry.ppn] = entry.vpn;
            coreMapEntry[entry.ppn] = entry;
            mapping.put(new Pair(processID, entry.vpn), new Integer(entry.ppn));
        }

        public int getProcessID(Integer ppn) {
            if (ppn == null || ppn < 0 || ppn >= coreMapPID.length) {
                return -1;
            }
            return coreMapPID[ppn];
        }

        public int getvpn(Integer ppn) {
            if (ppn == null || ppn < 0 || ppn >= coreMapVPN.length) {
                return -1;
            }
            return coreMapVPN[ppn];
        }

        public void removeProcessPage(int processID) {
            for (int i = 0; i < coreMapPID.length; ++i) {
                if (coreMapPID[i] == processID) {
                    removePage(processID, coreMapVPN[i]);
                }
            }
        }

        public void removePage(int processID, int vpn) {
            Pair del = new Pair(processID, vpn);
            if (mapping.containsKey(del)) {
                int ppn = mapping.remove(del);
                coreMapPID[ppn] = -1;
                coreMapVPN[ppn] = -1;
                coreMapEntry[ppn] = null;
            }
        }

        public void removePage(int ppn) {
            removePage(coreMapPID[ppn], coreMapVPN[ppn]);
        }

        private int[] coreMapPID;
        private int[] coreMapVPN;
        private TranslationEntry[] coreMapEntry;

        private HashMap<Pair, Integer> mapping;
    }

    private Lock pageLock;

    public SwapFile swapFile;
}
