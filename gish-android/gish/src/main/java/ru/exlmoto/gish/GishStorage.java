/************************************************************************************
** The MIT License (MIT)
**
** Copyright (c) 2017 EXL
**
** Permission is hereby granted, free of charge, to any person obtaining a copy
** of this software and associated documentation files (the "Software"), to deal
** in the Software without restriction, including without limitation the rights
** to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
** copies of the Software, and to permit persons to whom the Software is
** furnished to do so, subject to the following conditions:
**
** The above copyright notice and this permission notice shall be included in all
** copies or substantial portions of the Software.
**
** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
** IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
** FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
** AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
** LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
** OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
** SOFTWARE.
************************************************************************************/

package ru.exlmoto.gish;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Locates and populates the directory the native engine chdir()s into.
 *
 * <p>The original port pointed the engine at an arbitrary path under
 * {@code /sdcard} that the user browsed to. Android 11 removed File-API access
 * to shared storage for non-media files, so that path stops being readable no
 * matter which permissions are granted. Instead the data now lives in
 * app-specific external storage, which needs no permission on any API level,
 * and is filled either by the user copying files there over USB or by importing
 * a ZIP / folder through the Storage Access Framework.
 *
 * <p>Only reads happen against this directory: saves and config go to internal
 * storage via {@code SDL_AndroidGetInternalStoragePath()} (see cpp/Gish/game/config.c).
 */
public final class GishStorage {

    /** Relative path the engine is guaranteed to need; used to validate a directory. */
    private static final String MARKER_FILE = "texture/face.tga";

    private static final String DATA_DIR_NAME = "gishdata";

    private GishStorage() {
    }

    /**
     * The managed data directory, e.g.
     * {@code /sdcard/Android/data/ru.exlmoto.gish/files/gishdata}.
     * Falls back to internal storage when no external volume is mounted.
     */
    public static File getDefaultDataDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(base, DATA_DIR_NAME);
    }

    /**
     * The engine builds paths by plain string concatenation, so the value handed
     * to it over JNI has to keep its trailing separator.
     */
    public static String asEnginePath(File dir) {
        String path = dir.getAbsolutePath();
        return path.endsWith("/") ? path : path + "/";
    }

    /** True when {@code dir} actually holds unpacked Gish data files. */
    public static boolean isValidDataDir(File dir) {
        if (dir == null) {
            return false;
        }
        File marker = new File(dir, MARKER_FILE);
        return marker.exists() && marker.isFile();
    }

    public static boolean isValidDataPath(String path) {
        return path != null && !path.isEmpty() && isValidDataDir(new File(path));
    }

    /**
     * Whether the legacy "browse the whole device" picker can still return a
     * usable path. From Android 11 the File API cannot read arbitrary shared
     * storage, so offering it there would only produce confusing failures.
     */
    public static boolean isLegacyBrowsingUsable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return false;
        }
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        // Ignoring the result: a failed unlink surfaces later as a failed import.
        fileOrDirectory.delete();
    }

    // ---------------------------------------------------------------- ZIP import

    /**
     * Extracts a Gish data archive into {@code destination}, replacing whatever
     * was there.
     *
     * <p>If every entry sits under one common top-level directory (the usual
     * shape of a zipped "Gish" folder), that directory is stripped so
     * {@code texture/} ends up directly in {@code destination}.
     */
    public static void importFromZip(Context context, Uri source, File destination)
            throws IOException {
        String prefix = findCommonTopLevelDir(context, source);

        deleteRecursive(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IOException("Cannot create " + destination);
        }

        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) {
            throw new IOException("Cannot open " + source);
        }

        ZipInputStream zip = new ZipInputStream(raw);
        try {
            byte[] buffer = new byte[64 * 1024];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = stripPrefix(entry.getName(), prefix);
                if (name == null || name.isEmpty()) {
                    zip.closeEntry();
                    continue;
                }

                // Zip Slip: an archive may carry "../" entries that would
                // otherwise let extraction write outside the destination.
                File target = new File(destination, name);
                if (!isInside(destination, target)) {
                    throw new IOException("Refusing entry outside destination: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    OutputStream out = new FileOutputStream(target);
                    try {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    } finally {
                        closeQuietly(out);
                    }
                }
                zip.closeEntry();
            }
        } finally {
            closeQuietly(zip);
        }
    }

    /**
     * Returns the single top-level directory shared by every entry, or null when
     * entries live at the archive root or under differing roots.
     */
    private static String findCommonTopLevelDir(Context context, Uri source) throws IOException {
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) {
            throw new IOException("Cannot open " + source);
        }

        ZipInputStream zip = new ZipInputStream(raw);
        try {
            String candidate = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash <= 0) {
                    // A file at the archive root: nothing common to strip.
                    if (!entry.isDirectory()) {
                        return null;
                    }
                    zip.closeEntry();
                    continue;
                }
                String top = name.substring(0, slash);
                if (candidate == null) {
                    candidate = top;
                } else if (!candidate.equals(top)) {
                    return null;
                }
                zip.closeEntry();
            }
            return candidate;
        } finally {
            closeQuietly(zip);
        }
    }

    private static String stripPrefix(String name, String prefix) {
        String cleaned = name.replace('\\', '/');
        if (prefix == null) {
            return cleaned;
        }
        String withSlash = prefix + "/";
        if (cleaned.equals(prefix) || cleaned.equals(withSlash)) {
            return null;
        }
        return cleaned.startsWith(withSlash) ? cleaned.substring(withSlash.length()) : cleaned;
    }

    private static boolean isInside(File directory, File target) throws IOException {
        String dirPath = directory.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        return targetPath.equals(dirPath) || targetPath.startsWith(dirPath + File.separator);
    }

    // --------------------------------------------------------------- SAF import

    /**
     * Recursively copies a user-picked document tree into {@code destination}.
     *
     * <p>If the picked folder itself is not the data root but contains exactly
     * one subfolder that is, that subfolder is used instead — picking the
     * enclosing "Gish" folder is the natural thing for a user to do.
     */
    public static void importFromTree(Context context, Uri treeUri, File destination)
            throws IOException {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.isDirectory()) {
            throw new IOException("Not a readable folder: " + treeUri);
        }

        DocumentFile dataRoot = resolveDataRoot(root);

        deleteRecursive(destination);
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IOException("Cannot create " + destination);
        }

        copyTree(context, dataRoot, destination);
    }

    private static DocumentFile resolveDataRoot(DocumentFile root) {
        if (root.findFile("texture") != null) {
            return root;
        }
        DocumentFile onlyDir = null;
        for (DocumentFile child : root.listFiles()) {
            if (!child.isDirectory()) {
                continue;
            }
            if (onlyDir != null) {
                return root;
            }
            onlyDir = child;
        }
        if (onlyDir != null && onlyDir.findFile("texture") != null) {
            return onlyDir;
        }
        return root;
    }

    private static void copyTree(Context context, DocumentFile source, File destination)
            throws IOException {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0) {
                continue;
            }
            File target = new File(destination, name);
            if (!isInside(destination, target)) {
                continue;
            }
            if (child.isDirectory()) {
                target.mkdirs();
                copyTree(context, child, target);
            } else {
                copyDocument(context, child, target);
            }
        }
    }

    private static void copyDocument(Context context, DocumentFile source, File target)
            throws IOException {
        InputStream in = context.getContentResolver().openInputStream(source.getUri());
        if (in == null) {
            throw new IOException("Cannot read " + source.getUri());
        }
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(out);
            closeQuietly(in);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing useful to do while unwinding.
        }
    }
}
