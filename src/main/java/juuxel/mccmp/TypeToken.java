/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeToken<T> {
    public Type type() {
        var superclass = (ParameterizedType) getClass().getGenericSuperclass();
        return superclass.getActualTypeArguments()[0];
    }
}
