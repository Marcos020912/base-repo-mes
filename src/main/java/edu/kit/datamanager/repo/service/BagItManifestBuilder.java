package edu.kit.datamanager.repo.service;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** BagIt 1.0 manifests over the exact bytes written to the preservation ZIP (RFC 8493). */
public final class BagItManifestBuilder {
    private BagItManifestBuilder() {}

    public static byte[] declaration() {
        return "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] payloadManifest(List<RoCrateMetadataBuilder.PackagedFile> files) {
        return manifest(files, true);
    }

    public static byte[] tagManifest(List<RoCrateMetadataBuilder.PackagedFile> files) {
        return manifest(files, false);
    }

    private static byte[] manifest(List<RoCrateMetadataBuilder.PackagedFile> files, boolean payload) {
        StringBuilder lines = new StringBuilder();
        files.stream().filter(file -> file.path().startsWith("data/") == payload)
                .filter(file -> !file.path().startsWith("tagmanifest-"))
                .sorted(Comparator.comparing(RoCrateMetadataBuilder.PackagedFile::path))
                .forEach(file -> lines.append(file.sha256()).append("  ")
                        .append(file.path().replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
                        .append('\n'));
        return lines.toString().getBytes(StandardCharsets.UTF_8);
    }
}
