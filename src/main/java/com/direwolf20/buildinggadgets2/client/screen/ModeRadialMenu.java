/**
 * This class was adapted from code written by Vazkii for the PSI mod: https://github.com/Vazkii/Psi
 * Psi is Open Source and distributed under the
 * Psi License: http://psi.vazkii.us/license.php
 */
package com.direwolf20.buildinggadgets2.client.screen;

import com.direwolf20.buildinggadgets2.api.gadgets.GadgetModes;
import com.direwolf20.buildinggadgets2.client.KeyBindings;
import com.direwolf20.buildinggadgets2.client.OurSounds;
import com.direwolf20.buildinggadgets2.client.renderer.OurRenderTypes;
import com.direwolf20.buildinggadgets2.client.screen.widgets.GuiIconActionable;
import com.direwolf20.buildinggadgets2.client.screen.widgets.IncrementalSliderWidget;
import com.direwolf20.buildinggadgets2.common.BuildingGadgets2;
import com.direwolf20.buildinggadgets2.common.items.*;
import com.direwolf20.buildinggadgets2.common.network.PacketHandler;
import com.direwolf20.buildinggadgets2.common.network.packets.*;
import com.direwolf20.buildinggadgets2.setup.Registration;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.modes.BaseMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeI18n;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ModeRadialMenu extends Screen {
    private static final ImmutableList<ResourceLocation> signsCopyPaste = ImmutableList.of(
            new ResourceLocation(BuildingGadgets2.MODID, "textures/gui/mode/copy.png"),
            new ResourceLocation(BuildingGadgets2.MODID, "textures/gui/mode/paste.png")
    );
    private final List<Button> conditionalButtons = new ArrayList<>();
    private int timeIn = 0;
    private int slotSelected = -1;
    private int segments;
    private ArrayList<BaseMode> arrayOfModes = new ArrayList();
    private boolean cutForSure = false;

    public ModeRadialMenu(ItemStack stack) {
        super(Component.literal(""));

        if (stack.getItem() instanceof BaseGadget) {
            this.setSocketable(stack);
        }
    }

    private static float mouseAngle(int x, int y, int mx, int my) {
        Vector2f baseVec = new Vector2f(1F, 0F);
        Vector2f mouseVec = new Vector2f(mx - x, my - y);

        float ang = (float) (Math.acos(baseVec.dot(mouseVec) / (baseVec.length() * mouseVec.length())) * (180F / Math.PI));
        return my < y
                ? 360F - ang
                : ang;
    }

    public void setSocketable(ItemStack stack) {
        if (stack.getItem() instanceof BaseGadget actualGadget) {
            this.segments = GadgetModes.INSTANCE.getModesForGadget(actualGadget.gadgetTarget()).size();
        }
    }

    @Override
    public void init() {
        ItemStack tool = this.getGadget();
        if (tool.getItem() instanceof BaseGadget actualGadget) {
            ImmutableSortedSet<BaseMode> modesForGadget = GadgetModes.INSTANCE.getModesForGadget(actualGadget.gadgetTarget());
            arrayOfModes = new ArrayList<>(modesForGadget); // This is required to work with index's
        } else {
            return;
        }
        this.conditionalButtons.clear();


        if (tool.getItem() instanceof GadgetBuilding || tool.getItem() instanceof GadgetExchanger) {
            int widthSlider = 82;
            IncrementalSliderWidget sliderRange = new IncrementalSliderWidget(width / 2 - widthSlider / 2, height / 2 + 72, widthSlider, 14, 1, /*Config.GADGETS.maxRange.get()*/15, Component.translatable("buildinggadgets2.gui.range").append(": "), GadgetNBT.getToolRange(tool), slider -> {
                sendRangeUpdate(slider.getValueInt());
            });
            sliderRange.getComponents().forEach(this::addRenderableWidget);
        }

        if (tool.getItem() instanceof GadgetCutPaste) {
            //Cut Only
            addRenderableWidget(new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.cut"), "cut", ScreenPosition.LEFT, false, send -> {
                if (send) {
                    if (GadgetNBT.hasCopyUUID(tool) && !cutForSure) {
                        this.getMinecraft().player.displayClientMessage(Component.literal("Tool already has data stored, click again to confirm OVERWRITING this Cut. This action cannot be undone!"), true);
                        cutForSure = true;
                        return false;
                    }
                    PacketHandler.sendToServer(new PacketCut());
                }

                return false;
            }));
        }

        if (tool.getItem() instanceof GadgetCutPaste || tool.getItem() instanceof GadgetCopyPaste) {
            addRenderableWidget(new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.copypastemenu"), "copypaste_opengui", ScreenPosition.RIGHT, false, send -> {
                if (!send)
                    return false;

                assert this.getMinecraft().player != null;

                getMinecraft().player.closeContainer();
                var mode = GadgetNBT.getMode(tool);
                if (mode.getId().getPath().equals("paste"))
                    getMinecraft().setScreen(new PasteGUI(tool));
                else
                    getMinecraft().setScreen(new CopyGUI(tool));
                return true;
            }));
        }

        Button fuzzy_button = new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.fuzzy"), "fuzzy", ScreenPosition.RIGHT, send -> {
            if (send) {
                PacketHandler.sendToServer(new PacketToggleSetting(GadgetNBT.NBTValues.FUZZY.value));
            }

            return GadgetNBT.getSetting(this.getGadget(), GadgetNBT.NBTValues.FUZZY.value);
        });
        addRenderableWidget(fuzzy_button);
        conditionalButtons.add(fuzzy_button);

        Button connected_button = new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.connected_area"), "connected_area", ScreenPosition.RIGHT, send -> {
            if (send) {
                PacketHandler.sendToServer(new PacketToggleSetting(GadgetNBT.NBTValues.CONNECTED_AREA.value));
            }

            return GadgetNBT.getSetting(this.getGadget(), GadgetNBT.NBTValues.CONNECTED_AREA.value);
        });
        addRenderableWidget(connected_button);
        conditionalButtons.add(connected_button);

        Button undo_button = new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.undo"), "undo", ScreenPosition.LEFT, false, send -> {
            if (send)
                PacketHandler.sendToServer(new PacketUndo());

            return false;
        });
        if (!(tool.getItem() instanceof GadgetCutPaste))
            addRenderableWidget(undo_button);


        addRenderableWidget(new PositionedIconActionable(Component.translatable("buildinggadgets2.radialmenu.anchor"), "anchor", ScreenPosition.LEFT, send -> {
            if (send)
                PacketHandler.sendToServer(new PacketAnchor());

            return !GadgetNBT.getAnchorPos(tool).equals(GadgetNBT.nullPos);
        }));


