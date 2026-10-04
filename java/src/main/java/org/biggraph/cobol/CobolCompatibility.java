package org.biggraph.cobol;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import jp.osscons.opensourcecobol.libcobj.common.CobolModule;
import jp.osscons.opensourcecobol.libcobj.common.CobolUtil;
import jp.osscons.opensourcecobol.libcobj.data.AbstractCobolField;
import jp.osscons.opensourcecobol.libcobj.data.CobolDataStorage;
import jp.osscons.opensourcecobol.libcobj.data.CobolFieldFactory;
import jp.osscons.opensourcecobol.libcobj.data.CobolDecimal;
import jp.osscons.opensourcecobol.libcobj.file.CobolFile;
import jp.osscons.opensourcecobol.libcobj.file.CobolLineSequentialFile;
import jp.osscons.opensourcecobol.libcobj.file.CobolSequentialFile;
import jp.osscons.opensourcecobol.libcobj.exceptions.CobolStopRunException;

/** Compatibility fixes for verified differences between the two compiler runtimes. */
public final class CobolCompatibility {
    private CobolCompatibility() {}
    private record Key(int direction, AbstractCobolField field, int offset) {}
    private static final ThreadLocal<List<Key>> KEYS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<CobolDataStorage> COLLATION = new ThreadLocal<>();
    private static final Map<CobolFile, Boolean> WRITTEN = new WeakHashMap<>();
    private static final Map<CobolFile, Boolean> STDOUT = new WeakHashMap<>();

    public static void registerStdout(CobolFile file) { STDOUT.put(file, true); }
    private static void success(CobolFile file, AbstractCobolField status) {
        file.file_status[0] = '0'; file.file_status[1] = '0';
        if (status != null) status.moveFrom("00");
    }
    public static void open(CobolFile file, int mode, int sharing, AbstractCobolField status) {
        if (STDOUT.containsKey(file)) { success(file, status); return; }
        file.open(mode, sharing, status);
    }
    public static void close(CobolFile file, int options, AbstractCobolField status) {
        if (STDOUT.containsKey(file)) { success(file, status); return; }
        if (file instanceof CobolSequentialFile && Boolean.TRUE.equals(WRITTEN.get(file))) {
            jp.osscons.opensourcecobol.libcobj.file.RecordTerminator.newline(file);
        }
        file.close(options, status);
        WRITTEN.remove(file);
    }

    public static void write(CobolFile file, AbstractCobolField record, int options,
                             AbstractCobolField status) throws CobolStopRunException {
        if (STDOUT.containsKey(file)) {
            String value = new String(record.getDataStorage().getByteArray(0, record.getSize()), AbstractCobolField.charSetSJIS);
            System.out.println(value.stripTrailing()); success(file, status); return;
        }
        // The Java runtime immediately emits the record terminator. GnuCOBOL
        // defers it until the next ADVANCING request, so count that line once.
        if (file instanceof CobolLineSequentialFile && WRITTEN.containsKey(file)
            && (options & CobolFile.COB_WRITE_AFTER) != 0
            && (options & CobolFile.COB_WRITE_LINES) != 0 && (options & 0xFFFF) > 0
            && (file.flag_select_features & CobolFile.COB_SELECT_LINAGE) == 0) {
            options--;
        }
        file.write(record, options, status);
        WRITTEN.put(file, (options & CobolFile.COB_WRITE_AFTER) != 0);
    }

    public static AbstractCobolField displayIntegralFunction(AbstractCobolField field) {
        var decimal = field.getDecimal();
        var value = decimal.getValue().movePointLeft(decimal.getScale()).toBigInteger();
        String digits = value.toString();
        return CobolFieldFactory.makeCobolField("0".repeat(Math.max(0, 9-digits.length())) + digits);
    }

