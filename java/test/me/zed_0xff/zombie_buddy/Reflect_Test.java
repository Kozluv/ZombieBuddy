package me.zed_0xff.zombie_buddy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import me.zed_0xff.zombie_buddy.annotations.Patch;

class Reflect_Test {
    @Test
    void on_private_class() {
        var r = Reflect.on(Patch.NO_EXCEPTION_DESC);
        assertThat(r.isPresent()).isTrue();

        Set<String> fieldNames = Set.copyOf(
            r.fields(Reflect.DECLARED)
            .stream()
            .map(m -> m.getName())
            .toList()
        );
        assertThat(fieldNames)
            .containsExactlyInAnyOrder("serialVersionUID", "DESCRIPTION");
    }
}