/*        if (isDestruction) {
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.DESTRUCTION_OVERLAY, "destroy_overlay", right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketChangeRange());

                return GadgetDestruction.getOverlay(this.getGadget());
            }));

            addRenderableWidget(new PositionedIconActionable(RadialTranslation.FLUID_ONLY, "fluid_only", right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketToggleFluidOnly());

                return GadgetDestruction.getIsFluidOnly(this.getGadget());
            }));
        } else {
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.ROTATE, "rotate", left, false, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketRotateMirror(PacketRotateMirror.Operation.ROTATE));

                return false;
            }));
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.MIRROR, "mirror", left, false, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketRotateMirror(PacketRotateMirror.Operation.MIRROR));

                return false;
            }));
        }
        if (!(tool.getItem() instanceof GadgetCopyPaste)) {
            if (!isDestruction || Config.GADGETS.GADGET_DESTRUCTION.nonFuzzyEnabled.get()) {
                Button button = new PositionedIconActionable(RadialTranslation.FUZZY, "fuzzy", right, send -> {
                    if (send) {
                        PacketHandler.sendToServer(new PacketToggleFuzzy());
                    }

                    return BaseGadget.getFuzzy(this.getGadget());
                });
                addRenderableWidget(button);
                conditionalButtons.add(button);
            }
            if (!isDestruction) {
                Button button = new PositionedIconActionable(RadialTranslation.CONNECTED_SURFACE, "connected_area", right, send -> {
                    if (send) {
                        PacketHandler.sendToServer(new PacketToggleConnectedArea());
                    }

                    return BaseGadget.getConnectedArea(this.getGadget());
                });
                addRenderableWidget(button);
                conditionalButtons.add(button);
            }
            if (!isDestruction) {
                int widthSlider = 82;
                IncrementalSliderWidget sliderRange = new IncrementalSliderWidget(width / 2 - widthSlider / 2, height / 2 + 72, widthSlider, 14, 1, Config.GADGETS.maxRange.get(), GuiTranslation.SINGLE_RANGE.componentTranslation().append(": "),  GadgetUtils.getToolRange(tool), slider -> {
                    sendRangeUpdate(slider.getValueInt());
                });
                sliderRange.getComponents().forEach(this::addRenderableWidget);
            }
        } else {
            // Copy Paste specific
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.OPEN_GUI, "copypaste_opengui", right, send -> {
                if (!send)
                    return false;

                assert this.getMinecraft().player != null;

                getMinecraft().player.closeContainer();
                if (GadgetCopyPaste.getToolMode(tool) == GadgetCopyPaste.ToolMode.COPY)
                    getMinecraft().setScreen(new CopyGUI(tool));
                else
                    getMinecraft().setScreen(new PasteGUI(tool));
                return true;
            }));
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.OPEN_MATERIAL_LIST, "copypaste_materiallist", right, send -> {
                if (!send)
                    return false;

                assert this.getMinecraft().player != null;

                getMinecraft().player.closeContainer();
                getMinecraft().setScreen(new MaterialListGUI(tool));
                return true;
            }));
        }
        addRenderableWidget(new PositionedIconActionable(RadialTranslation.RAYTRACE_FLUID, "raytrace_fluid", right, send -> {
            if (send)
                PacketHandler.sendToServer(new PacketToggleRayTraceFluid());

            return BaseGadget.shouldRayTraceFluid(this.getGadget());
        }));
        if (tool.getItem() instanceof GadgetBuilding) {
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.PLACE_ON_TOP, "building_place_atop", right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketToggleBlockPlacement());

                return GadgetBuilding.shouldPlaceAtop(this.getGadget());
            }));
        }

        if (!(tool.getItem() instanceof GadgetExchanger)) {
            addRenderableWidget(new PositionedIconActionable(RadialTranslation.UNDO, "undo", left, false, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketUndo());

                return false;
            }));
        }
*/
        this.updateButtons();
    }

    private void updateButtons() {
        int buttonSize = 24;
        int paddingBetweenButtonsY = 10;
        int yPosLeft = height / 4;
        int yPosRight = height / 4;

        for (GuiEventListener widget : children()) {
            if (!(widget instanceof PositionedIconActionable button))
                continue;
            if (!button.visible) {
                continue;
            }

            if (button.position == ScreenPosition.RIGHT) {
                int xPos = width / 2 + 70;
                yPosRight = yPosRight + paddingBetweenButtonsY + buttonSize;
                button.setWidth(buttonSize);
                button.setHeight(buttonSize);
                button.setX(xPos);
                button.setY(yPosRight);
            } else if (button.position == ScreenPosition.LEFT) {
                int xPos = width / 2 - 70 - buttonSize;
                yPosLeft = yPosLeft + paddingBetweenButtonsY + buttonSize;
                button.setWidth(buttonSize);
                button.setHeight(buttonSize);
                button.setX(xPos);
                button.setY(yPosLeft);
            }
        }
    }

    private ItemStack getGadget() {
        assert this.getMinecraft().player != null;
        return BaseGadget.getGadget(this.getMinecraft().player);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mx, int my, float partialTicks) {
        PoseStack matrices = guiGraphics.pose();
        float speedOfButtonGrowth = 7f; //How fast the buttons move during initial window opening
        float fract = Math.min(speedOfButtonGrowth, this.timeIn + partialTicks) / speedOfButtonGrowth;
        int x = this.width / 2;
        int y = this.height / 2;

        int radiusMin = 26;
        int radiusMax = 60;
        double dist = new Vec3(x, y, 0).distanceTo(new Vec3(mx, my, 0));
        boolean inRange = false;
        //Highlights Segments if mouse over
        if (this.segments != 0) {
            inRange = dist > radiusMin && dist < radiusMax;
            for (GuiEventListener button : children()) {
                if (button instanceof PositionedIconActionable) {
                    ((PositionedIconActionable) button).setFaded(inRange);
                }
            }
        }


        // This triggers the animation on creation - only affects side buttons and slider(s)
        matrices.pushPose();
        matrices.translate((1 - fract) * x, (1 - fract) * y, 0);
        matrices.scale(fract, fract, fract);
        super.render(guiGraphics, mx, my, partialTicks);
        matrices.popPose();

        if (this.segments == 0) {
            return;
        }

        float angle = mouseAngle(x, y, mx, my);

        float totalDeg = 0;
        float degPer = 360F / this.segments;

        List<NameDisplayData> nameData = new ArrayList<>();

        ItemStack tool = this.getGadget();
        if (tool.isEmpty()) {
            return;
        }

        this.slotSelected = -1;

        BaseMode mode = GadgetNBT.getMode(tool);
        int modeIndex = arrayOfModes.indexOf(mode);

        /*if (tool.getItem() instanceof GadgetBuilding) {
            modeIndex = GadgetBuilding.getToolMode(tool).ordinal();
            signs = Arrays.stream(BuildingModes.values()).map(e -> new ResourceLocation(BuildingGadgets2.MODID, e.getIcon())).collect(Collectors.toList());
        } else if (tool.getItem() instanceof GadgetExchanger) {
            modeIndex = GadgetExchanger.getToolMode(tool).ordinal();
            signs = Arrays.stream(ExchangingModes.values()).map(e -> new ResourceLocation(BuildingGadgets2.MODID, e.getIcon())).collect(Collectors.toList());
        } else {
            modeIndex = GadgetCopyPaste.getToolMode(tool).ordinal();
            signs = signsCopyPaste;
        }*/

        boolean shouldCenter = (this.segments + 2) % 4 == 0;
        int indexBottom = this.segments / 4;
        int indexTop = indexBottom + this.segments / 2;
        for (int seg = 0; seg < this.segments; seg++) {
            boolean mouseInSector = this.isCursorInSlice(angle, totalDeg, degPer, inRange);
            //This makes the individual segments pop up one after another, a cool lil animation. Adjust the 6f to change it
            float delayBetweenSegments = 1f;
            float speedOfSegmentGrowth = 10f;
            float radius = Math.max(0F, Math.min((this.timeIn + partialTicks - seg * delayBetweenSegments / this.segments) * speedOfSegmentGrowth, radiusMax));
            float gs = 0.25F;
            if (seg % 2 == 0) {
                gs += 0.1F;
            }

            float r = gs;
            float g = gs + (seg == modeIndex
                    ? 1F
                    : 0.0F);
            float b = gs;
            float a = 0.4F;
            if (mouseInSector) {
                this.slotSelected = seg;
                r = g = b = 1F;
            }

            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer buffer = bufferSource.getBuffer(OurRenderTypes.TRIANGLE_STRIP);

            for (float i = degPer; i >= 0; i--) {
                float rad = (float) ((i + totalDeg) / 180F * Math.PI);
                float xp = (float) (x + Math.cos(rad) * radius);
                float yp = (float) (y + Math.sin(rad) * radius);
                if ((int) i == (int) (degPer / 2))
                    nameData.add(new NameDisplayData((int) xp, (int) yp, mouseInSector, shouldCenter && (seg == indexBottom || seg == indexTop)));

                Matrix4f pose = matrices.last().pose();
                buffer.vertex(pose, (float) (x + Math.cos(rad) * radius / 2.3F), (float) (y + Math.sin(rad) * radius / 2.3F), 0).color(r, g, b, a).endVertex();
                buffer.vertex(xp, yp, 0).color(r, g, b, a).endVertex();
            }

            bufferSource.endBatch(OurRenderTypes.TRIANGLE_STRIP);
            totalDeg += degPer;
        }

        // This is the naming logic for the text that pops up
        for (int i = 0; i < nameData.size(); i++) {
            matrices.pushPose();
            NameDisplayData data = nameData.get(i);
            int xp = data.getX();
            int yp = data.getY();

            String name = ForgeI18n.getPattern(arrayOfModes.get(i).i18n());

            int xsp = xp - 4;
            int ysp = yp;
            int width = font.width(name);

            if (xsp < x) {
                xsp -= width - 8;
            }
            if (ysp < y) {
                ysp -= 9;
            }

            Color color = i == modeIndex ? Color.GREEN : Color.WHITE;
            if (data.isSelected())
                guiGraphics.drawString(font, name, xsp + (data.isCentralized() ? width / 2f - 4 : 0), ysp, color.getRGB(), true);

            double mod = 0.7;
            int xdp = (int) ((xp - x) * mod + x);
            int ydp = (int) ((yp - y) * mod + y);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F, 1);
            RenderSystem.setShaderTexture(0, mode.icon());
            guiGraphics.blit(arrayOfModes.get(i).icon(), xdp - 8, ydp - 8, 0, 0, 16, 16, 16, 16);

            matrices.popPose();
        }
        Color color = Color.WHITE;
        RenderSystem.setShaderColor(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F, 1);
        float s = 1.8F * fract;
        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.scale(s, s, s);
        matrices.popPose();
        stack.translate(x - (tool.getItem() instanceof GadgetCopyPaste ? 8 : 8.5), y - 8, 440);
        guiGraphics.renderItem(tool, 0, 0);
        stack.popPose();
    }

    private boolean isCursorInSlice(float angle, float totalDeg, float degPer, boolean inRange) {
        return inRange && angle > totalDeg && angle < totalDeg + degPer;
    }

    private void changeMode() {
        if (this.slotSelected >= 0) {
            assert getMinecraft().player != null;
            PacketHandler.sendToServer(new PacketModeSwitch(arrayOfModes.get(this.slotSelected).getId(), false));
            OurSounds.playSound(Registration.BEEP.get());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        this.changeMode();
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void tick() {
        if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), KeyBindings.menuSettings.getKey().getValue())) {
            onClose();
            changeMode();
        }

        ImmutableSet<KeyMapping> set = ImmutableSet.of(getMinecraft().options.keyUp, getMinecraft().options.keyLeft, getMinecraft().options.keyDown, getMinecraft().options.keyRight, getMinecraft().options.keyShift, getMinecraft().options.keySprint, getMinecraft().options.keyJump);
        for (KeyMapping k : set)
            KeyMapping.set(k.getKey(), k.isDown());

        this.timeIn++;
        ItemStack tool = this.getGadget();

        boolean showButton = true;
        boolean changed = false;
        for (Button button : this.conditionalButtons) {
            if (tool.getItem() instanceof GadgetBuilding) {
                showButton = GadgetNBT.getMode(tool).getId().getPath().equals("surface");
            } else if (tool.getItem() instanceof GadgetExchanger) {
                showButton = true;
            } else {
                showButton = false;
            }

            if (button.visible != showButton) {
                button.visible = showButton;
                changed = true;
            }
        }
        if (changed) {
            this.updateButtons();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendRangeUpdate(int valueNew) {
        if (valueNew != GadgetNBT.getToolRange(this.getGadget())) {
            PacketHandler.sendToServer(new PacketRangeChange(valueNew));
        }
    }

    public enum ScreenPosition {
        RIGHT, LEFT, BOTTOM, TOP
    }

    private static final class NameDisplayData {
        private final int x;
        private final int y;
        private final boolean selected;
        private final boolean centralize;

        private NameDisplayData(int x, int y, boolean selected, boolean centralize) {
            this.x = x;
            this.y = y;
            this.selected = selected;
            this.centralize = centralize;
        }

        private int getX() {
            return this.x;
        }

        private int getY() {
            return this.y;
        }

        private boolean isSelected() {
            return this.selected;
        }

        private boolean isCentralized() {
            return this.centralize;
        }
    }

    private static class PositionedIconActionable extends GuiIconActionable {
        private ScreenPosition position;

        PositionedIconActionable(Component message, String icon, ScreenPosition position, boolean isSelectable, Predicate<Boolean> action) {
            super(0, 0, icon, message, isSelectable, action);

            this.position = position;
        }

        PositionedIconActionable(Component message, String icon, ScreenPosition position, Predicate<Boolean> action) {
            this(message, icon, position, true, action);
        }
    }

    private static class Vector2f {
        public float x;
        public float y;

        public Vector2f(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public final float dot(Vector2f v1) {
            return (this.x * v1.x + this.y * v1.y);
        }

        public final float length() {
            return (float) Math.sqrt(this.x * this.x + this.y * this.y);
        }
    }
}
