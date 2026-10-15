package com.pzmoodle.immersion.admin;

import com.pzmoodle.immersion.trait.OccupationTrait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 发放并清理职业/特质携带物品。 */
public final class GrantItems {
    public static void grant(ServerPlayer player, String occupation, Collection<String> traits) {
        removeMarked(player);
        List<DefOverrides.ItemGrant> grants = new ArrayList<>(DefOverrides.itemsForOccupation(occupation));
        for (String trait : traits) grants.addAll(DefOverrides.itemsForTrait(trait));
        for (DefOverrides.ItemGrant grant : grants) {
            ItemStack stack = grant.resolve(player.registryAccess());
            if (stack.isEmpty()) continue;
            stack.set(PZComponents.GRANTED.get(), true);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    public static void regrantOnline(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var data = com.pzmoodle.immersion.moodle.MoodleAttachments.get(player);
            grant(player, data.occupation, data.traits);
        }
    }

    public static void removeMarked(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getOrDefault(PZComponents.GRANTED.get(), false)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private GrantItems() {}
}
