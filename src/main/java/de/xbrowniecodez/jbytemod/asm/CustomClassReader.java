package de.xbrowniecodez.jbytemod.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class CustomClassReader extends ClassReader {

    private final byte[] rawBytes;

    public CustomClassReader(byte[] classFile) {
        super(classFile);
        this.rawBytes = classFile;
    }

    @Override
    public short readShort(int offset) {
        if (offset + 1 >= rawBytes.length) return Opcodes.V1_7;
        return (short) ((rawBytes[offset] & 255) << 8 | rawBytes[offset + 1] & 255);
    }

    @Override
    public String readUTF8(int offset, char[] charBuffer) {
        try {
            return super.readUTF8(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String readModule(int offset, char[] charBuffer) {
        try {
            return super.readModule(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int readUnsignedShort(int offset) {
        try {
            return super.readUnsignedShort(offset);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String readClass(int offset, char[] charBuffer) {
        try {
            return super.readClass(offset, charBuffer);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Object readConst(int constantPoolEntryIndex, char[] charBuffer) {
        try {
            return super.readConst(constantPoolEntryIndex, charBuffer);
        } catch (Exception e) {
            return null;
        }
    }
}
