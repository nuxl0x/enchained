package nux.enchained.teams;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TeamDamageHandler {

    private TeamDamageHandler() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((LivingEntity entity, net.minecraft.entity.damage.DamageSource source, float amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) {
                return true; // not a player getting hit
            }

            Entity direct = source.getAttacker();
            Entity causing = source.getSource();

            ServerPlayerEntity attacker = null;
            if (causing instanceof ServerPlayerEntity c) {
                attacker = c;
            } else if (direct instanceof ServerPlayerEntity d) {
                attacker = d;
            }

            if (attacker == null) {
                return true; // not PVP
            }

            if (attacker == victim) {
                return true; // self-damage, let vanilla handle
            }

            MinecraftServer server = victim.getServer();
            if (server == null) {
                return true;
            }

            TeamManager manager = TeamManager.get(server);

            if (manager.shouldCancelFriendlyFire(victim.getUuid(), attacker.getUuid())) {
                return false; // cancel same-team damage for teams with FF disabled
            }

            return true;
        });
    }
}