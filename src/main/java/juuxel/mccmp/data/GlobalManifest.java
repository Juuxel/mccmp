/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp.data;

import java.util.List;

public record GlobalManifest(List<Version> versions) {
    public record Version(String id, String url) {
    }
}
