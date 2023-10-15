/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp.data;

import java.nio.file.Path;

public record MinecraftMetadata(String id, VersionManifest manifest, Path gameJar, Path mappingsJar) {
}
