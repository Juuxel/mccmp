/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp.data;

import java.util.List;
import java.util.Map;

public record VersionManifest(String id, Map<String, Download> downloads, List<Library> libraries) {
    public record Download(String url) {
    }

    public record Library(String name, Downloads downloads) {
        public record Downloads(Artifact artifact) {
        }

        public record Artifact(String path, String url) {
        }
    }
}
