import java.util.*;                
import java.io.*;
import java.nio.ByteBuffer;

public class cachesim{
    public static CacheSet[] cache; // array of cache sets (2D array)
    public static byte[] mainMemory = new byte[16*1024*1024];
    public static int currentTime = 0;
    public static int blockSize; // in bytes
    public static int cacheSize; // in KB
    public static int associativity;
    public static int numSets;

    public class CacheBlock {
        boolean valid;
        boolean dirty;
        long tag;
        byte[] data;
        int lastUsedTime;
        int frameStart;
        public CacheBlock (int blockSize) {
            this.valid = false;
            this.dirty = false;
            this.tag = -1;
            this.frameStart = 0;
            this.data = new byte[blockSize];
            this.lastUsedTime = 0;
        }
    }

    public class CacheSet {
        CacheBlock[] blocks; // one set of blocks

        public CacheSet(int associativity, int blockSize) {
            blocks = new CacheBlock[associativity]; // array is of associativity length
            for (int i = 0; i < associativity; i++) { 
                blocks[i] = new CacheBlock(blockSize);
            }
        }

        public CacheBlock findLRU() {
        CacheBlock lru = blocks[0];
            for (CacheBlock block : blocks) {
                if (block.lastUsedTime < lru.lastUsedTime) {
                    lru = block;
                }
            }
            return lru;
        }
    }

    public void initialize(int cSize, int assoc, int bSize) {
        cacheSize = cSize;
        blockSize = bSize;
        associativity = assoc;
        numSets = (cacheSize * 1024) / (blockSize * associativity);

        cache = new CacheSet[numSets];
        for (int i = 0; i < numSets; i++) {
            cache[i] = new CacheSet(associativity, blockSize);

        }
    }

    public static void read (String file) throws FileNotFoundException{
        Scanner sc = new Scanner(new File(file));
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            String[] strings = line.split(" ");
            String type = strings[0];
            int address = Integer.parseInt(strings[1].substring(2), 16);
            int size = Integer.parseInt(strings[2]);
            //int value = null;
            if (strings.length > 3) {
                //System.out.println(strings[3]);
                //value = Long.parseInt(strings[3], 16);
            }
            if (type.equals("store")) {
                store(address, size, strings[3]);
            }
            else if (type.equals("load")) {
                load(address, size);
            }
        }
        sc.close();
    }

    public static void load(int address, int size) {
        AccessResult result = performAccess(address, "load", null);
        if (result.hit) {
            System.out.printf("load 0x%x hit ", address);
        }
        else {
            System.out.printf("load 0x%x miss ", address);
        }

        int startOfBlock = address/blockSize * blockSize; 
        int offset = address - startOfBlock;
        //System.out.printf("size: %d\n", size);
        for(int i = 0;i < size; i++){
            byte b = result.loadedBlock.data[i+offset];
            //System.out.printf("i + offset bit index: %d string: %02x\n", i/2+offset, b & 0xFF);

            System.out.printf("%02x", b);
        }
        System.out.printf("\n");
    }

    public static void store(int address, int size, String value) {
        AccessResult result = performAccess(address, "store", value);
        if (result.hit) {
            System.out.printf("store 0x%x hit\n", address);
        }
        else {
            System.out.printf("store 0x%x miss\n", address);
        }
    }

    public static class AccessResult {
        boolean hit;
        CacheBlock loadedBlock;

        public AccessResult(boolean hit, CacheBlock value) {
            this.hit = hit;
            this.loadedBlock = value;
        }
    }

    public static AccessResult performAccess(int address, String type, String value) {
        int offsetBits = log2(blockSize);
        int indexBits = log2(numSets);
        int index = (address >> offsetBits) & ((1 << indexBits) - 1);
        long tag = address >> (offsetBits + indexBits);
        int startOfBlock = address/blockSize * blockSize; 
        int offset = address - startOfBlock;
        CacheSet set = cache[index];
        CacheBlock targetBlock = null;
        boolean hit = false;
        int loadedValue = 0;

        for (CacheBlock block : set.blocks) {
            if (block.valid && block.tag == tag) {
                targetBlock = block;
                hit = true;
                if (type.equals("load")) {
                    //loadedValue = ByteBuffer.wrap(block.data).getInt();
                }
                else if (type.equals("store")) {
                    block.dirty = true;
                    for(int i = 0; i < value.length(); i+=2){
                        //System.out.printf("i + offset bit index: %d string: %s\n", i/2 + offset, value.substring(i, i+2));
                        block.data[i/2 + offset] = (byte)Integer.parseInt(value.substring(i,i+2), 16);
                    }
                    /** 
                    if (value != null) {
                        ByteBuffer.wrap(block.data).putInt(value);
                    }*/

                }
                break;
            }
        }
        if (!hit) {
            targetBlock = set.findLRU();
            String clean = "dirty";
            if(!targetBlock.dirty)clean = "clean";
            if (targetBlock.valid) {
                //System.arraycopy(targetBlock.data, 0, mainMemory, targetBlock.frameStart, blockSize);
                for(int i = 0;i<blockSize; i++){
                    mainMemory[targetBlock.frameStart+i] = targetBlock.data[i];
                }
                System.out.printf("replacement 0x%x %s\n", targetBlock.frameStart, clean);
            }
            //System.arraycopy(mainMemory, address & ~((1 << offsetBits) - 1), targetBlock.data, 0, blockSize);
            for(int i = 0; i < blockSize; i++){
                targetBlock.data[i] = mainMemory[startOfBlock+i];
            }
            targetBlock.tag = tag;
            if(type.equals("store")){
                for(int i = 0; i < value.length(); i+=2){
                    //System.out.printf("i + offset bit index: %d string: %s\n", i/2+offset, value.substring(i, i+2));
                    targetBlock.data[i/2 + offset] = (byte)Integer.parseInt(value.substring(i,i+2), 16);
                }
            }
            targetBlock.frameStart = startOfBlock;
            targetBlock.valid = true;
            targetBlock.dirty = false;
            if(type.equals("store"))targetBlock.dirty = true;
            targetBlock.lastUsedTime = ++currentTime;
            //loadedValue = ByteBuffer.wrap(targetBlock.data).getInt();
        }
        else {
            targetBlock.lastUsedTime = ++currentTime;
        }
        return new AccessResult (hit, targetBlock);
    }

    public static void main(String[] args) throws FileNotFoundException{
        if (args.length != 4) {
            System.out.println("Error");
            return;
        }
        String file = args[0];
        int cSize = Integer.parseInt(args[1]);
        int assoc = Integer.parseInt(args[2]);
        int bSize = Integer.parseInt(args[3]);

        cachesim cacheSim1 = new cachesim();
        cacheSim1.initialize(cSize, assoc, bSize);
        cacheSim1.read(file);
    }

    public static int log2(int n) {
        int r=0;
        while (n != 1) {
            n >>= 1;
            r++;
        }
        return r;
    }
}