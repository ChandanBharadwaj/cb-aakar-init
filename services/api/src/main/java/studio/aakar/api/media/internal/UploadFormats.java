package studio.aakar.api.media.internal;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import studio.aakar.api.media.UploadKind;

/**
 * The formats a customer may upload (the {@code content_source.format} enum of {@code design-spec.v1.json}) and how each
 * is recognised: first by the file name's extension, then by its first bytes, so a renamed or damaged file is refused
 * before anything is stored. Images: png, jpg (.jpg/.jpeg), webp, heic. Models (the inspect service's loaders): stl
 * (binary or ASCII), glb, 3mf (a zip), obj, ply, off, gltf. Pure functions.
 */
final class UploadFormats {

    /** How much of a text model file is read to recognise it. */
    static final int TEXT_HEAD_BYTES = 64 * 1024;
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");
    private static final Pattern OFF_HEADER = Pattern.compile("^[A-Za-z0-9]*OFF(\\s.*)?$");

    /** A format id with its kind, served content type and signature check. */
    record Format(String id, UploadKind kind, String contentType, String label, Predicate<byte[]> signature) {

        boolean matches(byte[] bytes) {
            return bytes != null && bytes.length > 0 && signature.test(bytes);
        }
    }

    static final Map<String, Format> FORMATS = new LinkedHashMap<>();
    /** File extension (lowercase) → format id. */
    static final Map<String, String> EXTENSIONS = new LinkedHashMap<>();

    static {
        add(new Format("png", UploadKind.image, "image/png", "PNG image", UploadFormats::png), "png");
        add(new Format("jpg", UploadKind.image, "image/jpeg", "JPG image", UploadFormats::jpg), "jpg", "jpeg");
        add(new Format("webp", UploadKind.image, "image/webp", "WEBP image", UploadFormats::webp), "webp");
        add(new Format("heic", UploadKind.image, "image/heic", "HEIC image", UploadFormats::heic), "heic");
        add(new Format("stl", UploadKind.model, "model/stl", "STL model file", UploadFormats::stl), "stl");
        add(new Format("glb", UploadKind.model, "model/gltf-binary", "GLB model file", UploadFormats::glb), "glb");
        add(new Format("3mf", UploadKind.model, "model/3mf", "3MF model file", UploadFormats::threeMf), "3mf");
        add(new Format("obj", UploadKind.model, "model/obj", "OBJ model file", UploadFormats::obj), "obj");
        add(new Format("ply", UploadKind.model, "application/octet-stream", "PLY model file", UploadFormats::ply), "ply");
        add(new Format("off", UploadKind.model, "application/octet-stream", "OFF model file", UploadFormats::off), "off");
        add(new Format("gltf", UploadKind.model, "model/gltf+json", "glTF model file", UploadFormats::gltf), "gltf");
    }

    private UploadFormats() {
    }

    private static void add(Format format, String... extensions) {
        FORMATS.put(format.id(), format);
        for (String extension : extensions) {
            EXTENSIONS.put(extension, format.id());
        }
    }

    /** The format a file name claims by its extension, if it is one we take. */
    static Optional<Format> byFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(EXTENSIONS.get(extension)).map(FORMATS::get);
    }

    static List<String> ids(UploadKind kind) {
        return FORMATS.values().stream().filter(f -> f.kind() == kind).map(Format::id).toList();
    }

    // ---- signatures -------------------------------------------------------------------------------------------------

    static boolean png(byte[] b) {
        return startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    }

    static boolean jpg(byte[] b) {
        return startsWith(b, 0xFF, 0xD8, 0xFF);
    }

    static boolean webp(byte[] b) {
        return b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP");
    }

    /** ISO base media file: {@code ftyp} box first, with a HEIF/HEVC brand. */
    static boolean heic(byte[] b) {
        return b.length >= 12 && ascii(b, 4, "ftyp") && HEIC_BRANDS.contains(new String(b, 8, 4, StandardCharsets.US_ASCII));
    }

    /** Binary STL (80-byte header, triangle count, 50 bytes per triangle) or ASCII STL ({@code solid … facet|endsolid}). */
    static boolean stl(byte[] b) {
        if (b.length >= 84) {
            long triangles = (b[80] & 0xFFL) | (b[81] & 0xFFL) << 8 | (b[82] & 0xFFL) << 16 | (b[83] & 0xFFL) << 24;
            if (84 + 50 * triangles == b.length) {
                return true;
            }
        }
        String head = textHead(b);
        if (head == null) {
            return false;
        }
        String trimmed = head.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("solid") && (trimmed.contains("facet") || trimmed.contains("endsolid"));
    }

    static boolean glb(byte[] b) {
        return ascii(b, 0, "glTF");
    }

    /** 3MF is an OPC zip package. */
    static boolean threeMf(byte[] b) {
        return startsWith(b, 'P', 'K', 0x03, 0x04);
    }

    /** Text with at least one vertex line ({@code v x y z}) near the top. */
    static boolean obj(byte[] b) {
        String head = textHead(b);
        if (head == null) {
            return false;
        }
        for (String line : head.split("\\R")) {
            String t = line.strip();
            if (t.startsWith("v ") || t.startsWith("v\t")) {
                return true;
            }
        }
        return false;
    }

    static boolean ply(byte[] b) {
        return ascii(b, 0, "ply\n") || ascii(b, 0, "ply\r\n");
    }

    /** {@code OFF} (or a variant such as {@code COFF}, {@code NOFF}) on the first line that is not blank or a comment. */
    static boolean off(byte[] b) {
        String head = textHead(b);
        if (head == null) {
            return false;
        }
        for (String line : head.split("\\R")) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            return OFF_HEADER.matcher(t).matches();
        }
        return false;
    }

    /** glTF JSON: an object whose text names the required {@code asset} property. */
    static boolean gltf(byte[] b) {
        String head = textHead(b);
        if (head == null) {
            return false;
        }
        String trimmed = head.startsWith("﻿") ? head.substring(1).stripLeading() : head.stripLeading();
        return trimmed.startsWith("{") && trimmed.contains("\"asset\"");
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static boolean startsWith(byte[] b, int... signature) {
        if (b.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((b[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (b[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** The first {@link #TEXT_HEAD_BYTES} as UTF-8 text, or null when they hold a NUL byte (a binary file). */
    static String textHead(byte[] b) {
        int length = Math.min(b.length, TEXT_HEAD_BYTES);
        for (int i = 0; i < length; i++) {
            if (b[i] == 0) {
                return null;
            }
        }
        return new String(b, 0, length, StandardCharsets.UTF_8);
    }
}
