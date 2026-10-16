package com.rentatools.support;

import com.rentatools.iam.infrastructure.dev.DevUsersSeeder;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import java.util.Set;
import java.util.UUID;

/**
 * Constantes de usuarios determinísticos con UUIDs fijos para pruebas automatizadas.
 */
public final class DevUsers {

    public static final UUID ADMIN_ID = DevUsersSeeder.ADMIN_ID;
    public static final UUID PROVIDER_1_ID = DevUsersSeeder.PROVIDER_1_ID;
    public static final UUID CLIENT_ID = DevUsersSeeder.CLIENT_ID;
    public static final UUID PROVIDER_2_ID = DevUsersSeeder.PROVIDER_2_ID;

    public static final Actor ADMIN_ACTOR = new Actor(ADMIN_ID, Set.of(ActorRole.ADMIN));
    public static final Actor PROVIDER_1_ACTOR = new Actor(PROVIDER_1_ID, Set.of(ActorRole.PROVEEDOR));
    public static final Actor CLIENT_ACTOR = new Actor(CLIENT_ID, Set.of(ActorRole.CLIENTE));
    public static final Actor PROVIDER_2_ACTOR = new Actor(PROVIDER_2_ID, Set.of(ActorRole.PROVEEDOR));

    private DevUsers() {
    }
}
