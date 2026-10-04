package org.biggraph.cobol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jp.osscons.opensourcecobol.libcobj.call.CobolResolve;
import jp.osscons.opensourcecobol.libcobj.call.CobolRunnable;
import jp.osscons.opensourcecobol.libcobj.common.CobolUtil;
import jp.osscons.opensourcecobol.libcobj.data.CobolDecimal;
import jp.osscons.opensourcecobol.libcobj.exceptions.CobolStopRunException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Java-only command-line entry point for every converted batch program. */
public final class ProgramRunner {
    private ProgramRunner() {}
    public static void main(String[] args) throws Exception {
        JSONArray inventory;
        try (var resource = ProgramRunner.class.getResourceAsStream("/conversion.json")) {
            if (resource == null) throw new IllegalStateException("Missing conversion.json; run java/build.sh");
            inventory = new JSONArray(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (args.length == 0 || args[0].equals("--list")) {
            for (int i = 0; i < inventory.length(); i++) {
                JSONObject entry = inventory.getJSONObject(i);
                System.out.println(entry.getString("source") + "\t" + entry.getString("status"));
            }
            return;
        }
        JSONObject selected = null;
        List<String> packages = new ArrayList<>();
        Path sourceParent = Path.of(args[0]).getParent();
        if (sourceParent == null) throw new IllegalArgumentException("Use the full source path from --list");
        String parent = sourceParent.toString();
        for (int i = 0; i < inventory.length(); i++) {
            JSONObject entry = inventory.getJSONObject(i);
            if (entry.getString("source").equals(args[0])) selected = entry;
            if (Path.of(entry.getString("source")).getParent().toString().equals(parent)
                && entry.getString("status").equals("generated")) {
                packages.add(entry.getString("package"));
            }
        }
        if (selected == null) throw new IllegalArgumentException("Unknown source path: " + args[0]);
        if (!selected.getString("status").equals("generated")) {
            throw new IllegalArgumentException("Source is " + selected.getString("status") + "; see java/conversion.json");
        }
        if (selected.optString("program_type").equals("subprogram")) {
            throw new IllegalArgumentException("Run this subprogram through its caller or Java execute API");
        }
        List<String> bindings = new ArrayList<>();
        List<String> programArgs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--dd")) {
                if (++i >= args.length) throw new IllegalArgumentException("--dd requires NAME=PATH");
                bindings.add(args[i]);
            } else if (args[i].equals("--")) {
                for (i++; i < args.length; i++) programArgs.add(args[i]);
            } else programArgs.add(args[i]);
        }
        CobolUtil.cob_init(programArgs.toArray(String[]::new), false);
        CobolUtil.setEnv("COB_PACKAGE_PATH", String.join(java.io.File.pathSeparator, packages));
        for (String binding : bindings) {
            int separator = binding.indexOf('=');
            if (separator <= 0 || separator == binding.length() - 1 || !binding.substring(0, separator).matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("DD binding must be NAME=PATH");
            }
            String name = binding.substring(0, separator);
            String path = Path.of(binding.substring(separator + 1)).toAbsolutePath().toString();
            CobolUtil.setEnv("DD_" + name, path);
            if (name.contains("-")) CobolUtil.setEnv("DD_" + name.substring(name.lastIndexOf('-') + 1), path);
        }
        CobolResolve.cobolInitCall();
        CobolDecimal.cobInitNumeric();
        Class<?> programClass = Class.forName(selected.getJSONArray("classes").getString(0));
        CobolRunnable program = (CobolRunnable) programClass.getDeclaredConstructor().newInstance();
        int exit = program.run();
        CobolStopRunException.stopRun();
        if (exit != 0) System.exit(exit);
    }
}
