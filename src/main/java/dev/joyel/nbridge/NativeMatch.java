package dev.joyel.nbridge;

public record NativeMatch(NativeLibrary library, NativeExport export, boolean longForm, boolean hinted,
                          boolean ambiguous) {
}
