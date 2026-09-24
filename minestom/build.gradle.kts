/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2026 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    packetevents.`library-conventions`
}

dependencies {
    // the shaded api relocates its patched adventure serializers, the plain one would clash with minestom's
    api(project(":api", "shadow"))
    api(project(":netty-common")) {
        isTransitive = false
    }
    // minestom doesn't ship netty, buffers are still netty based for api compatibility
    api(libs.netty.buffer)

    compileOnly(libs.minestom)

    testImplementation(libs.minestom)
    testImplementation(testlibs.bundles.junit)
    testRuntimeOnly(testlibs.slf4j)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    withType<JavaCompile> {
        options.release = 25
    }

    test {
        useJUnitPlatform()
        // every test class boots its own minestom server, which only exists once per jvm
        forkEvery = 1
        systemProperty("io.netty.leakDetection.level", "paranoid")
    }
}
