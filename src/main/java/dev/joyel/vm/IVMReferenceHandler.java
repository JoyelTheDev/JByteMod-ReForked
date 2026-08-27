package dev.joyel.vm;

import org.objectweb.asm.tree.ClassNode;

public interface IVMReferenceHandler {
    ClassNode tryClassLoad(String internalName);
}
