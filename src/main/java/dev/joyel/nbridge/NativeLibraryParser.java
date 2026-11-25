package dev.joyel.nbridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NativeLibraryParser {
    private static final int MAX_EXPORTS = 500_000;

    private NativeLibraryParser() {
    }

    public static boolean looksNative(byte[] data) {
        if (data == null || data.length < 64) {
            return false;
        }
        if (data[0] == 'M' && data[1] == 'Z') {
            return true;
        }
        if (data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') {
            return true;
        }
        int magic = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (magic == 0xfeedface || magic == 0xcefaedfe || magic == 0xfeedfacf || magic == 0xcffaedfe) {
            return true;
        }
        return isFat(data);
    }

    private static boolean isFat(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt(0);
        if (magic != 0xcafebabe && magic != 0xcafebabf) {
            return false;
        }
        int count = buffer.getInt(4);
        return count > 0 && count <= 20;
    }

    public static NativeLibrary parse(String path, byte[] data) {
        try {
            if (data == null || data.length < 64) {
                return null;
            }
            if (data[0] == 'M' && data[1] == 'Z') {
                return parsePe(path, data);
            }
            if (data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') {
                return parseElf(path, data);
            }
            int magic = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (magic == 0xfeedface || magic == 0xcefaedfe || magic == 0xfeedfacf || magic == 0xcffaedfe) {
                return parseMachO(path, data, 0, data.length, data.length);
            }
            if (isFat(data)) {
                return parseFat(path, data);
            }
            return null;
        } catch (RuntimeException failure) {
            return failed(path, data, guessFormat(data), failure);
        }
    }

    private static String guessFormat(byte[] data) {
        if (data[0] == 'M' && data[1] == 'Z') {
            return "PE";
        }
        if (data[0] == 0x7f) {
            return "ELF";
        }
        return "Mach-O";
    }

    private static NativeLibrary failed(String path, byte[] data, String format, Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new NativeLibrary(path, format, "unknown", 0, data.length, List.of(), false, false,
                "Could not read the export table: " + message);
    }

    private static NativeLibrary build(String path, String format, String architecture, int bits, long size,
                                       List<NativeExport> exports) {
        NativeLibrary probe = new NativeLibrary(path, format, architecture, bits, size, exports, false, false, null);
        boolean onLoad = false;
        boolean onUnload = false;
        for (NativeExport export : exports) {
            String name = JniNames.normalizeExport(export.name(), probe);
            if (name.equals("JNI_OnLoad") || name.startsWith("JNI_OnLoad_")) {
                onLoad = true;
            }
            if (name.equals("JNI_OnUnload") || name.startsWith("JNI_OnUnload_")) {
                onUnload = true;
            }
        }
        return new NativeLibrary(path, format, architecture, bits, size, List.copyOf(exports), onLoad, onUnload, null);
    }

    private static NativeLibrary parsePe(String path, byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int peOffset = buffer.getInt(0x3c);
        if (peOffset < 0 || peOffset + 24 > data.length || buffer.getInt(peOffset) != 0x00004550) {
            throw new IllegalArgumentException("not a PE image");
        }
        int machine = buffer.getShort(peOffset + 4) & 0xffff;
        int sectionCount = buffer.getShort(peOffset + 6) & 0xffff;
        int optionalSize = buffer.getShort(peOffset + 20) & 0xffff;
        int optional = peOffset + 24;
        int magic = buffer.getShort(optional) & 0xffff;
        boolean pe64 = magic == 0x20b;
        if (!pe64 && magic != 0x10b) {
            throw new IllegalArgumentException("unknown optional header");
        }
        int directory = optional + (pe64 ? 112 : 96);
        int exportRva = buffer.getInt(directory);
        int exportSize = buffer.getInt(directory + 4);
        String architecture = switch (machine) {
            case 0x14c -> "x86";
            case 0x8664 -> "x86_64";
            case 0x1c0, 0x1c4 -> "arm";
            case 0xaa64 -> "arm64";
            default -> "machine 0x" + Integer.toHexString(machine);
        };
        int bits = pe64 ? 64 : 32;
        if (exportRva == 0 || exportSize == 0) {
            return build(path, "PE", architecture, bits, data.length, List.of());
        }
        int sections = optional + optionalSize;
        int[][] table = new int[sectionCount][4];
        for (int index = 0; index < sectionCount; index++) {
            int base = sections + index * 40;
            if (base + 40 > data.length) {
                throw new IllegalArgumentException("truncated section table");
            }
            table[index][0] = buffer.getInt(base + 12);
            table[index][1] = buffer.getInt(base + 8);
            table[index][2] = buffer.getInt(base + 20);
            table[index][3] = buffer.getInt(base + 16);
        }
        int dir = rvaToOffset(table, exportRva, data.length);
        if (dir < 0 || dir + 40 > data.length) {
            throw new IllegalArgumentException("export directory is outside the file");
        }
        int functionCount = buffer.getInt(dir + 20);
        int nameCount = buffer.getInt(dir + 24);
        int functions = rvaToOffset(table, buffer.getInt(dir + 28), data.length);
        int names = rvaToOffset(table, buffer.getInt(dir + 32), data.length);
        int ordinals = rvaToOffset(table, buffer.getInt(dir + 36), data.length);
        if (nameCount < 0 || nameCount > MAX_EXPORTS || functionCount < 0 || functionCount > MAX_EXPORTS) {
            throw new IllegalArgumentException("implausible export count");
        }
        List<NativeExport> exports = new ArrayList<>();
        for (int index = 0; index < nameCount; index++) {
            int nameOffset = rvaToOffset(table, buffer.getInt(names + index * 4), data.length);
            int ordinal = buffer.getShort(ordinals + index * 2) & 0xffff;
            if (nameOffset < 0 || ordinal >= functionCount) {
                continue;
            }
            long address = buffer.getInt(functions + ordinal * 4) & 0xffffffffL;
            exports.add(new NativeExport(cString(data, nameOffset), address));
        }
        return build(path, "PE", architecture, bits, data.length, exports);
    }

    private static int rvaToOffset(int[][] sections, int rva, int fileSize) {
        for (int[] section : sections) {
            int start = section[0];
            int span = Math.max(section[1], section[2]);
            if (rva >= start && rva < start + span) {
                long offset = (long) rva - start + (section[3] & 0xffffffffL);
                return offset < fileSize ? (int) offset : -1;
            }
        }
        return rva < fileSize ? rva : -1;
    }

    private static NativeLibrary parseElf(String path, byte[] data) {
        boolean elf64 = data[4] == 2;
        ByteOrder order = data[5] == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(order);
        int machine = buffer.getShort(18) & 0xffff;
        long sectionOffset = elf64 ? buffer.getLong(40) : buffer.getInt(32) & 0xffffffffL;
        int sectionSize = buffer.getShort(elf64 ? 58 : 46) & 0xffff;
        int sectionCount = buffer.getShort(elf64 ? 60 : 48) & 0xffff;
        long programOffset = elf64 ? buffer.getLong(32) : buffer.getInt(28) & 0xffffffffL;
        int programSize = buffer.getShort(elf64 ? 54 : 42) & 0xffff;
        int programCount = buffer.getShort(elf64 ? 56 : 44) & 0xffff;
        String architecture = switch (machine) {
            case 3 -> "x86";
            case 62 -> "x86_64";
            case 40 -> "arm";
            case 183 -> "arm64";
            case 8 -> "mips";
            case 243 -> "riscv";
            case 20 -> "ppc";
            case 21 -> "ppc64";
            default -> "machine " + machine;
        };
        int bits = elf64 ? 64 : 32;
        int symbolSize = elf64 ? 24 : 16;
        List<NativeExport> exports = new ArrayList<>();

        boolean found = false;
        if (sectionOffset > 0 && sectionCount > 0 && sectionSize >= (elf64 ? 64 : 40)
                && sectionOffset + (long) sectionCount * sectionSize <= data.length) {
            for (int index = 0; index < sectionCount; index++) {
                int base = (int) (sectionOffset + (long) index * sectionSize);
                int type = buffer.getInt(base + 4);
                if (type != 11) {
                    continue;
                }
                long offset = elf64 ? buffer.getLong(base + 24) : buffer.getInt(base + 16) & 0xffffffffL;
                long size = elf64 ? buffer.getLong(base + 32) : buffer.getInt(base + 20) & 0xffffffffL;
                int link = buffer.getInt(base + (elf64 ? 40 : 24));
                if (link >= sectionCount) {
                    continue;
                }
                int linkBase = (int) (sectionOffset + (long) link * sectionSize);
                long stringOffset = elf64 ? buffer.getLong(linkBase + 24) : buffer.getInt(linkBase + 16) & 0xffffffffL;
                long stringSize = elf64 ? buffer.getLong(linkBase + 32) : buffer.getInt(linkBase + 20) & 0xffffffffL;
                readElfSymbols(data, buffer, elf64, offset, size / symbolSize, stringOffset, stringSize, exports);
                found = true;
            }
        }
        if (!found && programCount > 0) {
            readElfDynamic(data, buffer, elf64, programOffset, programSize, programCount, symbolSize, exports);
        }
        return build(path, "ELF", architecture, bits, data.length, exports);
    }

    private static void readElfDynamic(byte[] data, ByteBuffer buffer, boolean elf64, long programOffset,
                                       int programSize, int programCount, int symbolSize, List<NativeExport> exports) {
        long dynamicOffset = -1;
        long dynamicSize = 0;
        List<long[]> loads = new ArrayList<>();
        for (int index = 0; index < programCount; index++) {
            int base = (int) (programOffset + (long) index * programSize);
            if (base < 0 || base + programSize > data.length) {
                break;
            }
            int type = buffer.getInt(base);
            long offset = elf64 ? buffer.getLong(base + 8) : buffer.getInt(base + 4) & 0xffffffffL;
            long address = elf64 ? buffer.getLong(base + 16) : buffer.getInt(base + 8) & 0xffffffffL;
            long fileSize = elf64 ? buffer.getLong(base + 32) : buffer.getInt(base + 16) & 0xffffffffL;
            if (type == 1) {
                loads.add(new long[]{address, offset, fileSize});
            } else if (type == 2) {
                dynamicOffset = offset;
                dynamicSize = fileSize;
            }
        }
        if (dynamicOffset < 0) {
            return;
        }
        long symtab = -1;
        long strtab = -1;
        long strsz = 0;
        long hashChains = -1;
        int entry = elf64 ? 16 : 8;
        for (long position = dynamicOffset; position + entry <= dynamicOffset + dynamicSize && position + entry <= data.length;
             position += entry) {
            long tag = elf64 ? buffer.getLong((int) position) : buffer.getInt((int) position);
            long value = elf64 ? buffer.getLong((int) position + 8) : buffer.getInt((int) position + 4) & 0xffffffffL;
            if (tag == 0) {
                break;
            }
            if (tag == 6) {
                symtab = value;
            } else if (tag == 5) {
                strtab = value;
            } else if (tag == 10) {
                strsz = value;
            } else if (tag == 4) {
                long hashOffset = vaddrToOffset(loads, value, data.length);
                if (hashOffset >= 0 && hashOffset + 8 <= data.length) {
                    hashChains = buffer.getInt((int) hashOffset + 4) & 0xffffffffL;
                }
            }
        }
        if (symtab < 0 || strtab < 0) {
            return;
        }
        long symbolFile = vaddrToOffset(loads, symtab, data.length);
        long stringFile = vaddrToOffset(loads, strtab, data.length);
        if (symbolFile < 0 || stringFile < 0) {
            return;
        }
        long count = hashChains >= 0 ? hashChains : (strtab > symtab ? (strtab - symtab) / symbolSize : 0);
        if (strsz == 0) {
            strsz = data.length - stringFile;
        }
        readElfSymbols(data, buffer, elf64, symbolFile, count, stringFile, strsz, exports);
    }

    private static long vaddrToOffset(List<long[]> loads, long address, int fileSize) {
        for (long[] load : loads) {
            if (address >= load[0] && address < load[0] + Math.max(load[2], 1)) {
                long offset = address - load[0] + load[1];
                return offset < fileSize ? offset : -1;
            }
        }
        return address < fileSize ? address : -1;
    }

    private static void readElfSymbols(byte[] data, ByteBuffer buffer, boolean elf64, long offset, long count,
                                       long stringOffset, long stringSize, List<NativeExport> exports) {
        int symbolSize = elf64 ? 24 : 16;
        if (count > MAX_EXPORTS) {
            throw new IllegalArgumentException("implausible symbol count");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (long index = 1; index < count; index++) {
            long base = offset + index * symbolSize;
            if (base + symbolSize > data.length) {
                break;
            }
            int nameIndex = buffer.getInt((int) base);
            int info;
            int other;
            int section;
            long value;
            if (elf64) {
                info = data[(int) base + 4] & 0xff;
                other = data[(int) base + 5] & 0xff;
                section = buffer.getShort((int) base + 6) & 0xffff;
                value = buffer.getLong((int) base + 8);
            } else {
                value = buffer.getInt((int) base + 4) & 0xffffffffL;
                info = data[(int) base + 12] & 0xff;
                other = data[(int) base + 13] & 0xff;
                section = buffer.getShort((int) base + 14) & 0xffff;
            }
            int binding = info >> 4;
            int type = info & 0xf;
            if (section == 0 || (binding != 1 && binding != 2) || (other & 3) == 2 || (other & 3) == 1) {
                continue;
            }
            if (type != 2 && type != 0 && type != 10) {
                continue;
            }
            long nameStart = stringOffset + (nameIndex & 0xffffffffL);
            if (nameIndex == 0 || nameStart >= data.length || nameStart >= stringOffset + stringSize) {
                continue;
            }
            String name = cString(data, (int) nameStart);
            if (!name.isEmpty() && seen.add(name)) {
                exports.add(new NativeExport(name, value));
            }
        }
    }

    private static NativeLibrary parseFat(String path, byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        boolean fat64 = buffer.getInt(0) == 0xcafebabf;
        int count = buffer.getInt(4);
        List<String> architectures = new ArrayList<>();
        Map<String, NativeExport> merged = new LinkedHashMap<>();
        int bits = 0;
        for (int index = 0; index < count; index++) {
            int base = 8 + index * (fat64 ? 32 : 20);
            if (base + (fat64 ? 32 : 20) > data.length) {
                break;
            }
            long offset = fat64 ? buffer.getLong(base + 8) : buffer.getInt(base + 8) & 0xffffffffL;
            long size = fat64 ? buffer.getLong(base + 16) : buffer.getInt(base + 12) & 0xffffffffL;
            if (offset < 0 || offset + size > data.length) {
                continue;
            }
            NativeLibrary slice = parseMachO(path, data, (int) offset, (int) size, data.length);
            if (slice.error() != null) {
                continue;
            }
            architectures.add(slice.architecture());
            bits = Math.max(bits, slice.bits());
            for (NativeExport export : slice.exports()) {
                merged.putIfAbsent(export.name(), export);
            }
        }
        if (architectures.isEmpty()) {
            throw new IllegalArgumentException("no readable slices in the universal binary");
        }
        return build(path, "Mach-O", String.join("+", architectures), bits, data.length, new ArrayList<>(merged.values()));
    }

    private static NativeLibrary parseMachO(String path, byte[] data, int start, int length, int fileSize) {
        int rawMagic = ByteBuffer.wrap(data, start, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        boolean swapped = rawMagic == 0xcefaedfe || rawMagic == 0xcffaedfe;
        boolean macho64 = rawMagic == 0xfeedfacf || rawMagic == 0xcffaedfe;
        ByteOrder order = swapped ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        if (rawMagic == 0xfeedface || rawMagic == 0xfeedfacf) {
            order = ByteOrder.BIG_ENDIAN;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(order);
        int cpu = buffer.getInt(start + 4);
        int commandCount = buffer.getInt(start + 16);
        int position = start + (macho64 ? 32 : 28);
        String architecture = switch (cpu) {
            case 7 -> "x86";
            case 0x01000007 -> "x86_64";
            case 12 -> "arm";
            case 0x0100000c -> "arm64";
            case 18 -> "ppc";
            case 0x01000012 -> "ppc64";
            default -> "cpu 0x" + Integer.toHexString(cpu);
        };
        List<NativeExport> exports = new ArrayList<>();
        for (int index = 0; index < commandCount && position + 8 <= start + length; index++) {
            int command = buffer.getInt(position);
            int size = buffer.getInt(position + 4);
            if (size < 8) {
                break;
            }
            if (command == 2) {
                int symbolOffset = start + buffer.getInt(position + 8);
                int symbolCount = buffer.getInt(position + 12);
                int stringOffset = start + buffer.getInt(position + 16);
                int stringSize = buffer.getInt(position + 20);
                int entry = macho64 ? 16 : 12;
                if (symbolCount < 0 || symbolCount > MAX_EXPORTS) {
                    throw new IllegalArgumentException("implausible symbol count");
                }
                for (int symbol = 0; symbol < symbolCount; symbol++) {
                    int base = symbolOffset + symbol * entry;
                    if (base + entry > fileSize) {
                        break;
                    }
                    int nameIndex = buffer.getInt(base);
                    int type = data[base + 4] & 0xff;
                    if ((type & 0x01) == 0 || (type & 0x0e) != 0x0e) {
                        continue;
                    }
                    long value = macho64 ? buffer.getLong(base + 8) : buffer.getInt(base + 8) & 0xffffffffL;
                    int nameStart = stringOffset + nameIndex;
                    if (nameIndex < 0 || nameIndex >= stringSize || nameStart >= fileSize) {
                        continue;
                    }
                    String name = cString(data, nameStart);
                    if (name.startsWith("_")) {
                        name = name.substring(1);
                    }
                    if (!name.isEmpty()) {
                        exports.add(new NativeExport(name, value));
                    }
                }
            }
            position += size;
        }
        return build(path, "Mach-O", architecture, macho64 ? 64 : 32, length, exports);
    }

    private static String cString(byte[] data, int offset) {
        int end = offset;
        while (end < data.length && data[end] != 0 && end - offset < 4096) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.ISO_8859_1);
    }
}
