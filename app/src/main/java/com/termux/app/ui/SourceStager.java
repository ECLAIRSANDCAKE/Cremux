package com.termux.app.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Copies a picked zip or folder (Storage Access Framework URIs) into a plain directory git can read. */
public final class SourceStager {

    private SourceStager() { }

    public static String displayName(Context ctx, Uri uri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "source" : last.substring(last.lastIndexOf(':') + 1);
    }

    public static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    /** @return number of files written. */
    public static int stageZip(Context ctx, Uri zip, File dest, boolean unwrapSingleFolder) throws IOException {
        deleteRecursive(dest);
        if (!dest.mkdirs()) throw new IOException("Cannot create " + dest);
        String root = dest.getCanonicalPath() + File.separator;
        int count = 0;
        InputStream raw = ctx.getContentResolver().openInputStream(zip);
        if (raw == null) throw new IOException("Cannot open zip");
        ZipInputStream zin = new ZipInputStream(raw);
        try {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) continue;
                File out = new File(dest, name);
                String canon = out.getCanonicalPath();
                if (!(canon + File.separator).startsWith(root)) throw new IOException("Unsafe path in zip: " + name);
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(out);
                int n;
                while ((n = zin.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                count++;
            }
        } finally {
            zin.close();
        }
        if (unwrapSingleFolder) unwrap(dest);
        return count;
    }

    private static void unwrap(File dest) throws IOException {
        File[] kids = dest.listFiles();
        if (kids == null || kids.length != 1 || !kids[0].isDirectory()) return;
        File tmp = new File(dest.getParentFile(), "unwrap_tmp");
        deleteRecursive(tmp);
        if (!kids[0].renameTo(tmp)) return;
        dest.delete();
        if (!tmp.renameTo(dest)) throw new IOException("Could not unwrap top folder");
    }

    /** @return number of files written. */
    public static int stageTree(Context ctx, Uri tree, File dest) throws IOException {
        deleteRecursive(dest);
        if (!dest.mkdirs()) throw new IOException("Cannot create " + dest);
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        return copyDir(ctx.getContentResolver(), tree, rootId, dest);
    }

    private static int copyDir(ContentResolver cr, Uri tree, String docId, File dir) throws IOException {
        int count = 0;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = cr.query(children, new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) throw new IOException("Cannot list folder");
        try {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (name == null || name.contains("/") || name.equals("..") || name.equals(".") || name.equals(".git")) continue;
                File out = new File(dir, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    out.mkdirs();
                    count += copyDir(cr, tree, id, out);
                } else {
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    InputStream in = cr.openInputStream(doc);
                    if (in == null) continue;
                    OutputStream os = new FileOutputStream(out);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    os.close();
                    in.close();
                    count++;
                }
            }
        } finally {
            c.close();
        }
        return count;
    }
}
