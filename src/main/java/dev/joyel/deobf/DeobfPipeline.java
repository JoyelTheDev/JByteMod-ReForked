package dev.joyel.deobf;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassResult;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class DeobfPipeline {

    private final List<DeobfPass> passes;
    private final boolean verbose;
    private final Consumer<String> logger;

    public DeobfPipeline(List<DeobfPass> passes, boolean verbose, Consumer<String> logger) {
        this.passes  = new ArrayList<>(passes);
        this.verbose = verbose;
        this.logger  = logger;
    }

    public List<PassResult> run(Map<String, ClassNode> classes) {
        List<PassResult> results = new ArrayList<>();
        logger.accept("Starting deobfuscation pipeline: " + passes.size() + " pass(es).");
        for (DeobfPass pass : passes) {
            logger.accept("[" + pass.category.displayName + "] Running: " + pass.name);
            long start = System.currentTimeMillis();
            try {
                boolean ok = pass.execute(classes, verbose);
                long ms = System.currentTimeMillis() - start;
                results.add(ok ? PassResult.ok(pass, ms) : PassResult.fail(pass, ms, "Pass reported no effect"));
                logger.accept((ok ? "\u2713" : "\u26A0") + " " + pass.name + " completed in " + ms + "ms" + (ok ? "" : " (no changes)"));
            } catch (Exception e) {
                long ms = System.currentTimeMillis() - start;
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                results.add(PassResult.fail(pass, ms, msg));
                logger.accept("\u2717 " + pass.name + " FAILED: " + msg);
                if (verbose) e.printStackTrace();
            }
        }
        long totalMs = results.stream().mapToLong(r -> r.durationMs).sum();
        long ok = results.stream().filter(r -> r.success).count();
        logger.accept("Pipeline complete. " + ok + "/" + passes.size() + " passes succeeded in " + totalMs + "ms.");
        return results;
    }
}
