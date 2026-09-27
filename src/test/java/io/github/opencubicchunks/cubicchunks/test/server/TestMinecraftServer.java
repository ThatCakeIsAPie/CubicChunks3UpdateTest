package io.github.opencubicchunks.cubicchunks.test.server;

import static io.github.opencubicchunks.cubicchunks.testutils.Misc.setupServerLevel;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;

import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.mixin.test.common.server.level.MinecraftServerTestAccess;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.CloseableReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

// TODO :: These tests don't work, because mocking the minecraft server is extremely hard. If anyone can figure it out, please re-enable these tests.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestMinecraftServer extends BaseTest {
    private CloseableReference<IntegratedServer> setupServer() {
        WorldStem worldStemMock = mock(WorldStem.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        when(worldStemMock.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)).thenReturn(true);
        MockedConstruction<ServerFunctionManager> serverFunctionManagerMockedConstruction = Mockito.mockConstruction(ServerFunctionManager.class,
                withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        return new CloseableReference<>(new IntegratedServer(mock(Thread.class, RETURNS_DEEP_STUBS), mock(Minecraft.class, RETURNS_DEEP_STUBS),
                mock(LevelStorageSource.LevelStorageAccess.class, RETURNS_DEEP_STUBS), mock(PackRepository.class, RETURNS_DEEP_STUBS), worldStemMock,
                Optional.empty(), mock(Services.class, RETURNS_DEEP_STUBS), mock(LevelLoadListener.class, RETURNS_DEEP_STUBS)),
                serverFunctionManagerMockedConstruction);
    }

    @Test
    @Disabled
    public void testSetInitialSpawnVanilla() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            ((MarkableAsCubic) serverLevelReference.value()).cc_setCubic();
            try (CloseableReference<IntegratedServer> server = setupServer()) {
                ((MinecraftServerTestAccess) server.value()).invoke_setInitialSpawn(serverLevelReference.value(), mock(RETURNS_DEEP_STUBS), false,
                        false, mock(LevelLoadListener.class));
            }
        }
    }

    @Test
    @Disabled
    void testPrepareLevelsVanilla() throws Exception {
        try (CloseableReference<IntegratedServer> server = setupServer()) {
            ((MarkableAsCubic) server.value().overworld()).cc_setCubic();
            ((MinecraftServerTestAccess) server.value()).invoke_prepareLevels();
        }
    }
}
