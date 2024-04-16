#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int debug = 1;

char memory[1 << 24]; 

int log2(int n) {
    int r = 0;
    while (n >>= 1) r++;
    return r;
}

struct block {
    int valid;
    int dirty;
    int tag;
    char data[1024];
};

int main(int argc, char* argv[]) {
    // ./cachesim <trace-file> <cache-size-kB> <associativity> <block-size>
    // ./cachesim traces/example.txt 1024 4 32
    FILE* file = fopen(argv[1], "r");
    int cache_size = atoi(argv[2]) * 1024; // 1 KB = 1024 bytes in CS 250
    int num_ways = atoi(argv[3]);
    int block_size = atoi(argv[4]);

    // Calculations for cache
    int num_frames = cache_size / block_size;
    int num_sets = num_frames / num_ways;
    int offset_bits = log2(block_size);
    int index_bits = log2(num_sets);
    int tag_bits = 24 - offset_bits - index_bits;

    int index_mask = (1 << index_bits) - 1;
    int offset_mask = (1 << offset_bits) - 1;

    // Cache = 2D array
    struct block** cache = (struct block**)malloc(num_sets * sizeof(struct block*));
    for (int i = 0; i < num_sets; i++) {
        cache[i] = (struct block*)malloc(num_ways * sizeof(struct block));
    }
    for (int i = 0; i < num_sets; i++) { // Initialize 
        for (int j = 0; j < num_ways; j++) {
            cache[i][j].valid = 0;
            cache[i][j].dirty = 0;
            cache[i][j].tag = 0;
            memset(cache[i][j].data, 0, sizeof(cache[i][j].data)); // IS THIS OKAY
        }
    }

    // Initialize
    char type[8]; // 5 (max) + 1 (null) = 6
    int address; // 24-bit address, in hex 
    int access_size; // in bytes, no larger than 8 at a time
    char data[block_size]; // since we aren't writing across blocks

    while (fscanf(file, "%s 0x%x %d", type, &address, &access_size) != EOF) {
        // if (debug) printf("%s 0x%x %d", type, address, access_size);

        int tag = address >> (offset_bits + index_bits);
        int index = (address >> offset_bits) & index_mask;
        int block_offset = address & offset_mask;
        
        int hit = 0;
        int hit_idx = 0;

        // LOAD
        if (strcmp(type, "load") == 0) {
            for (int j = 0; j < num_ways; j++) {
                if (cache[index][j].tag == tag && cache[index][j].valid) {
                    hit = 1;
                    hit_idx = j; // Locate cache block to use
                    break;
                }
            }

            // HIT
            if (hit == 1) {
                printf("%s 0x%x hit ", type, address);
                for (int i = 0; i < access_size; i++) { // Value loaded from cache/memory
                    printf("%02hhx", cache[index][hit_idx].data[block_offset + i]); // Starting position in block, iterate over all bytes
                }
                printf("\n");
            }
            
            // MISS
            else { // UPDATE 0 INDEX AFTER IMPLMENTING LRU
                for (int i = 0; i < block_size; i++) { // Write previous data back to lower memory (entire block of data)
                    cache[index][0].data[i] = memory[address + i];
                }
                cache[index][0].tag = tag; // Which memory block cache block corresponds to
                cache[index][0].valid = 1; // Mark as not dirty
                
                printf("%s 0x%x miss ", type, address); // Return data
                for (int i = 0; i < access_size; i++) {
                    printf("%02hhx", cache[index][0].data[block_offset + i]);
                }
                printf("\n");
            } // Note: Beginning of address til end of access size but bring entire block in
        }

        // STORE
        if (strcmp(type, "store") == 0) {
            for (int i = 0; i < access_size; i++) { // Read data value to be written, store in data array
                fscanf(file, "%2hhx", &data[i]);
            }
            
            for (int j = 0; j < num_ways; j++) {
                if (cache[index][j].tag == tag && cache[index][j].valid) {
                    hit = 1;
                    hit_idx = j; // Locate cache block to use
                    break;
                }
            }

            // HIT
            if (hit == 1) {
                printf("%s 0x%x hit\n", type, address);

                for (int i = 0; i < access_size; i++) { // Write new data into cache block
                    cache[index][hit_idx].data[block_offset + i] = data[i];
                }
                
            }

            // MISS
            else {
                for (int i = 0; i < access_size; i++) { // Write previous data back to lower memory (accessed data)
                    memory[address + i] = data[i];
                }

                for (int i = 0; i < access_size; i++) { // Write new data into cache block
                    cache[index][0].data[block_offset + i] = data[i];
                }
                printf("%s 0x%x miss\n", type, address);
            }

            cache[index][hit_idx].dirty = 1; // Mark as dirty
    }
    for (int i = 0; i < num_sets; i++) {
        free(cache[i]);
    }
    free(cache);

    fclose(file);
    return EXIT_SUCCESS;
    }
}