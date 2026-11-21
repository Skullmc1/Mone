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

import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Minecraft 26.2 registry loading compatibility helper.
 */
public final class McRegistryCompat {

    private McRegistryCompat() {}

    public static CompletableFuture<RegistryAccess> loadRegistryAccess() {
        CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(
                PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource())
        );
        LayeredRegistryAccess<RegistryLayer> baseLayeredRegistry = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(
                closeableResourceManager, baseLayeredRegistry.getLayer(RegistryLayer.STATIC)
        );
        List<HolderLookup.RegistryLookup<?>> worldGenRegistryLookupList = TagLoader.buildUpdatedLookups(
                baseLayeredRegistry.getAccessForLoading(RegistryLayer.WORLDGEN),
                pendingTags
        );
        RegistryAccess.Frozen worldgenRegistries = RegistryDataLoader.load(
                closeableResourceManager,
                worldGenRegistryLookupList,
                RegistryDataLoader.WORLDGEN_REGISTRIES,
                ForkJoinPool.commonPool()
        ).join();
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = baseLayeredRegistry.replaceFrom(
                RegistryLayer.WORLDGEN,
                worldgenRegistries
        );
        return ReloadableServerRegistries.reload(
                layeredRegistryAccess,
                pendingTags,
                closeableResourceManager,
                ForkJoinPool.commonPool()
        ).thenApply(r -> r.layers().compositeAccess());
    }
}
