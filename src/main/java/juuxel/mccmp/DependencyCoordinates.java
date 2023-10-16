/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp;

import org.jetbrains.annotations.Nullable;

public record DependencyCoordinates(
    String group,
    String name,
    String version,
    @Nullable String classifier
) {
    public DependencyCoordinates(String group, String name, String version) {
        this(group, name, version, null);
    }

    public String toUrlPart() {
        var sb = new StringBuilder()
            .append(group.replace('.', '/'))
            .append('/')
            .append(name)
            .append('/')
            .append(version)
            .append('/')
            .append(name)
            .append('-')
            .append(version);

        if (classifier != null) {
            sb.append('-').append(classifier);
        }

        sb.append(".jar");
        return sb.toString();
    }

    public String toFabricMavenUrl() {
        return "https://maven.fabricmc.net/" + toUrlPart();
    }
}
