package io.github.opencubicchunks.cubicchunks.client.gui.render.pip;

import java.util.EnumSet;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.client.gui.render.state.pip.WorldLoadingCubeStatusesRenderState;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.CubicLevelLoadingScreen;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Vector3f;

/**
 * Picture-in-Picture renderer used to render the loading animation used when loading into a cubic world.
 * <p>
 * 26.1 only exposes column status through {@link ChunkLoadStatusView}. Cube faces are not drawn; that needs a cube status
 * store (Phase 6b) and is not a Vulkan rewrite.
 */
public class WorldLoadingCubeStatusesRenderer extends PictureInPictureRenderer<WorldLoadingCubeStatusesRenderState> {
    // PictureInPictureRenderer sets an orthographic projection before renderToTexture. This replaces it with perspective.
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer perspectiveProjectionMatrixBuffer = new ProjectionMatrixBuffer("world loading cubes");

    private static final float FOV_DEGREES = 60F;

    /** Distance between the center of the loading animation and the camera */
    private static final float CAMERA_DISTANCE = 20F;

    /** Rotation of the loading animation about the X axis (rotating "downwards" towards the camera) */
    private static final float X_ROTATION_DEGREES = 30F;

    private static final double Y_ROTATION_DEGREES_PER_SECOND = 40;

    /** Constant controlling the size at which cubes are rendered */
    private static final float CUBE_BASE_SCALE = 0.12F;

    private static final float CHUNK_Y_OFFSET = -30F;

    private static final int CHUNK_ALPHA = 0x80;

    private static final int DARKEN_PERCENTAGE_CUBE_FRONT_BACK = 20;
    private static final int DARKEN_PERCENTAGE_CUBE_SIDES = 30;
    private static final int DARKEN_PERCENTAGE_CUBE_BOTTOM = 40;

    public WorldLoadingCubeStatusesRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override public void close() {
        super.close();

        perspectiveProjectionMatrixBuffer.close();
    }

    @Override public Class<WorldLoadingCubeStatusesRenderState> getRenderStateClass() {
        return WorldLoadingCubeStatusesRenderState.class;
    }

    @SuppressWarnings("checkstyle:MagicNumber") // millis to seconds; degrees modulo 360
    private static float yRotationAngleDegrees(long milliseconds) {
        return (float) ((milliseconds * (Y_ROTATION_DEGREES_PER_SECOND / 1000)) % 360);
    }

    @Override protected void renderToTexture(WorldLoadingCubeStatusesRenderState state, PoseStack poseStackUnused) {
        int width = state.x1() - state.x0();
        int height = state.y1() - state.y0();
        this.projection.setupPerspective(0.05F, 100F, FOV_DEGREES, width, height);
        RenderSystem.setProjectionMatrix(this.perspectiveProjectionMatrixBuffer.getBuffer(this.projection), ProjectionType.PERSPECTIVE);

        // The PoseStack passed to this method is set up for the orthographic projection matrix;
        // as we're using perspective projection instead, we set up our own PoseStack.
        var poseStack = new PoseStack();

        poseStack.translate(0f, 0f, -CAMERA_DISTANCE);
        poseStack.rotateAround(Axis.XP.rotationDegrees(X_ROTATION_DEGREES), 0.0F, 0.0F, 0.0F);
        poseStack.rotateAround(Axis.YP.rotationDegrees(yRotationAngleDegrees(System.currentTimeMillis())), 0.0F, 0.0F, 0.0F);
        var vertexConsumer = this.bufferSource.getBuffer(RenderTypes.debugQuads());
        renderColumns(vertexConsumer, poseStack, state.chunkLoadStatusView(), state.scale());
        this.bufferSource.endBatch();
    }

    @Override protected String getTextureLabel() {
        return "world loading cube statuses";
    }

    private static void renderColumns(VertexConsumer vertexConsumer, PoseStack poseStack, ChunkLoadStatusView statusView, float scale) {
        int diameter = statusView.radius() * 2 + 1;
        for (int cdx = 0; cdx < diameter; cdx++) {
            for (int cdz = 0; cdz < diameter; cdz++) {
                ChunkStatus columnStatus = statusView.get(cdx, cdz);
                if (columnStatus == null) {
                    continue;
                }
                int color = ARGB.color(CHUNK_ALPHA,
                        CubicLevelLoadingScreen.STATUS_COLORS.getOrDefault(columnStatus, CubicLevelLoadingScreen.DEFAULT_STATUS_COLOR));
                // Column slabs stand in for the old cube volume. Per-cube faces need a status store 26.1 removed.
                drawCube(vertexConsumer, poseStack, cdx - (float) diameter / 2, CHUNK_Y_OFFSET, cdz - (float) diameter / 2,
                        CUBE_BASE_SCALE * scale / CubicConstants.DIAMETER_IN_SECTIONS, color, EnumSet.of(Direction.UP));
            }
        }
    }

