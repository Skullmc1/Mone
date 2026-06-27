/*
 * This file is part of Mone.
 *
 * Mone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Mone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mone.  If not, see <https://www.gnu.org/licenses/>.
 */

package mone.api.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 */
public final class TypeUtils {

    private TypeUtils() {}

    /**
     * Resolves the "base type" for the specified type. For example, if the specified
     * type is {@code List<String>}, then {@code List.class} will be returned. If the
     * specified type is already a class, then it is directly returned.
     *
     * @param type The type to resolve
     * @return The base class
     */
    public static Class<?> resolveBaseClass(Type type) {
        return type instanceof Class ? (Class<?>) type
                : type instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) type).getRawType()
                : null;
    }
}
