package jp.osscons.opensourcecobol.libcobj.file;

/** Package bridge for the runtime's package-private file handle. */
public final class RecordTerminator {
    private RecordTerminator() {}
    public static void newline(CobolFile file) {
        file.file.putc((byte) '\n');
    }
}