    private static void drawCube(
            VertexConsumer vertexConsumer, PoseStack poseStack, float x, float y, float z, float scale, int color, EnumSet<Direction> renderFaces
    ) {
        float x0 = x * scale;
        float x1 = x0 + scale;
        float y0 = y * scale;
        float y1 = y0 + scale;
        float z0 = z * scale;
        float z1 = z0 + scale;
        if (renderFaces.contains(Direction.UP)) {
            // up face
            vertex(vertexConsumer, poseStack, x0, y1, z0, 0, 1, 0, color);
            vertex(vertexConsumer, poseStack, x0, y1, z1, 0, 1, 0, color);
            vertex(vertexConsumer, poseStack, x1, y1, z1, 0, 1, 0, color);
            vertex(vertexConsumer, poseStack, x1, y1, z0, 0, 1, 0, color);
        }
        if (renderFaces.contains(Direction.DOWN)) {
            int c = darken(color, DARKEN_PERCENTAGE_CUBE_BOTTOM);
            // down face
            vertex(vertexConsumer, poseStack, x1, y0, z0, 0, -1, 0, c);
            vertex(vertexConsumer, poseStack, x1, y0, z1, 0, -1, 0, c);
            vertex(vertexConsumer, poseStack, x0, y0, z1, 0, -1, 0, c);
            vertex(vertexConsumer, poseStack, x0, y0, z0, 0, -1, 0, c);
        }
        if (renderFaces.contains(Direction.EAST)) {
            int c = darken(color, DARKEN_PERCENTAGE_CUBE_SIDES);
            // right face
            vertex(vertexConsumer, poseStack, x1, y1, z0, 1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x1, y1, z1, 1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x1, y0, z1, 1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x1, y0, z0, 1, 0, 0, c);
        }
        if (renderFaces.contains(Direction.WEST)) {
            int c = darken(color, DARKEN_PERCENTAGE_CUBE_SIDES);
            // left face
            vertex(vertexConsumer, poseStack, x0, y0, z0, -1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x0, y0, z1, -1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x0, y1, z1, -1, 0, 0, c);
            vertex(vertexConsumer, poseStack, x0, y1, z0, -1, 0, 0, c);
        }
        if (renderFaces.contains(Direction.NORTH)) {
            int c = darken(color, DARKEN_PERCENTAGE_CUBE_FRONT_BACK);
            // front face (facing camera)
            vertex(vertexConsumer, poseStack, x0, y1, z0, 0, 0, -1, c);
            vertex(vertexConsumer, poseStack, x1, y1, z0, 0, 0, -1, c);
            vertex(vertexConsumer, poseStack, x1, y0, z0, 0, 0, -1, c);
            vertex(vertexConsumer, poseStack, x0, y0, z0, 0, 0, -1, c);
        }
        if (renderFaces.contains(Direction.SOUTH)) {
            int c = darken(color, DARKEN_PERCENTAGE_CUBE_FRONT_BACK);
            // back face
            vertex(vertexConsumer, poseStack, x0, y0, z1, 0, 0, 1, c);
            vertex(vertexConsumer, poseStack, x1, y0, z1, 0, 0, 1, c);
            vertex(vertexConsumer, poseStack, x1, y1, z1, 0, 0, 1, c);
            vertex(vertexConsumer, poseStack, x0, y1, z1, 0, 0, 1, c);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber") // 100 for percentage
    private static int darken(int color, int percentage) {
        int r = ARGB.red(color);
        r -= (r * percentage) / 100;
        int g = ARGB.green(color);
        g -= (g * percentage) / 100;
        int b = ARGB.blue(color);
        b -= (b * percentage) / 100;
        return ARGB.color(ARGB.alpha(color), r, g, b);
    }

    // TODO do we have any use for the normal vector? it's currently unused
    private static void vertex(VertexConsumer vertexConsumer, PoseStack pose, float x, float y, float z, int nx, int ny, int nz, int color) {
        var vec = new Vector3f();
        pose.last().pose().transformPosition(x, y, z, vec);

        vertexConsumer.addVertex(vec.x, vec.y, vec.z).setColor(color);
    }
}
