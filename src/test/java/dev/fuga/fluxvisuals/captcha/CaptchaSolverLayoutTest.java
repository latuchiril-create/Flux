package dev.fuga.fluxvisuals.captcha;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class CaptchaSolverLayoutTest {
    private static final int MAP_SIZE = 128;
    private static final int OUTPUT_SCALE = 2;

    @Test
    void keepsSouthFacingRowInWorldXOrderWhenWallCrossesAngleWrap() throws Exception {
        assertRowOrder(Direction.SOUTH, new double[][]{
                {-1.5D, -3.0D}, {-0.5D, -3.0D}, {0.5D, -3.0D}, {1.5D, -3.0D}
        });
    }

    @Test
    void keepsNorthFacingRowInViewerOrder() throws Exception {
        assertRowOrder(Direction.NORTH, new double[][]{
                {1.5D, 3.0D}, {0.5D, 3.0D}, {-0.5D, 3.0D}, {-1.5D, 3.0D}
        });
    }

    @Test
    void keepsWestFacingRowInViewerOrder() throws Exception {
        assertRowOrder(Direction.WEST, new double[][]{
                {3.0D, -1.5D}, {3.0D, -0.5D}, {3.0D, 0.5D}, {3.0D, 1.5D}
        });
    }

    @Test
    void keepsEastFacingRowInViewerOrder() throws Exception {
        assertRowOrder(Direction.EAST, new double[][]{
                {-3.0D, 1.5D}, {-3.0D, 0.5D}, {-3.0D, -0.5D}, {-3.0D, -1.5D}
        });
    }

    @Test
    void separatesNearbyParallelBillboardsOnDifferentPlanes() throws Exception {
        List<Object> frames = new ArrayList<>();
        for (double z : new double[]{-3.0D, -2.0D}) {
            frames.add(frame(-0.5D, 2.0D, z, (byte) 6, Direction.SOUTH));
            frames.add(frame(0.5D, 2.0D, z, (byte) 10, Direction.SOUTH));
            frames.add(frame(-0.5D, 1.0D, z, (byte) 14, Direction.SOUTH));
            frames.add(frame(0.5D, 1.0D, z, (byte) 18, Direction.SOUTH));
        }

        Method clusterWalls = CaptchaSolver.class.getDeclaredMethod(
                "clusterWalls", List.class, Vec3d.class
        );
        clusterWalls.setAccessible(true);
        List<?> walls = (List<?>) clusterWalls.invoke(null, frames, new Vec3d(0.0D, 0.0D, 0.0D));

        assertEquals(2, walls.size(),
                "parallel captcha billboards on separate planes must not be stitched together");
    }

    @Test
    void appliesItemFrameMapRotationBeforeStitching() throws Exception {
        byte[] pixels = new byte[MAP_SIZE * MAP_SIZE];
        fillQuadrant(pixels, 0, 0, (byte) 6);
        fillQuadrant(pixels, MAP_SIZE / 2, 0, (byte) 18);
        fillQuadrant(pixels, 0, MAP_SIZE / 2, (byte) 30);
        fillQuadrant(pixels, MAP_SIZE / 2, MAP_SIZE / 2, (byte) 42);

        Object unrotated = frame(0.0D, 2.0D, 3.0D, pixels, Direction.NORTH, 0);
        Object clockwise = frame(0.0D, 2.0D, 3.0D, pixels, Direction.NORTH, 1);
        BufferedImage source = render(List.of(unrotated), Vec3d.ZERO, 0.0D);
        BufferedImage rotated = render(List.of(clockwise), Vec3d.ZERO, 0.0D);
        int last = MAP_SIZE * OUTPUT_SCALE - 2;

        assertEquals(source.getRGB(1, last), rotated.getRGB(1, 1));
        assertEquals(source.getRGB(1, 1), rotated.getRGB(last, 1));
        assertEquals(source.getRGB(last, last), rotated.getRGB(1, last));
        assertEquals(source.getRGB(last, 1), rotated.getRGB(last, last));
    }

    @Test
    void rememberedPhysicalWallDoesNotAvoidAReusedListIndex() throws Exception {
        List<Object> oldWall = List.of(
                frame(-3.0D, 2.0D, -0.5D, (byte) 6, Direction.WEST),
                frame(-3.0D, 2.0D, 0.5D, (byte) 6, Direction.WEST),
                frame(-3.0D, 1.0D, -0.5D, (byte) 6, Direction.WEST),
                frame(-3.0D, 1.0D, 0.5D, (byte) 6, Direction.WEST)
        );
        List<Object> sideWall = List.of(
                frame(3.0D, 2.0D, -0.5D, (byte) 6, Direction.EAST),
                frame(3.0D, 2.0D, 0.5D, (byte) 6, Direction.EAST),
                frame(3.0D, 1.0D, -0.5D, (byte) 6, Direction.EAST),
                frame(3.0D, 1.0D, 0.5D, (byte) 6, Direction.EAST)
        );
        List<Object> crosshairWall = List.of(
                frame(-0.5D, 2.0D, 3.0D, (byte) 6, Direction.NORTH),
                frame(0.5D, 2.0D, 3.0D, (byte) 6, Direction.NORTH),
                frame(-0.5D, 1.0D, 3.0D, (byte) 6, Direction.NORTH),
                frame(0.5D, 1.0D, 3.0D, (byte) 6, Direction.NORTH)
        );

        Class<?> identityType = Class.forName("dev.fuga.fluxvisuals.captcha.CaptchaSolver$WallIdentity");
        Method wallIdentity = CaptchaSolver.class.getDeclaredMethod("wallIdentity", List.class);
        wallIdentity.setAccessible(true);
        Object avoided = wallIdentity.invoke(null, oldWall);
        Method select = CaptchaSolver.class.getDeclaredMethod("selectWallIndex", List.class,
                identityType, Vec3d.class, double.class, Vec3d.class, double.class,
                int.class, int.class);
        select.setAccessible(true);
        CaptchaSolver solver = new CaptchaSolver();
        int selected = (int) select.invoke(solver, List.of(sideWall, crosshairWall), avoided,
                Vec3d.ZERO, 0.0D, new Vec3d(0.0D, 1.5D, 0.0D), 0.0D, -1, -1);

        assertEquals(1, selected,
                "an old physical wall must not blacklist a different wall that now has the same numeric index");
    }

    private static Object frame(double x, double y, double z, byte color, Direction facing) throws Exception {
        byte[] pixels = new byte[MAP_SIZE * MAP_SIZE];
        Arrays.fill(pixels, color);
        return frame(x, y, z, pixels, facing, 0);
    }

    private static Object frame(double x, double y, double z, byte[] pixels,
                                Direction facing, int rotation) throws Exception {
        Class<?> frameType = Class.forName("dev.fuga.fluxvisuals.captcha.CaptchaSolver$FrameMap");
        Constructor<?> constructor = frameType.getDeclaredConstructor(
                Vec3d.class, byte[].class, Direction.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new Vec3d(x, y, z), pixels, facing, rotation);
    }

    private static void fillQuadrant(byte[] pixels, int startX, int startY, byte color) {
        for (int y = startY; y < startY + MAP_SIZE / 2; y++) {
            for (int x = startX; x < startX + MAP_SIZE / 2; x++) {
                pixels[x + y * MAP_SIZE] = color;
            }
        }
    }

    private static void assertRowOrder(Direction facing, double[][] xz) throws Exception {
        Vec3d player = new Vec3d(0.0D, 0.0D, 0.0D);
        byte[] mapColors = {6, 18, 30, 42};
        List<Object> frames = new ArrayList<>();
        int[] expected = new int[xz.length];
        for (int index = 0; index < xz.length; index++) {
            Object frame = frame(xz[index][0], 2.0D, xz[index][1], mapColors[index], facing);
            frames.add(frame);
            expected[index] = render(List.of(frame), player, 0.0D).getRGB(1, 1);
        }

        BufferedImage stitched = render(frames, player, 0.0D);
        int[] actual = new int[xz.length];
        for (int column = 0; column < actual.length; column++) {
            actual[column] = stitched.getRGB(column * MAP_SIZE * OUTPUT_SCALE + 1, 1);
        }
        assertArrayEquals(expected, actual, facing + " wall columns must follow the viewer's left-to-right order");
    }

    @SuppressWarnings("unchecked")
    private static BufferedImage render(List<?> frames, Vec3d player, double yaw) throws Exception {
        Method renderWall = CaptchaSolver.class.getDeclaredMethod(
                "renderWall", List.class, Vec3d.class, double.class
        );
        renderWall.setAccessible(true);
        return (BufferedImage) renderWall.invoke(null, frames, player, yaw);
    }
}
