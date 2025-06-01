package memoryapp;

import java.util.Arrays;
public class MemoryLogic {

    private int[] registers = new int[8];
    
    private String addressBits = "000";  // 3-bit
    private String dataBits    = "0000"; // 4-bit
    
    private int currentAddress = 0;
    private int dataIn = 0;

    private boolean writeEnable = false;
    private boolean memoryEnabled = true;  // NEW: memory enable/disable functionality
    private boolean clockSignal = false;
    
    private String dataOutBits = "----";

    // NEW: Write is queued until the clock
    private boolean shouldWrite = false;  // user pressed "Write" button?
    
    // NEW: Status tracking
    private String lastOperation = "";
    private boolean lastOperationSuccess = false;
    private int clockPulseCount = 0;

    public MemoryLogic() {
        Arrays.fill(registers, 0);
    }

    // Parse bit strings into integers with validation
    public boolean parseBitInputs() {
        try {
            // Validate addressBits format (must be 3 bits of 0s and 1s)
            if (!addressBits.matches("[01]{3}")) {
                lastOperation = "Invalid address format. Must be 3 bits (0s and 1s).";
                lastOperationSuccess = false;
                return false;
            }
            
            // Validate dataBits format (must be 4 bits of 0s and 1s)
            if (!dataBits.matches("[01]{4}")) {
                lastOperation = "Invalid data format. Must be 4 bits (0s and 1s).";
                lastOperationSuccess = false;
                return false;
            }
            
            currentAddress = Integer.parseInt(addressBits, 2);
            dataIn = Integer.parseInt(dataBits, 2);
            return true;
        } catch (NumberFormatException e) {
            lastOperation = "Error parsing binary values: " + e.getMessage();
            lastOperationSuccess = false;
            return false;
        }
    }

    /**
     * Called when user presses "Write" button:
     * Instead of writing immediately, we set shouldWrite = true.
     * The actual write occurs on the clock edge in onClockPulse().
     * @return true if the write request was queued successfully
     */
    public boolean requestWrite() {
        if (!memoryEnabled) {
            lastOperation = "Write request failed: Memory is disabled";
            lastOperationSuccess = false;
            return false;
        }
        
        if (!writeEnable) {
            lastOperation = "Write request failed: Write Enable is off";
            lastOperationSuccess = false;
            return false;
        }
        
        if (!parseBitInputs()) {
            return false; // parseBitInputs already set the error message
        }
        
        this.shouldWrite = true;
        lastOperation = "Write request queued for address " + addressBits + " with data " + dataBits;
        lastOperationSuccess = true;
        return true;
    }

    /**
     * Called when user presses "Read" button:
     * Immediately reads from the current register -> dataOutBits
     * @return true if the read operation was successful
     */
    public boolean readFromMemory() {
        if (!memoryEnabled) {
            lastOperation = "Read failed: Memory is disabled";
            lastOperationSuccess = false;
            dataOutBits = "----";
            return false;
        }
        
        if (!parseBitInputs()) {
            dataOutBits = "----";
            return false; // parseBitInputs already set the error message
        }
        
        if (currentAddress >= 0 && currentAddress < registers.length) {
            int val = registers[currentAddress];
            dataOutBits = formatBinary(val, 4);
            lastOperation = "Read from address " + addressBits + ": " + dataOutBits;
            lastOperationSuccess = true;
            return true;
        } else {
            dataOutBits = "----";
            lastOperation = "Read failed: Address out of range";
            lastOperationSuccess = false;
            return false;
        }
    }

    /**
     * Called when user presses "Pulse Clock":
     * If shouldWrite && writeEnable => perform the actual write now.
     * Then reset shouldWrite = false.
     * @return true if a write operation was performed
     */
    public boolean onClockPulse() {
        clockPulseCount++;
        
        if (!memoryEnabled) {
            lastOperation = "Clock pulse: No effect (Memory disabled)";
            lastOperationSuccess = false;
            return false;
        }
        
        if (shouldWrite && writeEnable) {
            boolean result = writeToMemory();
            shouldWrite = false;
            return result;
        } else {
            if (shouldWrite && !writeEnable) {
                lastOperation = "Clock pulse: No write (Write Enable is off)";
            } else {
                lastOperation = "Clock pulse: No pending write operations";
            }
            lastOperationSuccess = true;
            return false;
        }
    }

    /**
     * The actual memory update logic, used internally by onClockPulse.
     * @return true if the write operation was successful
     */
    private boolean writeToMemory() {
        if (!parseBitInputs()) {
            return false; // parseBitInputs already set the error message
        }
        
        if (currentAddress >= 0 && currentAddress < registers.length) {
            registers[currentAddress] = dataIn;
            lastOperation = "Write to address " + addressBits + ": " + dataBits;
            lastOperationSuccess = true;
            return true;
        } else {
            lastOperation = "Write failed: Address out of range";
            lastOperationSuccess = false;
            return false;
        }
    }

    /**
     * Resets all registers to 0 and clears pending operations
     */
    public void resetMemory() {
        Arrays.fill(registers, 0);
        shouldWrite = false;
        dataOutBits = "----";
        lastOperation = "Memory reset: All registers cleared";
        lastOperationSuccess = true;
    }
    
    /**
     * Format an integer as a binary string with specified length
     */
    private String formatBinary(int value, int length) {
        String bin = Integer.toBinaryString(value);
        return String.format("%" + length + "s", bin).replace(' ', '0');
    }

    // Accessors
    public int[] getRegisters() {
        return registers;
    }

    public String getRegisterBits(int index) {
        if (index < 0 || index >= registers.length) return "----";
        int val = registers[index];
        return formatBinary(val, 4);
    }

    public int getCurrentAddress() {
        return currentAddress;
    }

    public boolean isWriteEnable() {
        return writeEnable;
    }
    
    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public String getDataOutBits() {
        return dataOutBits;
    }
    
    public String getLastOperation() {
        return lastOperation;
    }
    
    public boolean isLastOperationSuccess() {
        return lastOperationSuccess;
    }
    
    public int getClockPulseCount() {
        return clockPulseCount;
    }
    
    public boolean hasPendingWrite() {
        return shouldWrite;
    }

    // Mutators
    public void setAddressBits(String addressBits) {
        this.addressBits = addressBits;
        parseBitInputs();
    }
    
    public void setDataBits(String dataBits) {
        this.dataBits = dataBits;
        parseBitInputs();
    }
    
    public void setWriteEnable(boolean we) {
        this.writeEnable = we;
    }
    
    public void setMemoryEnabled(boolean enabled) {
        this.memoryEnabled = enabled;
    }
    
    public void setClockSignal(boolean clk) {
        this.clockSignal = clk;
    }
    
    public void resetDataOut() {
        this.dataOutBits = "----";
    }
}