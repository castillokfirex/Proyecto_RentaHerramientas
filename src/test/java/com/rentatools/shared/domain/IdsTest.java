package com.rentatools.shared.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {

    @Test
    void generatesValidUuidV7WithVersion7AndRfcVariant2() {
        UUID id = Ids.newId();

        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void generatesTimeOrderedMonotonicUuids() {
        int count = 1_000;
        List<UUID> generated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            generated.add(Ids.newId());
        }

        Set<UUID> unique = new HashSet<>(generated);
        assertThat(unique).hasSize(count);

        for (int i = 1; i < generated.size(); i++) {
            UUID previous = generated.get(i - 1);
            UUID current = generated.get(i);
            assertThat(previous.compareTo(current))
                    .as("UUID at index %d (%s) should be strictly less than UUID at index %d (%s)",
                            i - 1, previous, i, current)
                    .isLessThan(0);
            assertThat(previous.toString().compareTo(current.toString()))
                    .isLessThan(0);
        }
    }

    @Test
    void encodesTimestampCorrectlyForChronologicalOrdering() {
        Instant earlier = Instant.parse("2026-10-16T10:00:00Z");
        Instant later = Instant.parse("2026-10-16T10:00:05Z");

        UUID idEarlier = Ids.newId(earlier);
        UUID idLater = Ids.newId(later);

        assertThat(idEarlier.version()).isEqualTo(7);
        assertThat(idLater.version()).isEqualTo(7);
        assertThat(Ids.extractInstant(idEarlier)).isEqualTo(earlier);
        assertThat(Ids.extractInstant(idLater)).isEqualTo(later);
        assertThat(idEarlier.compareTo(idLater)).isLessThan(0);
    }
}