    public static void setDecimalField(CobolDecimal decimal, AbstractCobolField field) {
        decimal.setField(normalize(field));
    }
    public static int numdisp(CobolDataStorage storage, int width) {
        byte[] data = storage.getByteArray(0, width);
        for (int i = 0; i < width; i++) if (data[i]==' ' || data[i]==0) data[i]='0';
        return new CobolDataStorage(data).getNumdisp(width);
    }
    public static int add(AbstractCobolField target, AbstractCobolField source, int options)
        throws CobolStopRunException {
        var amount = normalize(target).getDecimal();
        amount.add(normalize(source).getDecimal());
        return amount.getField(target, options);
    }
    private static AbstractCobolField normalize(AbstractCobolField field) {
        if (field.getAttribute().isTypeNumericDisplay()) {
            byte[] data = field.getDataStorage().getByteArray(0, field.getSize());
            for (int i = 0; i < data.length; i++) {
                if (data[i] == ' ' || data[i] == 0) data[i] = '0';
            }
            field = CobolFieldFactory.makeCobolField(field.getSize(), new CobolDataStorage(data), field.getAttribute());
        }
        return field;
    }

    public static void sortTableInit(int keys, int collation) {
        sortTableInit(keys, (CobolDataStorage) null);
    }
    public static void sortTableInit(int keys, CobolDataStorage collation) {
        KEYS.get().clear();
        COLLATION.set(collation == null ? CobolModule.getCurrentModule().collating_sequence : collation);
    }
    public static void sortTableInitKey(int direction, AbstractCobolField field, int offset) {
        KEYS.get().add(new Key(direction, field, offset));
    }
    public static void sortTable(AbstractCobolField table, int count) {
        int width = table.getSize();
        byte[][] records = new byte[count][];
        for (int i = 0; i < count; i++) {
            records[i] = table.getDataStorage().getByteArray(i * width, width);
        }
        Arrays.sort(records, (left, right) -> {
            for (Key key : KEYS.get()) {
                int offset = key.field().getDataStorage().getIndex() - table.getDataStorage().getIndex();
                CobolDataStorage a = new CobolDataStorage(left, offset);
                CobolDataStorage b = new CobolDataStorage(right, offset);
                int compared;
                if (key.field().getAttribute().isTypeNumeric()) {
                    var af = CobolFieldFactory.makeCobolField(key.field().getSize(), a, key.field().getAttribute());
                    var bf = CobolFieldFactory.makeCobolField(key.field().getSize(), b, key.field().getAttribute());
                    compared = af.numericCompareTo(bf);
                } else {
                    compared = CobolUtil.alnumCmps(a, b, key.field().getSize(), COLLATION.get());
                }
                if (compared != 0) return key.direction() == 0 ? compared : -compared;
            }
            return 0;
        });
        for (int i = 0; i < count; i++) {
            table.getDataStorage().getSubDataStorage(i * width).memcpy(records[i], width);
        }
    }

    public static void display(int output, int newline, int count, AbstractCobolField... fields) {
        PrintStream stream = output == 0 ? System.out : System.err;
        for (AbstractCobolField field : fields) {
            var attr = field.getAttribute();
            if (attr.isTypeNumericBinary() && CobolModule.getCurrentModule().flag_pretty_display == 0) {
                int digits = switch (field.getSize()) { case 1 -> 3; case 2 -> 5; case 4 -> 10; default -> 20; };
                var value = field.getDecimal().getValue().toBigInteger();
                String number = value.abs().toString();
                stream.print((attr.isFlagHaveSign() ? (value.signum()<0 ? "-" : "+") : "")
                    + "0".repeat(Math.max(0, digits-number.length())) + number);
            } else if (attr.isTypeNumericDisplay() && CobolModule.getCurrentModule().flag_pretty_display == 0) {
                var numeric = field.getDecimal().getValue().toBigInteger();
                String value = numeric.abs().toString();
                stream.print("0".repeat(Math.max(0, attr.getDigits()-value.length())) + value
                    + (attr.isFlagHaveSign() ? (numeric.signum()<0 ? "-" : "+") : ""));
            } else if (attr.isTypeNumeric()) {
                String value = field.toString();
                int decimal = value.indexOf('.');
                if (decimal >= 0) {
                    value = value.substring(0, decimal + 1) + value.substring(decimal + 1).replace(' ', '0');
                }
                stream.print(value);
            } else {
                byte[] raw = field.getDataStorage().getByteArray(0, field.getSize());
                stream.print(new String(raw, AbstractCobolField.charSetSJIS));
            }
        }
        if (newline == 1) stream.println();
    }
}
