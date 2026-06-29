/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.recia.mce.api.escomceapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LicenseHeaderTest {

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "/\\*\n" +
            " \\* Copyright \\(C\\) \\d{4} GIP-RECIA, Inc\\.\n" +
            " \\*\n" +
            " \\* Licensed under the Apache License, Version 2\\.0 \\(the \"License\"\\);\n" +
            " \\* you may not use this file except in compliance with the License\\.\n" +
            " \\* You may obtain a copy of the License at\n" +
            " \\*\n" +
            " \\* {1,5}https?://www\\.apache\\.org/licenses/LICENSE-2\\.0\n" +
            " \\*\n" +
            " \\* Unless required by applicable law or agreed to in writing, software\n" +
            " \\* distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " \\* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied\\.\n" +
            " \\* See the License for the specific language governing permissions and\n" +
            " \\* limitations under the License\\.\n" +
            " \\*/\n");

    @Test
    void allJavaSourceFilesHaveLicenseHeader() throws IOException {
        Path mainSrc = Paths.get("src/main/java");
        Path testSrc = Paths.get("src/test/java");

        List<Path> mainFiles = new ArrayList<>();
        List<Path> testFiles = new ArrayList<>();

        try (var stream = Files.walk(mainSrc)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(mainFiles::add);
        }
        try (var stream = Files.walk(testSrc)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(testFiles::add);
        }

        List<String> missing = new ArrayList<>();

        for (Path file : mainFiles) {
            String content = Files.readString(file);
            if (!HEADER_PATTERN.matcher(content).find()) {
                missing.add("main/" + mainSrc.relativize(file));
            }
        }

        for (Path file : testFiles) {
            String content = Files.readString(file);
            if (!HEADER_PATTERN.matcher(content).find()) {
                missing.add("test/" + testSrc.relativize(file));
            }
        }

        if (!missing.isEmpty()) {
            fail("Fichiers sans entête de licence (" + missing.size() + ") :\n  " +
                    String.join("\n  ", missing));
        }

        assertTrue(mainFiles.size() + testFiles.size() > 0,
                "Aucun fichier source Java trouvé");
    }
}
